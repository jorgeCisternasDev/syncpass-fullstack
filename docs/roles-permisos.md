# Roles y permisos — SyncPass v2

## 1. Roles del caso

| Rol del caso | Nombre técnico | Uso principal |
| --- | --- | --- |
| Docente / Coordinador | `Coordinador` | Administra usuarios, proyectos y eventos; acredita |
| Estudiante / Monitor | `Monitor` | Consulta catálogo, acredita y revisa indicadores |
| Beneficiario / Asistente | `Asistente` | Se inscribe, usa la app, responde encuestas |

El rol viaja en el JWT (`roles` o `cognito:groups`, según el modo de auth). El backend lo exige con `@PreAuthorize`; las interfaces solo lo usan para mostrar u ocultar opciones.

**Usuarios ficticios del seed local:**

| Correo | Contraseña | Rol |
| --- | --- | --- |
| `coordinador@syncpass.cl` | `Coord.2026` | Coordinador |
| `monitor@syncpass.cl` | `Monitor.2026` | Monitor |
| `asistente@syncpass.cl` | `Asist.2026` | Asistente |

## 2. Matriz de endpoints

| Endpoint | Público | Coordinador | Monitor | Asistente | Nota |
| --- | --- | --- | --- | --- | --- |
| `GET /api/health` | Sí | Sí | Sí | Sí | Sin token |
| `POST /api/auth/login` | Sí | — | — | — | Devuelve tokens |
| `POST /api/auth/refresh` | Sí | — | — | — | Renueva tokens |
| `POST /api/auth/registro` | No | **Sí** | No | No | `@PreAuthorize` |
| `POST /api/auth/logout` | No | Sí | Sí | Sí | Exige `Authorization` en la práctica |
| `GET /api/usuarios` | No | **Sí** | No | No | Listado |
| `PATCH /api/usuarios/{id}/baja` | No | **Sí** | No | No | Ciclo de vida |
| `PATCH /api/usuarios/{id}/alta` | No | **Sí** | No | No | Ciclo de vida |
| `DELETE /api/usuarios/{id}` | No | **Sí** | No | No | Elimina |
| `GET /api/proyectos` | No | Sí | Sí | Sí | Lectura del catálogo |
| `POST /api/proyectos` | No | **Sí** | No | No | Escritura |
| `PUT /api/proyectos/{id}` | No | **Sí** | No | No | Escritura |
| `DELETE /api/proyectos/{id}` | No | **Sí** | No | No | `409` si tiene eventos |
| `GET /api/proyectos/{id}/eventos` | No | Sí | Sí | Sí | Lectura |
| `POST /api/eventos` | No | **Sí** | No | No | Escritura |
| `PUT /api/eventos/{id}` | No | **Sí** | No | No | Escritura |
| `DELETE /api/eventos/{id}` | No | **Sí** | No | No | Escritura |
| `POST /api/participantes` | No | Sí | Sí | Sí | Aprovisiona usuario `Asistente` |
| `POST /api/sync/lote` | No | Sí | Sí | Sí | Lote offline |
| `GET /api/dashboard/resumen` | No | Sí | Sí | Sí | Totales |

Reglas de la cadena de seguridad (`SecurityConfig`): `OPTIONS /**` y `/api/auth/**` + `/api/health` públicos; `/api/sync/**` y `POST /api/participantes` para los tres roles; todo lo demás autenticado. El detalle fino lo aplica `@PreAuthorize` en cada método.

Respuestas esperadas: sin token → `401`; con token de rol insuficiente → `403`.

## 3. Panel web

| Ruta | Acceso | Qué ve |
| --- | --- | --- |
| `/login` | Público | Formulario de acceso |
| `/` | Sesión | Dashboard con nombre, rol y totales |
| `/usuarios` | **Coordinador** | Alta, baja, alta de nuevo y eliminación |
| `/proyectos` | Sesión | Lista; formulario y acciones solo Coordinador |
| `/eventos` | Sesión | Eventos por proyecto; escritura solo Coordinador |

El `Guard` redirige a `/login` sin sesión y a `/` cuando falta el rol. Ocultar un botón no protege el endpoint: la API responde `403` igual.

## 4. App Android

| Pantalla / ruta | Acceso | Uso |
| --- | --- | --- |
| Login | Sin sesión | Autenticación remota con respaldo local si `MODO_DEMO=true` |
| Inicio Monitor (`home`) | Monitor | Resumen y accesos |
| Inicio Coordinador | Coordinador | Panel de coordinación |
| Inicio Asistente | Asistente | Próximos eventos y encuestas |
| Acreditar (`accredit`) | Monitor y Coordinador | Acreditación manual o por QR |
| Proyectos (`projects`) | Monitor y Coordinador | Catálogo descargado |
| Evento (`event/{id}`, `event/new`, `event/{id}/edit`) | Monitor y Coordinador | Detalle y mantenimiento |
| QR (`qr/{participantId}`) | Monitor y Coordinador | Código del participante |
| Escáner (`scanner/{eventId}/{useLocation}`) | Monitor y Coordinador | Lectura de QR |
| Encuestas (`surveys`, `survey/*`) | Según rol | Crear, responder y ver resultados |
| Indicadores (`indicators/{eventId}`) | Monitor y Coordinador | Métricas del evento |
| Ajustes (`settings`) | Todos | Sync, pendientes, rechazos y descarte |

La app decide la pantalla inicial por el rol del token: `MONITOR` → navegación completa; `COORDINATOR` → panel de coordinación; `PARTICIPANT` → inicio de participante.

## 5. Reglas transversales

1. El JWT es la única fuente del rol; el cliente no lo declara.
2. Si el token expira, el cliente puede renovar con `refreshToken`; si falla, vuelve al login.
3. Un participante aprovisionado no tiene contraseña: existe para atribuir asistencias, no para iniciar sesión.
4. El guard de UI mejora la experiencia, no la seguridad. La seguridad está en la API.

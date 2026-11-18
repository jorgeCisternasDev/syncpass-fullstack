# Requisitos — SyncPass v2

Los requisitos se derivan del contrato v2 y del código en `main`. Cada uno indica dónde está implementado y con qué se verificó.

## 1. Requisitos funcionales

| Código | Requisito | Implementación | Verificación |
| --- | --- | --- | --- |
| RF-01 | Login con email y contraseña; devolver `accessToken`, `idToken` y `refreshToken` | `AuthController.login`, `AuthService` | `AuthControllerTest`, `Login.test.tsx` |
| RF-02 | Refresh y logout; en Cognito el logout ejecuta `GlobalSignOut` | `AuthController.refresh/logout` | Pruebas de auth |
| RF-03 | Listar usuarios y permitir registro, baja, alta y eliminación solo a Coordinador | `UsuarioController`, `AuthController.registro` | `Usuarios.test.tsx`, pruebas de seguridad |
| RF-04 | Listar proyectos para cualquier usuario autenticado | `ProyectoController.listar` | `Proyectos.test.tsx`, pruebas de API |
| RF-05 | Crear y actualizar proyectos solo a Coordinador | `ProyectoController` | Pruebas de CRUD y 403 |
| RF-06 | Eliminar proyectos sin eventos; si hay eventos, responder `409` | `ProyectoService` | Pruebas de regla |
| RF-07 | Listar los eventos de un proyecto para cualquier autenticado | `ProyectoController.eventos` | `Eventos.test.tsx` |
| RF-08 | Crear y actualizar eventos solo a Coordinador, con fecha dentro del proyecto y cupos | `EventoController`, `EventoService` | Pruebas de validación |
| RF-09 | Eliminar eventos solo a Coordinador | `EventoController.delete` | Pruebas de seguridad |
| RF-10 | Recibir un lote de asistencias y encuestas y responder aceptados sin duplicar filas | `SyncController`, `LoteService` | `SyncControllerTest`, `LoteServiceTest` |
| RF-11 | Idempotencia por `eventoId + usuarioId`; el UUID no participa de la clave | `AsistenciaId`, `LoteService` | Pruebas de reenvío |
| RF-12 | Persistir `INSCRITO`/`PRESENTE`; sin estado, asumir `PRESENTE` y derivar `validado` | `LoteService`, `EstadoAsistencia` | Pruebas de transición |
| RF-13 | Exigir presencia antes de aceptar una encuesta (`409 SIN_PRESENCIA`) | `LoteService.aplicarEncuesta` | Pruebas de presencia |
| RF-14 | Contar cupos por filas de asistencia; rechazar fila nueva si está lleno; permitir la transición | `LoteService`, bloqueo `FOR UPDATE` | Pruebas de cupo y concurrencia |
| RF-15 | Aprovisionar participantes con UUID como `Asistente`; sin correo de la app; `409 EMAIL_EN_USO` si el correo es de otro UUID | `ParticipanteService` | Pruebas de idempotencia y conflicto |
| RF-16 | Descargar proyectos y eventos a Room con *upsert* por `remoteId`, sin borrar datos locales | `CatalogoRepository` | `CatalogoRepositoryTest` |
| RF-17 | Programar sync con WorkManager: trabajo único al abrir/tras acreditar/encuestar y periódico de 15 minutos | `SyncScheduler` / workers | Pruebas de workers |
| RF-18 | Marcar ítems rechazados por `4xx` de negocio, no reintentar en bucle y permitir descartarlos | `SyncRepository`, Ajustes | Evidencia de emulador H3 |
| RF-19 | Exponer totales de usuarios, eventos, asistencias y encuestas | `DashboardController` | `Dashboard.test.tsx` |
| RF-20 | Exponer `/api/health` sin autenticación con estado y modo de auth | `HealthController` | Smoke de etapa C |
| RF-21 | Rutas del panel: login, dashboard, usuarios, proyectos y eventos | `App.tsx` (wouter) | Pruebas de web |
| RF-22 | Ocultar formularios y acciones de escritura a Monitor y Asistente; el backend igualmente responde `403` | `Guard`, `hasRole`, `@PreAuthorize` | `Nav.test.tsx`, `Proyectos.test.tsx`, `Usuarios.test.tsx` |

## 2. Requisitos no funcionales

| Código | Tipo | Requisito | Verificación |
| --- | --- | --- | --- |
| RNF-01 | Seguridad | Todo endpoint privado exige `Authorization: Bearer` y un rol reconocido | `SecurityConfig`, pruebas 401/403 |
| RNF-02 | Seguridad | Contraseñas con BCrypt; secretos por variables de entorno | `AuthService`, `.env.example` |
| RNF-03 | Entorno | Perfil local con H2 en memoria y auth local | `application.yml` |
| RNF-04 | Entorno | Perfil `postgres` con URL, usuario y contraseña por variables | Smoke de perfil |
| RNF-05 | Entorno | Perfil `cognito` con issuer, pool, client y región por variables | Smoke de perfil |
| RNF-06 | Seguridad | No se versionan credenciales; `.env.example` solo tiene nombres y valores ficticios | Revisión de commits |
| RNF-07 | Contrato | Login, sync, proyectos y eventos responden DTO planos; el resto usa `ApiResponse` | Contrato v2 |
| RNF-08 | Calidad | Validación explícita y errores centralizados en `GlobalExceptionHandler` | Pruebas de validación |
| RNF-09 | Calidad | App, API y web compilan y ejecutan sus pruebas | 79 + 65 + 20 en verde |
| RNF-10 | Datos | Usuarios, participantes, proyectos y eventos de ejemplo son ficticios | Seeders y pruebas |
| RNF-11 | Sincronización | El lote es atómico: si un ítem se rechaza, no se aplica ninguno y la respuesta detalla el error | `@Transactional` en `LoteService` |
| RNF-12 | Sincronización | Un `4xx` de negocio marca el ítem y no se reintenta; `5xx`/red reintentan con backoff | `SyncRepository`, evidencia H3 |
| RNF-13 | Privacidad | La app no envía el correo del participante; el backend usa uno sintético | `ParticipanteService` |

## 3. Reglas de negocio que no son requisitos de interfaz

1. `estado` ausente equivale a `PRESENTE`; `validado` se deriva y no se confía en el cliente.
2. La transición `INSCRITO → PRESENTE` conserva `inscritoMs` y actualiza `timestamp`.
3. El estado más avanzado gana: `PRESENTE` no retrocede a `INSCRITO`.
4. El cupo se cuenta con todas las filas del evento, en cualquier estado; solo bloquea filas nuevas.
5. Dos lotes concurrentes no pueden superar el cupo: bloqueo `SELECT ... FOR UPDATE` por evento, en orden de id.
6. La primera encuesta gana; un reenvío no pisa la respuesta anterior.
7. Un UUID distinto sobre una fila ya atribuida responde `CONFLICTO_IDENTIDAD`.
8. Un participante aprovisionado nace sin contraseña: no puede iniciar sesión.

## 4. Fuera de alcance

- Despliegue en la nube, PostgreSQL y Cognito reales.
- Validación QR con cámara física.
- Publicación en tiendas de aplicaciones.
- Administración de asistencias desde el panel web.
- Pruebas de carga y SLA de latencia.

## 5. Criterios de aceptación de la entrega

| Criterio | Cómo se comprueba |
| --- | --- |
| Contrato v2 respetado por app y web | Pruebas de backend y smoke de emulador |
| Reglas de estado, presencia y cupos | `LoteServiceTest` y evidencia de emulador |
| Panel con guard por rol | `npm test` (20 casos) |
| App con catálogo, sync y rechazos visibles | `testDebugUnitTest` (79 casos) y evidencia H3 |
| Números de cobertura declarados sin inventar | [pruebas-cobertura.md](pruebas-cobertura.md) |
| Sin secretos versionados | `.gitignore` y revisión de commits |

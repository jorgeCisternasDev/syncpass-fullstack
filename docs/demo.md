# Guion de demo — SyncPass v2

Duración objetivo: **10 a 12 minutos**. Todo con datos ficticios. La app y la API corren en local; no hay despliegue.

## 1. Antes de partir (5 minutos antes)

| Qué | Cómo |
| --- | --- |
| API arriba | `cd syncpass_api; .\gradlew.bat bootRun` → `http://localhost:8080/api/health` responde 200 |
| Panel web | `cd syncpass_web; npm run dev` → `http://localhost:5173` |
| App instalada | `adb install -r entregas\SyncPass-1.1.0-h3-release-firmado.apk` |
| Red de la app | Misma WiFi que el PC; `API_BASE_URL` del APK es `http://192.168.1.84:8080/`. Si la IP cambió, recompilar o usar build debug con `adb reverse` |
| Evento listo | Un evento de hoy con ventana abierta y cupos disponibles |
| Datos limpios | Borrar datos de la app o usar un evento nuevo para que los contadores partan en cero |

Credenciales ficticias:

| Usuario | Clave | Rol |
| --- | --- | --- |
| `coordinador@syncpass.cl` | `Coord.2026` | Coordinador |
| `monitor@syncpass.cl` | `Monitor.2026` | Monitor |
| `asistente@syncpass.cl` | `Asist.2026` | Asistente |

## 2. Guion

### 0:00–1:00 — Qué es SyncPass

> "SyncPass acredita participación en talleres. La app funciona sin red y sincroniza después; el panel web administra usuarios, proyectos y eventos; la API aplica las reglas y es el único que toca la base."

Mostrar el panel en `/login` y la app en la pantalla de acceso.

### 1:00–2:00 — Login Monitor y guard por rol

1. En el panel, entrar como `monitor@syncpass.cl`.
2. Mostrar el dashboard: "Hola, Diego Fuentes" y rol **Monitor**.
3. Abrir el menú: **no aparece Usuarios**.
4. Entrar a `/proyectos`: se ve la lista, **sin** botones de crear, editar o eliminar.

> "El rol viene en el JWT. El guard oculta lo que no corresponde, pero la API además responde 403."

### 2:00–3:00 — Catálogo

1. En `/proyectos` mostrar los proyectos y en `/eventos` los eventos del proyecto.
2. En la app (Monitor), abrir **Inicio**: aparecen los eventos descargados.
3. Abrir **Proyectos**: el catálogo local coincide con la API.

> "La app baja proyectos y eventos y los guarda en Room. Si se corta la red, sigue mostrando lo último descargado."

### 3:00–5:00 — Acreditación manual y QR

1. En la app, entrar a **Acreditar** y elegir el evento.
2. Agregar un participante desde el detalle del evento (nombre ficticio). La app genera un código `SP...` y su QR.
3. Escribir ese código en **Código o documento** y tocar **Acreditar**.
4. Mostrar el contador `1/1 acreditados` y **Últimas acreditaciones**.
5. Tocar **Abrir cámara** para mostrar la pantalla de escaneo.

> "La cámara física no se verificó en esta entrega; el escaneo existe y el camino manual es el respaldado. Si la cámara falla en la sala, se sigue con el código."

### 5:00–6:30 — Encuesta

1. Acreditar un segundo participante (o usar el asistente del seed).
2. Entrar como `asistente@syncpass.cl` en la app.
3. Responder la encuesta de satisfacción (5 y 5, comentario ficticio).
4. Mostrar la confirmación.

> "Solo puede responder quien figura PRESENTE en el evento. Si no hay presencia, la API responde 409 SIN_PRESENCIA."

### 6:30–8:00 — Offline y sincronización

1. Activar modo avión.
2. Acreditar a otro participante.
3. Abrir **Ajustes**: **Pendientes por enviar** sube.
4. Cerrar y reabrir la app: la sesión y el pendiente siguen ahí.
5. Quitar modo avión.
6. Tocar **Sincronizar** (o esperar al worker). **Pendientes: 0**.
7. En el panel, refrescar el dashboard: asistencias y encuestas suben.

> "WorkManager programa un trabajo único al abrir, al acreditar y al encuestar, y uno periódico cada 15 minutos. No depende de que el usuario se acuerde."

### 8:00–9:00 — Idempotencia y rechazos

1. Tocar **Sincronizar** otra vez con la cola vacía (o reenviar el mismo dato en la prueba de API).
2. Mostrar que los totales no cambian.

> "La clave es evento + usuario. Reenviar el mismo lote no duplica: cuenta como duplicado."

3. (Opcional) Provocar un rechazo de negocio y mostrar **Rechazados** y **Descartar rechazados** en Ajustes.

> "Un 4xx de negocio marca el ítem y no se reintenta en bucle. Un 5xx o un corte de red sí se reintentan."

### 9:00–10:00 — Rol Coordinador y 403 real

1. Salir y entrar como `coordinador@syncpass.cl`.
2. Abrir **Usuarios**: aparece el alta y la lista.
3. Abrir **Proyectos** y **Eventos**: aparecen los formularios y las acciones.
4. Mostrar en consola que un token de Monitor recibe 403 al intentar escribir:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"email":"monitor@syncpass.cl","password":"Monitor.2026"}'
try {
  Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/proyectos `
    -Headers @{ Authorization = "Bearer $($login.accessToken)" } `
    -ContentType 'application/json' `
    -Body '{"titulo":"x","descripcion":"x","areaTematica":"x","unidadVecinal":"x","fechaInicio":1795000000000,"fechaTermino":1795100000000,"estado":"EN_CURSO"}'
} catch { $_.Exception.Response.StatusCode.value__ }
```

> "Ocultar el botón no es seguridad; la seguridad está en la API."

### 10:00–11:00 — APK y cierre

1. Mostrar el APK firmado: versión 1.1.0, `MODO_DEMO=true`, `API_BASE_URL` del PC.
2. Mostrar el hash:

```powershell
Get-FileHash entregas\SyncPass-1.1.0-h3-release-firmado.apk -Algorithm SHA256
```

3. Cerrar con los números: **79 pruebas app, 65 backend, 20 web**; cobertura **64,1 %** API y **50,3 %** web; app medida pero no representativa por Robolectric.

## 3. Matriz caso `.md` → evidencia

| Bloque de la demo | Documento | Evidencia real |
| --- | --- | --- |
| Login y guard por rol | [roles-permisos.md](roles-permisos.md), [casos-de-uso.md](casos-de-uso.md) CU-01/CU-14 | `evidencia\h3-emulador\05-home-3-eventos.png`; tests `Login.test.tsx`, `Nav.test.tsx`, `Usuarios.test.tsx` |
| Catálogo en la app | [arquitectura.md](arquitectura.md) §5, CU-05 | `evidencia\h3-emulador\05-db-local.txt`, `backend-proyectos-final.json`, `CatalogoRepositoryTest` |
| Acreditación manual | [arquitectura.md](arquitectura.md) §6, CU-07 | `evidencia\h3-emulador\03-evento-2de2.png`, `03-ui-evento.xml` |
| QR / cámara | CU-07 y [pruebas-cobertura.md](pruebas-cobertura.md) §5 | Pantalla de escaneo en la app; **cámara física no verificada**, camino manual como respaldo |
| Encuesta con presencia | CU-11 y [requisitos.md](requisitos.md) RF-13 | `evidencia\h3-emulador\06-ui-encuesta-formulario.xml`, `06-ui-encuesta-gracias.xml`; `LoteServiceTest` |
| Offline y pendientes | [arquitectura.md](arquitectura.md) §6 | `evidencia\h3-emulador\04-ajustes-pendientes.png`, `02-persistencia-asistente.png` |
| Sync e idempotencia | CU-08, RF-11 y RNF-11 | `evidencia\h3-emulador\backend-dashboard-03-final.json`, `backend-dashboard-02-reenvio.json`, `logcat-final.txt` (worker `SUCCESS`) |
| Rechazo y descarte | CU-12 y RF-18 | `evidencia\h3-emulador\07-ajustes-rechazo.png`, `08-ajustes-descartado.png` |
| 403 real | [roles-permisos.md](roles-permisos.md) §2 | Bloque de consola de este guion; tests de seguridad del backend |
| APK firmado | `FIRMA-APK.md` de la entrega | SHA-256 `0538D515…`; esquema v2; huella `a4cd1b83…` |

Las capturas y JSON viven en la carpeta de evidencia de la entrega (fuera del repositorio). Los tests y comandos sí están en este repositorio y se pueden repetir.

## 4. Si algo falla en la sala

| Falla | Plan B |
| --- | --- |
| No hay red o cambió la IP | Mostrar la app con datos ya descargados; la demo offline funciona igual. Recompilar con la IP correcta solo si hay tiempo |
| El emulador no arranca | Usar capturas de `evidencia\h3-emulador` y el APK instalado en un teléfono |
| La API no arranca | Mostrar `npm test`, `gradlew test` y los reportes de cobertura; el panel web queda sin datos |
| La cámara no enfoca | Acreditar por código; decir explícitamente que la cámara no está verificada |
| Se llenó el cupo del evento | Usar otro evento o subir `cuposMax` desde el panel |

## 5. Qué no prometer

- No hay despliegue en la nube.
- PostgreSQL y Cognito reales no se probaron.
- La cámara/QR físico no se verificó.
- Las pruebas instrumentadas de Android no se ejecutaron.
- La cobertura de la app no es una métrica válida por el sandbox de Robolectric.

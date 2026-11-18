# Guion de demo — SyncPass v2

Duración objetivo: **10 a 12 minutos**. Todo con datos ficticios. La app y la API corren en local; no hay despliegue.

## 1. Antes de partir (5 minutos antes)

| Qué | Cómo |
| --- | --- |
| API arriba | Ejecutar `demo\reset-demo.ps1` de la entrega (deja una ventana "API" abierta) o `cd syncpass_api; .\gradlew.bat bootRun` |
| Panel web | `cd syncpass_web; npm run dev` → `http://localhost:5173` |
| App principal | `adb install -r entregas\SyncPass-1.1.0-h3-release-firmado.apk` (IP LAN, `MODO_DEMO=true`) |
| App plan B | `entregas\SyncPass-1.1.0-debug-localhost.apk` + `adb reverse tcp:8080 tcp:8080` |
| Red de la app | Release: misma WiFi que el PC, `http://192.168.1.84:8080/`. Plan B: `localhost` por `adb reverse` |
| Evento listo | Evento fijo **Demo Defensa** (ver abajo); el reset refresca su ventana |
| Datos limpios | `demo\reset-demo.ps1` restaura la app y la base a 0 asistencias y 3 participantes |

Credenciales ficticias:

| Usuario | Clave | Rol |
| --- | --- | --- |
| `coordinador@syncpass.cl` | `Coord.2026` | Coordinador |
| `monitor@syncpass.cl` | `Monitor.2026` | Monitor |
| `asistente@syncpass.cl` | `Asist.2026` | Asistente |

### Kit fijo de defensa

| Campo | Valor |
| --- | --- |
| Proyecto | **5** — Demo Defensa |
| Evento | **3** — Demo Defensa |
| Ventana | El reset la deja en `ahora - 30 min` a `ahora + 6 h` |
| Cupos | 10 |
| Recinto | Sala de defensa |
| Participantes | `Ana Demo` / `DEMO-001` / **SPGY6PW8** · `Bruno Demo` / `DEMO-002` / **SPPPTLFK** · `Carla Demo` / `DEMO-003` / **SPTJL9EA** |

Reset para repetir la defensa igual:

```powershell
cd <carpeta de la entrega>\demo
.\reset-demo.ps1
```

El script detiene la API, restaura la base desde `demo\snapshots\backend-syncpass.mv.db`, vuelve a levantar la API en una ventana propia, refresca la ventana del evento y restaura los datos de la app desde `demo\snapshots\app-data.tar`. Deja **0 acreditados, 3 participantes y los mismos códigos**. Cerrar la ventana "API" detiene el servidor.

Nota: la base es H2 en archivo (`demo\syncpass.mv.db`), no en memoria; por eso el evento y los usuarios sobreviven a un reinicio.

Los códigos fijos viven en el snapshot de la app del **emulador preparado** (build debug). Si la demo se hace con la release en un teléfono, hay que inscribir a Ana, Bruno y Carla en la app y usar los códigos que muestre esa instalación; el evento y los cupos son los mismos.

### Setup A — emulador preparado

```powershell
# 1. Encender el AVD si no esta abierto
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_37.0

# 2. Restaurar todo (API, evento y app) desde la carpeta de la entrega
cd "C:\Users\jorge\Documents\empresas\Taller de Codigo\demo"
.\reset-demo.ps1
```

El script deja la API en una ventana propia, corre `adb reverse tcp:8080 tcp:8080` (el build del emulador usa `localhost`) y arranca la app con 3 participantes y 0 acreditados. Cerrar la ventana "API" detiene el servidor.

### Setup B — release firmado en teléfono

```powershell
adb devices                      # debe aparecer el telefono autorizado
adb uninstall cl.syncpass.app    # solo si viene de otro APK (firma distinta)
adb install -r "entregas\SyncPass-1.1.0-h3-release-firmado.apk"

# Verificar que el telefono llega a la API por la WiFi
adb shell am start -a android.intent.action.VIEW -d http://192.168.1.84:8080/api/health
```

En el navegador del teléfono debe verse `{"status":"UP"}`. Luego, en la app:

1. Entrar como `monitor@syncpass.cl`.
2. Abrir **Demo Defensa** → **Agregar** e inscribir a Ana, Bruno y Carla. Anotar los códigos que genere esa instalación (cambian respecto del emulador).
3. Acreditar con el código y probar la cámara.

Si la red falla, plan B en el mismo teléfono:

```powershell
adb uninstall cl.syncpass.app
adb install -r "entregas\SyncPass-1.1.0-debug-localhost.apk"
adb reverse tcp:8080 tcp:8080
```

Cambiar de release a debug (o al revés) exige desinstalar: las firmas son distintas y Android no deja instalar encima.

### Probar cámara/QR en teléfono

1. Abrir el detalle de un participante en la app y mostrar su QR.
2. Llevar ese QR a la pantalla del PC:
   ```powershell
   adb exec-out screencap -p > qr-participante.png
   ```
3. Mostrar `qr-participante.png` en el PC y apuntar la cámara del teléfono a la pantalla.
4. Verificar la acreditación en la app y, si hace falta, en `adb logcat`.
5. Permiso denegado:
   ```powershell
   adb shell pm revoke cl.syncpass.app android.permission.CAMERA
   ```
   Al tocar **Abrir cámara** debe aparecer el aviso con la alternativa **Escribir código**.

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

1. En la app, entrar a **Acreditar**: el evento **Demo Defensa** ya está seleccionado.
2. Escribir el código de **Ana Demo** (`SPGY6PW8`) en **Código o documento** y tocar **Acreditar**.
3. Mostrar el contador `1/3 acreditados` y **Últimas acreditaciones**.
4. (Opcional) Abrir el detalle del evento y mostrar el QR de un participante.
5. Tocar **Abrir cámara** para mostrar la pantalla de escaneo.

> "La pantalla de escaneo abre con permiso concedido, y con el permiso denegado la app ofrece la alternativa manual. Lo que no se pudo verificar sin un teléfono es la lectura de un QR físico. Si la cámara falla en la sala, se sigue con el código."

### 5:00–6:30 — Encuesta

1. Acreditar a **Bruno Demo** con su código (`SPPPTLFK`).
2. Ir a **Encuestas** → **Hacer una encuesta** y elegir a Bruno (figura PRESENTE).
3. Responder la encuesta de satisfacción (5 y 5, comentario ficticio).
4. Mostrar la confirmación.

> "Solo puede responder quien figura PRESENTE en el evento. Si no hay presencia, la API responde 409 SIN_PRESENCIA."

### 6:30–8:00 — Offline y sincronización

1. Activar modo avión.
2. Acreditar a **Carla Demo** (`SPTJL9EA`).
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
| Kit fijo y reset | Este documento, §1 | `demo\snapshots\backend-syncpass.mv.db` y `demo\snapshots\app-data.tar`; reset probado 1 → 0 acreditados |
| Login y guard por rol | [roles-permisos.md](roles-permisos.md), [casos-de-uso.md](casos-de-uso.md) CU-01/CU-14 | `evidencia\h3-emulador\05-home-3-eventos.png`; tests `Login.test.tsx`, `Nav.test.tsx`, `Usuarios.test.tsx` |
| Catálogo en la app | [arquitectura.md](arquitectura.md) §5, CU-05 | `evidencia\h3-emulador\05-db-local.txt`, `backend-proyectos-final.json`, `CatalogoRepositoryTest` |
| Acreditación manual | [arquitectura.md](arquitectura.md) §6, CU-07 | `evidencia\h3-emulador\03-evento-2de2.png`, `03-ui-evento.xml` |
| QR / cámara | CU-07 y [pruebas-cobertura.md](pruebas-cobertura.md) §5 | `evidencia\defensa\escaner-qr.png` (escáner abierto) y `permiso-camara-denegado.xml`; **lectura de QR físico no verificada** |
| Encuesta con presencia | CU-11 y [requisitos.md](requisitos.md) RF-13 | `evidencia\h3-emulador\06-ui-encuesta-formulario.xml`, `06-ui-encuesta-gracias.xml`; `LoteServiceTest` |
| Offline y pendientes | [arquitectura.md](arquitectura.md) §6 | `evidencia\h3-emulador\04-ajustes-pendientes.png`, `02-persistencia-asistente.png` |
| Sync e idempotencia | CU-08, RF-11 y RNF-11 | `evidencia\h3-emulador\backend-dashboard-03-final.json`, `backend-dashboard-02-reenvio.json`, `logcat-final.txt` (worker `SUCCESS`) |
| Rechazo y descarte | CU-12 y RF-18 | `evidencia\h3-emulador\07-ajustes-rechazo.png`, `08-ajustes-descartado.png` |
| 403 real | [roles-permisos.md](roles-permisos.md) §2 | Bloque de consola de este guion; tests de seguridad del backend |
| APK firmado | `FIRMA-APK.md` de la entrega | SHA-256 `0538D515…`; esquema v2; huella `a4cd1b83…` |
| Plan B de red | Este documento, §1 y §4 | `evidencia\defensa\ajustes-debug-localhost.png` (servidor `localhost`, `MODO_DEMO` desactivado, pendientes 0) |

Las capturas y JSON viven en la carpeta de evidencia de la entrega (fuera del repositorio). Los tests y comandos sí están en este repositorio y se pueden repetir.

## 4. Si algo falla en la sala

| Falla | Plan B |
| --- | --- |
| No hay red o cambió la IP | Plan B: instalar `entregas\SyncPass-1.1.0-debug-localhost.apk`, `adb reverse tcp:8080 tcp:8080` y usar `localhost`. Sin adb, mostrar la app con datos ya descargados |
| El emulador no arranca | Usar capturas de `evidencia\h3-emulador` y el APK instalado en un teléfono |
| La API no arranca | Mostrar `npm test`, `gradlew test` y los reportes de cobertura; el panel web queda sin datos |
| La cámara no enfoca | Acreditar por código. El permiso denegado y la pantalla de escaneo están probados; la lectura de un QR físico no |
| Se llenó el cupo del evento | Usar otro evento o subir `cuposMax` desde el panel |

## 5. Qué no prometer

- No hay despliegue en la nube.
- PostgreSQL y Cognito reales no se probaron.
- La lectura de un QR físico no se verificó; sí el permiso denegado y la pantalla de escaneo.
- Las pruebas instrumentadas de Android no se ejecutaron.
- La cobertura de la app no es una métrica válida por el sandbox de Robolectric.

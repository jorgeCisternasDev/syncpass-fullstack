# Casos de uso — SyncPass v2

Actores: **Coordinador** (docente), **Monitor** (estudiante) y **Asistente** (beneficiario). Los tres existen en el backend y en el JWT.

Convenciones: *Pre* = condición previa, *Post* = resultado esperado. Los endpoints están verificados contra los controladores de `syncpass_api` y las pantallas contra `syncpass_web` y `SyncPassMVP`.

## CU-01 — Iniciar sesión

- **Actor:** cualquiera de los tres roles.
- **Pre:** la API responde y el usuario existe y está activo.
- **Flujo:**
  1. El usuario ingresa correo y contraseña en el panel web (`/login`) o en la app.
  2. El cliente llama `POST /api/auth/login`.
  3. La API valida con BCrypt (local) o Cognito y devuelve `accessToken`, `idToken` y `refreshToken`.
  4. El cliente guarda los tokens (`localStorage` en web; almacenamiento local en la app) y navega al inicio.
- **Excepciones:** credenciales inválidas → `401` con mensaje; usuario inactivo → rechazo; en la app, si la API no responde y `MODO_DEMO=true`, usa el acceso local de respaldo.
- **Post:** sesión activa con el rol del token.
- **Pruebas:** `Login.test.tsx` (4 casos), `AuthRepositoryTest`, `SyncSecurityIntegrationTest`.

## CU-02 — Administrar usuarios

- **Actor:** Coordinador.
- **Pre:** sesión con rol `Coordinador`.
- **Flujo:**
  1. Abre `/usuarios`; el panel llama `GET /api/usuarios`.
  2. Puede crear (`POST /api/auth/registro`), dar de baja (`PATCH /api/usuarios/{id}/baja`), reactivar (`.../alta`) o eliminar (`DELETE /api/usuarios/{id}`).
- **Excepciones:** Monitor o Asistente reciben `403`; correo duplicado se rechaza; eliminar un usuario con datos dependientes puede fallar por integridad.
- **Post:** la lista refleja el cambio.
- **Pruebas:** `Usuarios.test.tsx` (2), pruebas de seguridad y CRUD del backend.

## CU-03 — Mantener proyectos

- **Actor:** Coordinador (escritura); los tres roles leen.
- **Flujo:**
  1. `GET /api/proyectos` lista el catálogo.
  2. `POST /api/proyectos`, `PUT /api/proyectos/{id}` y `DELETE /api/proyectos/{id}` exigen rol Coordinador.
  3. La web valida campos y fechas antes de enviar (`catalogo.ts`).
- **Excepciones:** fechas inválidas → `400`; proyecto con eventos al eliminar → `409`; Monitor en escritura → `403`.
- **Post:** catálogo actualizado; la app lo verá en la próxima descarga.
- **Pruebas:** `Proyectos.test.tsx` (4), `catalogo.test.ts` (4), pruebas de CRUD backend.

## CU-04 — Mantener eventos

- **Actor:** Coordinador.
- **Flujo:**
  1. En `/eventos` se elige un proyecto y se listan sus eventos (`GET /api/proyectos/{id}/eventos`).
  2. Se crea o edita con `POST /api/eventos` o `PUT /api/eventos/{id}`; se elimina con `DELETE /api/eventos/{id}`.
  3. El evento exige fecha dentro del rango del proyecto, cupos, recinto y geocerca opcional.
- **Excepciones:** fecha fuera del proyecto → `400`; sin rol → `403`.
- **Post:** evento disponible para inscripción y acreditación.
- **Pruebas:** `Eventos.test.tsx` (2) y pruebas de reglas del backend.

## CU-05 — Consultar catálogo en la app

- **Actor:** Asistente y Monitor.
- **Flujo:**
  1. La app pide `GET /api/proyectos` y, por proyecto, `GET /api/proyectos/{id}/eventos`.
  2. Guarda los datos en Room con *upsert* por `remoteId`.
  3. Sin red, muestra lo último descargado.
- **Excepciones:** error de red → conserva la caché y muestra el estado; no borra filas locales.
- **Post:** catálogo disponible offline.
- **Pruebas:** `CatalogoRepositoryTest` (upsert idempotente en tres descargas).

## CU-06 — Aprovisionar participante

- **Actor:** Asistente (a través de la app).
- **Pre:** el evento existe y la app conoce su UUID local.
- **Flujo:**
  1. La app envía `POST /api/participantes` con `eventoId`, `usuarioUuid` y `nombre`.
  2. La API normaliza el UUID y lo crea como usuario `Asistente` sin contraseña, o lo reutiliza si ya existe.
  3. Si falta correo, usa `participante+<uuid>@syncpass.local`.
- **Excepciones:** evento inexistente → `409`; UUID con formato inválido → `400`; correo explícito de otro UUID → `409 EMAIL_EN_USO`.
- **Post:** existe el usuario que permite atribuir asistencias y encuestas.
- **Pruebas:** `ParticipanteService` con pruebas de idempotencia y conflicto de correo; evidencia de emulador en H3.

## CU-07 — Acreditar participación sin red

- **Actor:** Monitor o Coordinador.
- **Pre:** evento descargado y ventana de acreditación vigente.
- **Flujo:**
  1. Se abre *Acreditar* y se elige evento.
  2. Se escanea el QR o se registra manualmente el participante.
  3. La app valida duplicado, ventana y cupo local, y guarda la asistencia pendiente en Room.
  4. La UI confirma sin esperar a la red.
- **Excepciones:** participante ya acreditado → aviso; fuera de ventana → rechazo local; cámara sin permiso → alternativa manual.
- **Post:** asistencia local pendiente de sincronizar.
- **Pruebas:** `AccreditationRepository` (duplicado, índice único, contador de pendientes); evidencia de emulador.

## CU-08 — Sincronizar el lote offline

- **Actor:** app (automático) y Monitor (disparo manual opcional).
- **Pre:** hay ítems pendientes y red disponible.
- **Flujo:**
  1. WorkManager ejecuta la sincronización (al abrir, tras acreditar/encuestar y cada 15 minutos).
  2. La app envía `POST /api/sync/lote` con asistencias y encuestas.
  3. La API aplica las reglas y responde `aceptados`, `nuevos`, `actualizados`, `duplicados`, `atribuidos`, `rechazados` y `errores[]`.
  4. La app marca como sincronizados solo los ítems aceptados.
- **Excepciones:** `401` renueva token o pide login; `5xx` o red → reintenta con backoff y conserva pendientes; `4xx` de negocio → marca rechazado y no reintenta.
- **Post:** pendientes en cero o con rechazo visible.
- **Pruebas:** `SyncControllerTest`, `SyncRepository` y evidencia de reenvío 1 → 1.

## CU-09 — Inscripción que pasa a presente

- **Actor:** app / API.
- **Flujo:**
  1. Llega una asistencia con `estado=INSCRITO` y queda la fila.
  2. Más tarde llega la misma clave `(eventoId, usuarioId)` con `estado=PRESENTE`.
  3. La API actualiza la misma fila, conserva `inscritoMs` y cuenta `actualizados`.
- **Excepciones:** si la fila ya está `PRESENTE` y llega `INSCRITO`, gana `PRESENTE` y cuenta `duplicados`.
- **Post:** una sola fila por participante y evento.
- **Pruebas:** casos de `LoteServiceTest` con transición y transición inversa.

## CU-10 — Control de cupos

- **Actor:** API.
- **Flujo:** al crear una asistencia nueva se bloquea el evento, se cuentan las filas en cualquier estado y, si `cuposMax > 0` y ya está lleno, se rechaza.
- **Excepciones:** la transición de una fila existente se permite aunque el evento esté lleno.
- **Post:** el evento nunca supera su cupo con filas nuevas; dos lotes concurrentes no se cuelan por una carrera.
- **Pruebas:** `LoteServiceTest` de cupo lleno y concurrencia; bloqueo `FOR UPDATE` en orden de id.

## CU-11 — Responder encuesta

- **Actor:** Asistente.
- **Pre:** figura `PRESENTE` en el evento.
- **Flujo:**
  1. La app envía la encuesta en el lote con satisfacción, utilidad, comentario y fecha.
  2. La API valida rango 1..5 y que exista presencia.
  3. Guarda la encuesta; un reenvío cuenta como duplicado sin pisar la primera.
- **Excepciones:** sin presencia → `409 SIN_PRESENCIA`; valores fuera de rango → `400`.
- **Post:** encuesta única por evento y usuario.
- **Pruebas:** `LoteServiceTest` de presencia y duplicado; `AnswerSurveyViewModel` en la app.

## CU-12 — Descartar un rechazo

- **Actor:** Monitor o Coordinador (Ajustes de la app).
- **Flujo:**
  1. Un `4xx` de negocio deja el ítem marcado como rechazado con su motivo.
  2. Ajustes muestra el contador y el detalle.
  3. El usuario descarta el ítem; sale de la cola y no se reintenta.
- **Excepciones:** un `5xx` o un corte de red no marca rechazo: se reintenta.
- **Post:** la cola no entra en bucle y el rechazo queda visible.
- **Pruebas:** worker en estado `FAILURE` y descarte en la evidencia de emulador (`EVIDENCIA-H3-EMULADOR.md`).

## CU-13 — Ver dashboard e indicadores

- **Actor:** los tres roles (lectura).
- **Flujo:**
  1. La web abre `/` y llama `GET /api/dashboard/resumen`.
  2. Muestra usuarios, eventos, asistencias y encuestas.
  3. La app muestra indicadores por evento para el rol que corresponda.
- **Excepciones:** error de API → el panel muestra estado vacío o error sin romper la sesión.
- **Post:** totales consistentes con la base central.
- **Pruebas:** `Dashboard.test.tsx` (2) y pruebas del controlador.

## CU-14 — Navegar según el rol

- **Actor:** los tres roles.
- **Flujo:**
  1. Tras el login, el menú se arma según el rol del token.
  2. Coordinador ve Usuarios, Proyectos y Eventos con acciones de escritura.
  3. Monitor y Asistente ven solo lectura; la app oculta acciones de administración.
- **Excepciones:** acceso directo a una ruta protegida sin rol → redirección en el panel (`Guard`) y `403` en la API.
- **Post:** cada rol ve solo lo que puede usar.
- **Pruebas:** `Nav.test.tsx`, `Proyectos.test.tsx` y `Usuarios.test.tsx` (guard por rol).

## Trazabilidad rápida

| Caso | Requisitos | Pantalla / endpoint |
| --- | --- | --- |
| CU-01 | RF-01, RF-02, RNF-01 | `/login`, app Login, `POST /api/auth/login` |
| CU-02 | RF-03 | `/usuarios`, `/api/usuarios` |
| CU-03 | RF-04, RF-05, RF-06 | `/proyectos`, `/api/proyectos` |
| CU-04 | RF-07, RF-08, RF-09 | `/eventos`, `/api/eventos` |
| CU-05 | RF-16 | app Catálogo, `GET /api/proyectos` |
| CU-06 | RF-15 | app, `POST /api/participantes` |
| CU-07 | RF-10, RF-12 | app Acreditar |
| CU-08 | RF-10, RF-11, RF-17, RF-18 | `POST /api/sync/lote`, WorkManager |
| CU-09 | RF-12 | `LoteService` |
| CU-10 | RF-14 | `LoteService` |
| CU-11 | RF-13 | app Encuestas, `POST /api/sync/lote` |
| CU-12 | RF-18 | app Ajustes |
| CU-13 | RF-19 | `/`, `GET /api/dashboard/resumen` |
| CU-14 | RF-21, RF-22 | Nav web y app |

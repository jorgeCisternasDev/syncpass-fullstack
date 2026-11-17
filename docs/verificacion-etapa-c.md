# Verificación etapa C

Fecha: 16-11-2026

## Perfiles de la API

| Perfil | Comando | Resultado | Evidencia |
| --- | --- | --- | --- |
| local | `SPRING_PROFILES_ACTIVE=local` y `JWT_SECRET` en `.env` / entorno; `.\gradlew.bat bootRun` | Arranca en H2. `/api/health` 200, login 200, `GET /api/proyectos` 200 con Bearer | `_evidencia/etapa-c-local.txt` |
| cognito | `SPRING_PROFILES_ACTIVE=cognito` con issuer y pool ficticios | Arranca en H2. `/api/health` 200 con `authMode=cognito` | `_evidencia/etapa-c-cognito.txt` |
| postgres | `SPRING_PROFILES_ACTIVE=postgres` con URL ficticia | El perfil carga y falla al conectar a PostgreSQL inexistente | `_evidencia/etapa-c-postgres.txt` |

No se probó login real contra Cognito ni conexión contra PostgreSQL porque requieren credenciales y servicios externos. No se crearon recursos AWS.

## Builds y pruebas

| Proyecto | Comando | Resultado |
| --- | --- | --- |
| API | `.\gradlew.bat test --console=plain` | 8 pruebas, 0 fallos |
| API | `.\gradlew.bat build -x test --console=plain` | BUILD SUCCESSFUL |
| Web | `npm test` | 4 pruebas, 0 fallos |
| Web | `VITE_API_URL=http://localhost:8080/api npm run build` | BUILD SUCCESSFUL; URL presente en `dist` |

## Documentación

- Diagramas C4: `docs/diagramas/contexto.mmd`, `contenedores.mmd`, `componentes-api.mmd`.
- ERS: `ERS_V1_Proyecto_Fullstack_2.docx` y `.pdf`, 16 páginas.
- Variables: `syncpass_api/.env.example`, `syncpass_web/.env.example`, `syncpass_web/.env.production.example`.

## Pendientes externos

- Crear una base PostgreSQL y ejecutar el perfil `postgres` contra ella.
- Crear un user pool Cognito y probar login/refresh/logout reales.
- Definir URL de API de producción y ejecutar el build con `.env.production`.
- Desplegar API y web; no se hizo en esta etapa.
- Medir rendimiento y agregar pruebas de carga si se exige SLA.

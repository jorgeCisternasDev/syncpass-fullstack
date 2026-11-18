# Proyecto del ramo — SyncPass

Backend Spring Boot, panel web React y documentación del caso SyncPass. La app Android que consume este backend es `SyncPassMVP` (`C:\Users\jorge\AndroidStudioProjects\SyncPassMVP`, paquete `cl.syncpass.app`); la copia `cl.duoc.syncpass` del ramo DSY1105 quedó archivada y no es la app de la entrega.

## Contenido del repositorio

| Ruta | Qué es |
| --- | --- |
| `docs/` | Documentación vigente en Markdown: arquitectura, casos de uso, requisitos, roles, vistas, pruebas y cobertura |
| `syncpass_api/` | Backend Spring Boot 4.1 / Java 21 (Gradle). Tiene su propio `README.md` |
| `syncpass_web/` | Panel React 19 + TypeScript + Vite. Tiene su propio `README.md` |
| `ERS_V1_Proyecto_Fullstack_2.docx` | Especificación de la v1 (registro histórico) |
| `docs/diagramas/` | Diagramas Mermaid anteriores; no reflejan el sistema actual y quedan solo como registro |

## Documentación

| Documento | Contenido |
| --- | --- |
| [docs/arquitectura.md](docs/arquitectura.md) | C1/C2/C3 en Mermaid y explicación de cada pieza: app, API, web, base, auth, catálogo, aprovisionamiento, sync offline y WorkManager |
| [docs/casos-de-uso.md](docs/casos-de-uso.md) | 14 casos de uso con actor, flujo, excepciones y pruebas |
| [docs/requisitos.md](docs/requisitos.md) | 22 requisitos funcionales, 13 no funcionales, reglas de negocio y alcance |
| [docs/roles-permisos.md](docs/roles-permisos.md) | Matriz de permisos por endpoint, ruta web y pantalla de la app |
| [docs/vistas-navegacion.md](docs/vistas-navegacion.md) | Rutas web y de la app, flujos de acreditación y encuesta, estados de UI |
| [docs/demo.md](docs/demo.md) | Guion de demo de 10–12 minutos y matriz caso → evidencia |
| [docs/pruebas-cobertura.md](docs/pruebas-cobertura.md) | Comandos, resultados y porcentajes reales de cobertura, con límites declarados |

La documentación Markdown es la fuente vigente. La ERS en Word queda como apoyo y no se edita desde este repositorio.

## Stack

| Capa | Tecnología |
| --- | --- |
| API | Spring Boot 4.1.1, Java 21, Spring Security, Spring Data JPA, H2 / PostgreSQL |
| Web | React 19, TypeScript, Vite, `wouter`, Vitest + React Testing Library |
| App | Kotlin, Jetpack Compose, Room, Retrofit, WorkManager, Gradle |
| Auth | JWT HS256 local o tokens RS256 de Cognito (perfil opcional) |

## Puesta en marcha de la demo

### 1. API

```powershell
cd syncpass_api
Copy-Item .env.example .env    # completar JWT_SECRET (32+ bytes)
.\gradlew.bat bootRun
```

- Arranca en `http://localhost:8080` con perfil `local` (H2 en memoria).
- Verificación rápida: `GET http://localhost:8080/api/health`.
- El seed crea usuarios y eventos ficticios. Usuarios:

| Correo | Contraseña | Rol |
| --- | --- | --- |
| `coordinador@syncpass.cl` | `Coord.2026` | Coordinador |
| `monitor@syncpass.cl` | `Monitor.2026` | Monitor |
| `asistente@syncpass.cl` | `Asist.2026` | Asistente |

### 2. Panel web

```powershell
cd syncpass_web
npm install
Copy-Item .env.example .env.local   # VITE_API_URL=http://localhost:8080/api
npm run dev
```

Abre `http://localhost:5173`. El CORS de la API ya permite ese origen (`CORS_ALLOWED_ORIGINS`).

### 3. App Android

Abrir `C:\Users\jorge\AndroidStudioProjects\SyncPassMVP` en Android Studio o compilar por consola:

```powershell
cd C:\Users\jorge\AndroidStudioProjects\SyncPassMVP
.\gradlew.bat assembleDebug
```

- La URL de la API se define en `app/build.gradle.kts` (`buildConfigField "API_BASE_URL"`). Si cambia la IP del PC, hay que ajustarla y recompilar.
- Emulador: `adb reverse tcp:8080 tcp:8080` y `API_BASE_URL = "http://localhost:8080/"`.
- Dispositivo físico: usar la IP LAN del PC (`http://192.168.x.x:8080/`) y estar en la misma red.
- `MODO_DEMO=true` permite el login local si la API no responde (`monitor/1234`, `coordinador/1234`, `benjamin/1234`). Es un respaldo de demostración, no el camino normal.

## Pruebas

| Proyecto | Comando | Resultado esperado |
| --- | --- | --- |
| Backend | `cd syncpass_api; .\gradlew.bat test` | 65 pruebas en verde |
| Web | `cd syncpass_web; npm test` | 20 pruebas en verde |
| App | `cd SyncPassMVP; .\gradlew.bat testDebugUnitTest` | 79 pruebas en verde |

La app necesita **JDK 21** para las pruebas: con la JBR 25 de Android Studio, Robolectric falla con `Unsupported class file major version 69`.

## Cobertura

| Proyecto | Comando | Resultado real |
| --- | --- | --- |
| Backend | `.\gradlew.bat test jacocoTestReport` | **64,1 %** de líneas (467/729) |
| Web | `npm run test:coverage` | **50,3 %** de líneas (172/342), umbral 30 % |
| App | `testDebugUnitTest` + JaCoCo | **2,6 %** de líneas (347/13.143), **no representativo** |

La cobertura de la app se midió con JaCoCo inyectado por un *init script* externo, sin modificar su repositorio. El número no sirve como puerta de calidad porque Robolectric instrumenta las clases en su *sandbox* y no pasa por el agente: repositorios cubiertos por pruebas aparecen en 0 %. El detalle y los reportes están en [docs/pruebas-cobertura.md](docs/pruebas-cobertura.md).

## Antes de tocar el backend

Leer `AGENTS.md` en la raíz del ramo: fija el contrato que la app Android ya espera y prohíbe inventar endpoints sin justificarlos.

## Repositorios

- `syncpass-fullstack`: backend, web y documentación (este repositorio).
- `syncpass-mvp`: app Android `SyncPassMVP`.

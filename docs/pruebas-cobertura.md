# Pruebas y cobertura — SyncPass v2

Medición hecha el **2026-11-18** sobre `main` (`062b26c`) con la rama de documentación `feature/h5-docs-md`. Los números son los que entregan las herramientas; no hay estimaciones.

## 1. Suites

| Proyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `.\gradlew.bat test` | **65 pruebas, 0 fallos** |
| Web | `npm test` | **20 pruebas, 0 fallos** (7 archivos) |
| App Android | `.\gradlew.bat testDebugUnitTest` | **79 pruebas, 0 fallos** |
| Web build | `npm run build` | `tsc --noEmit` + Vite OK |

## 2. Cobertura backend (JaCoCo)

```powershell
cd syncpass_api
.\gradlew.bat test jacocoTestReport
```

| Métrica | Cubierto / total | Porcentaje |
| --- | --- | --- |
| Instrucciones | 2.117 / 3.253 | **65,1 %** |
| Ramas | 126 / 236 | **53,4 %** |
| Líneas | 467 / 729 | **64,1 %** |
| Métodos | 104 / 151 | **68,9 %** |
| Clases | 30 / 34 | **88,2 %** |

- Reporte HTML: `syncpass_api/build/reports/jacoco/test/html/index.html`.
- XML: `syncpass_api/build/reports/jacoco/test/jacocoTestReport.xml`.
- Copia de evidencia: `evidencia/h5-docs-md/api-jacoco.xml`.
- Configuración: plugin `jacoco` en `syncpass_api/build.gradle`, `toolVersion 0.8.13`, reporte XML + HTML al finalizar `test`.

## 3. Cobertura web (Vitest + v8)

```powershell
cd syncpass_web
npm run test:coverage
```

| Métrica | Cubierto / total | Porcentaje | Umbral |
| --- | --- | --- | --- |
| Sentencias | 182 / 366 | **49,72 %** | 30 % |
| Ramas | 109 / 237 | **45,99 %** | 30 % |
| Funciones | 53 / 131 | **40,45 %** | 30 % |
| Líneas | 172 / 342 | **50,29 %** | 30 % |

El umbral de 30 % está configurado en `vitest.config.ts` y **la corrida pasa**. Se excluyen `src/main.tsx`, los archivos `*.test.*` y `src/test/**`.

- Reporte HTML: `syncpass_web/coverage/index.html`.
- Resumen máquina: `syncpass_web/coverage/coverage-summary.json`.
- Evidencia: `evidencia/h5-docs-md/web-coverage.log` y `web-coverage-summary.json`.

Cobertura por archivo (resumen):

| Archivo | Líneas |
| --- | --- |
| `Login.tsx` | 88,2 % |
| `Dashboard.tsx` | 100 % |
| `auth.tsx` | 80 % |
| `api.ts` | 64,3 % |
| `App.tsx` | 0 % (solo compone rutas; se prueba vía guard) |
| `Nav.tsx` | 33,3 % |
| `Proyectos.tsx` | 37,7 % |
| `Eventos.tsx` | 44,6 % |
| `Usuarios.tsx` | 50 % |
| `catalogo.ts` | 45,9 % |

## 4. Cobertura app Android (JaCoCo) — medida, pero no representativa

```powershell
$env:JAVA_HOME = 'C:\Users\jorge\.jdks\jbr-21.0.11'
cd C:\Users\jorge\AndroidStudioProjects\SyncPassMVP
.\gradlew.bat :app:testDebugUnitTest :app:jacocoUnitTestReport `
  --init-script C:\Users\jorge\AppData\Local\Temp\opencode\jacoco-app-init.gradle
```

Resultado crudo:

| Métrica | Cubierto / total | Porcentaje |
| --- | --- | --- |
| Instrucciones | 2.479 / 104.852 | **2,4 %** |
| Ramas | 177 / 4.763 | **3,7 %** |
| Líneas | 347 / 13.143 | **2,6 %** |
| Métodos | 129 / 2.462 | **5,2 %** |
| Clases | 32 / 433 | **7,4 %** |

**Por qué el número no sirve como medida de calidad:** la mayoría de las 79 pruebas corre bajo **Robolectric**, que instrumenta las clases en su propio *sandbox* y no pasa por el agente de JaCoCo. La prueba está en el propio reporte:

- `cl/syncpass/app/data/repository/AuthRepository` (66 líneas): **0 %**, aunque `AuthRepositoryTest` pasa.
- `cl/syncpass/app/data/repository/CatalogoRepository` (82 líneas): **0 %**, aunque `CatalogoRepositoryTest` pasa.
- `cl/syncpass/app/data/repository/AccreditationRepository` (85 líneas): **0 %**, aunque sus pruebas pasan.
- Solo 32 de 427 clases registran alguna línea, y son las que se ejercitan fuera de Robolectric (`CsvBuilder`, `JwtRoles`, reglas de dominio).

Por eso **no se adopta como umbral ni como puerta de calidad**. El reporte existe y es reproducible, pero declara cobertura del subconjunto no-Robolectric.

**Límite adicional del entorno:** con la JBR 25 que trae Android Studio, las pruebas fallan con `Unsupported class file major version 69` (ASM de Robolectric). Se corrieron con el JDK 21 instalado en `C:\Users\jorge\.jdks\jbr-21.0.11`. No se modificó el repositorio de la app: JaCoCo se inyectó con un *init script* externo (`evidencia/h5-docs-md/` no contiene el script, pero el comando lo referencia en `%TEMP%\opencode\jacoco-app-init.gradle`).

- XML de evidencia: `evidencia/h5-docs-md/app-jacoco.xml`.
- Reporte HTML: `SyncPassMVP/app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`.

## 5. Qué falta medir

| Brecha | Estado |
| --- | --- |
| Pruebas instrumentadas (emulador) | No ejecutadas en esta entrega; la app se validó con unitarias y evidencia manual |
| End-to-end de navegador | No hay Playwright/Cypress; la web se prueba por componente |
| Cobertura de UI Compose | No medida; requiere pruebas instrumentadas |
| Cobertura real de la app con Robolectric | Requiere instrumentación offline o un runner compatible; no se hizo |
| PostgreSQL y Cognito reales | No probados; solo carga de perfiles |
| Carga y latencia | Sin SLA ni pruebas de carga |

## 6. Deuda de dependencias

`npm install` reportó **7 vulnerabilidades altas** en dependencias de desarrollo (6 antes de agregar `@vitest/coverage-v8`). No se aplicó `npm audit fix --force` para no romper versiones. Afectan herramientas de desarrollo, no el runtime de producción de esta entrega.

## 7. Reproducir todo

```powershell
# Backend
cd ...\proyecto\syncpass_api
.\gradlew.bat test jacocoTestReport

# Web
cd ...\proyecto\syncpass_web
npm install
npm test
npm run test:coverage
npm run build

# App (JVM 21 y emulador o dispositivo para la parte manual)
cd C:\Users\jorge\AndroidStudioProjects\SyncPassMVP
$env:JAVA_HOME = 'C:\Users\jorge\.jdks\jbr-21.0.11'
.\gradlew.bat testDebugUnitTest
```

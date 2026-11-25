# Design: tests de Studio en un navegador real

## Decisiones

| Tema | Decisión | Alternativa descartada y motivo |
|---|---|---|
| Herramienta | Playwright for Java (`com.microsoft.playwright`), solo en `test` | Selenium: esperas explícitas a mano y menos ayuda para consola, red y accesibilidad. Tests en JavaScript (Playwright Test, Vitest): obligan a meter Node en un repo Java, lo mismo que ADR-014 evitó en el frontend |
| Navegador | El Chrome instalado (`channel("chrome")`), con `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` | Descargar los navegadores de Playwright: unos 150 MB en cada máquina y en CI, cuando los runners de GitHub ya traen Chrome (lo usa `pages.yml` para el PDF) |
| Dónde | Módulo `proofstack-cli`, fuentes en `src/e2e/java` (paquete `dev.proofstack.cli`, para usar `StudioCommand.start`), añadidas solo con el perfil Maven `e2e` | En `proofstack-in-studio`: no tiene los renderers reales ni la composición de `StudioCommand`, y el test debe arrancar Studio como lo hace el usuario |
| Accesibilidad | `axe-core` cargado como recurso de test e inyectado con `page.addScriptTag` | Servicios externos: el test no debe salir a internet |

No cambia ADR-014: el frontend sigue sin build ni dependencias. Las dependencias nuevas son solo de test y solo en el perfil `e2e`.

## Estructura

- `StudioE2E` (base): copia `profiles/example/profile.yaml` a un `@TempDir`, arranca `StudioCommand.start(file, 0)`, abre un `BrowserContext` nuevo por test y registra los mensajes de consola y los `securitypolicyviolation` (R2.3). En `@AfterEach` falla si hubo alguno.
- Un test por requisito de R1 y R2 (`editsAndSaves`, `invalidFieldIsMarked`, `conflictOffersReload`, `previewTabsLoad`...). Localizadores por rol y nombre accesible (`getByRole`, `getByLabel`), no por clases CSS: así también se comprueba R3.1.
- `AccessibilityE2E`: inyecta axe y falla con violaciones `serious` o `critical` (R3.2).
- El conflicto (R1.5) se provoca escribiendo el fichero desde el test entre la carga y el guardado.

## Maven y CI

- Perfil `e2e` en `proofstack-cli/pom.xml`: añade `playwright` y ejecuta con `maven-failsafe-plugin` las clases `*E2E`. Sin el perfil, surefire no las ve.
- Job `studio-e2e` en `ci.yml` (Ubuntu), en cada push y PR, con un reintento (`-De2e.reruns=1`). Sin filtro de rutas: tarda poco, y un cambio en el esquema o en un renderer también afecta a Studio.
- Si no hay Chrome, los tests se saltan con un mensaje claro (`Assumptions`), en lugar de fallar con una traza de Playwright.

## Riesgos

- Playwright for Java incluye su driver de Node (unos 50 MB por plataforma) en el artefacto de test. Solo se descarga con `-Pe2e`.
- Los tests de navegador son más lentos y pueden ser inestables. Mitigación: esperas por estado (`waitFor`, `expect().toHaveText`), nunca `sleep`, y un solo reintento en CI.

## Lo que encontraron al implementarlos

- Cada carga ped?a `/favicon.ico` y recib?a un 404 (`b0afe58`).
- A?adir una skill o una evidencia y guardar fallaba: los enum sin `type` (?rea, categor?a) no se rellenaban al crear el elemento (`0424d33`).
- Pulsar un hallazgo dentro de un elemento de lista cerrado no llevaba el foco al campo, y las URL sal?an como textarea de tres l?neas (`da15ea2`).

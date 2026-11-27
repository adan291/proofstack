---
inclusion: always
---

# Proofstack: cómo trabajar en este repositorio

CLI en Java 25, arquitectura hexagonal (ArchUnit lo comprueba). El estado del proyecto está en `README.md` y `CHANGELOG.md` (`[Unreleased]`); léelos antes de preguntar.

## Comandos

- Compilar un módulo y lo que necesita: `mvn -B -q install "-DskipTests" -pl <modulo> -am`
- Tests de un módulo: `mvn -B install -pl <modulo>`. Uno concreto: añade `"-Dtest=Clase" "-Dsurefire.failIfNoSpecifiedTests=false"`.
- Suite completa: `mvn -B verify`. Con Chrome (Playwright): `mvn -B -Pe2e verify`. Un e2e concreto: `-pl proofstack-cli "-Dit.test=StudioWizardE2E" "-Dtest=none" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dfailsafe.failIfNoSpecifiedTests=false"`.
- Filtra la salida de Maven: `| Select-String -CaseSensitive -Pattern "Failures: [1-9]|Errors: [1-9]|BUILD|<<<"`. Los detalles de un fallo están en `target/surefire-reports` o `target/failsafe-reports`.
- Lanzador: `./proofstack <comando>` usa `proofstack-cli/target/proofstack.jar`; recompila la CLI tras cambiar código.
- Studio u otros servidores: arráncalos en segundo plano (control_pwsh_process), nunca en un comando normal.
- CI y despliegue: `Invoke-RestMethod "https://api.github.com/repos/adan291/proofstack/actions/runs?per_page=3"` (no hay `gh`).

## Cómo trabajar (ahorra contexto)

- Ejecuta solo los tests del módulo tocado mientras trabajas; la suite completa y el push, una vez al final.
- Lee ficheros por rangos o con `Select-String`; no leas ni reescribas ficheros grandes enteros si basta una edición.
- Capturas de pantalla solo si el cambio es visual y el usuario no lo va a ver él mismo.
- Commit y push al terminar cada tarea, con una línea en `CHANGELOG.md` si cambia algo visible.

## Trampas de Windows / PowerShell 5.1

- PowerShell 5.1 no entiende `` `u{00ED} ``: escribe las tildes literales con `str_replace`/`fs_write`. Si un script necesita escapes, usa un marcador propio (p. ej. `~00ED`) y conviértelo con `[regex]::Replace`.
- Lee y escribe con `[IO.File]::ReadAllText($p,[Text.Encoding]::UTF8)` y `New-Object Text.UTF8Encoding $false` (sin BOM). La consola muestra mal el UTF-8 (`├│`), aunque el fichero esté bien; compruébalo con la regex `Ã[\x80-\xBF]|Â[\xA0-\xBF]`.
- Los ficheros del repo usan LF. Si `str_replace` no encuentra un texto con varias líneas, normaliza `` "`r`n" `` a `` "`n" `` o edita por rangos.
- Argumentos de Maven con punto, siempre entre comillas: `"-DskipTests"`, `"-DnewVersion=0.8.0"`.
- `$` dentro de comillas dobles se interpola: usa comillas simples para patrones de regex.

## Reglas del proyecto

- No inventar datos del usuario ni métricas. El CV es determinista, sin IA (ADR-013).
- CV apto para ATS: una columna; el DOCX sin tablas, imágenes ni cuadros de texto. Máximo 2 páginas.
- Web y Studio: sin estilos ni scripts inline, sin dependencias externas; respetan la CSP y `prefers-reduced-motion`.
- Los adaptadores usan el core solo por puertos (`core.port.in`/`out`) y dominio, nunca `core.application`.
- Nunca guardar teléfono, tokens ni secretos en el repositorio. La app de LinkedIn vive en `~/.proofstack/linkedin.properties`.
- La documentación no usa el perfil real como ejemplo: `profiles/example` y `https://<usuario>.github.io/<repo>/`.
- `profiles/adan/profile.yaml` es del usuario: no lo subas ni lo cambies sin que lo pida.

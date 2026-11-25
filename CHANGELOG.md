# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones siguen [SemVer](https://semver.org/lang/es/).

## [Unreleased]

### Añadido
- Lanzador `proofstack` (`proofstack.cmd` en Windows) en la raíz del repositorio: `./proofstack <comando>` funciona en PowerShell, macOS y Linux sin crear alias. Usa el jar compilado, o un `proofstack.jar` descargado, y si no hay ninguno lo compila con Maven.
- Guía (`docs/guide/`): instalación, recetas para el día a día, piezas opcionales (borradores con IA, MCP en Kiro, asistente) y qué hacer si cambias el código o publicas una versión.

### Corregido
- Avisos del compilador: posibles fugas de recursos al arrancar Studio, un Javadoc suelto y un `serialVersionUID`. `javac -Xlint:all` no da avisos.

## [0.7.0] - 2026-11

### Añadido
- **CV completo** (`proofstack cv`): Markdown, HTML imprimible y Word (`cv.docx`), en una columna y apto para filtros ATS. Sin IA: solo contiene lo que dice el perfil y omite los `TODO` (ADR-013).
- `cv --focus <área>` y `cv --projects N` para tener un CV por objetivo.
- `cv --job oferta.txt`: ordena proyectos y skills según una oferta e informa de lo que la oferta pide y el perfil nunca menciona. No añade texto al CV.
- `build --with-cv`: publica el CV junto a la web con un botón *Descargar CV*. El workflow de Pages genera `cv.pdf` con Chrome.
- **Studio** (`proofstack studio`): editor del perfil en el navegador, con formularios generados desde el esquema, avisos de `doctor` junto a cada campo y vista previa. Solo escucha en `127.0.0.1`, valida antes de guardar, escribe de forma atómica con copia `.bak` y detecta cambios hechos en disco (ADR-014).
- **Importación desde la API de LinkedIn** (`import linkedin-api`), para miembros del EEE y Suiza, con OAuth en local y el token solo en memoria (ADR-012).
- `import ... --merge-into`: LinkedIn aporta la trayectoria y el perfil conserva proyectos y evidencias.
- Secciones `languages` y `certifications` en el perfil, el CV y los importadores de LinkedIn.
- Formato `evidence-draft` en `generate`.
- Guía visual en `docs/guide/`, publicada en la web en `/guide/`.

### Cambiado
- Todos los módulos pasan a la versión `0.7.0` (antes `0.1.0-SNAPSHOT`).
- Acciones de GitHub actualizadas a `checkout@v5` y `setup-java@v5`.
- README y `docs/getting-started.md` reescritos para empezar desde cero, con una tabla de problemas frecuentes.
- Los tests ya no dependen del perfil real del autor.

### Corregido
- Los errores de la API de LinkedIn muestran el código y el mensaje de LinkedIn (nunca el token) en vez de un 401 genérico.

## [0.6.0] - 2026-11

### Añadido
- Plantilla pública: `proofstack init`, `proofstack doctor` (bloquea la publicación con `TODO` o enlaces de ejemplo), workflow de Pages y releases con SHA-256 (ADR-011).

[0.7.0]: https://github.com/adan291/proofstack/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/adan291/proofstack/releases/tag/v0.6.0

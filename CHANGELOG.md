# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones siguen [SemVer](https://semver.org/lang/es/).

## [Unreleased]

### Añadido
- Lanzador `proofstack` (`proofstack.cmd` en Windows) en la raíz del repositorio: `./proofstack <comando>` funciona en PowerShell, macOS y Linux sin crear alias. Usa el jar compilado, o un `proofstack.jar` descargado, y si no hay ninguno lo compila con Maven.
- Guía (`docs/guide/`): instalación, recetas para el día a día, piezas opcionales (borradores con IA, MCP en Kiro, asistente) y qué hacer si cambias el código o publicas una versión.

### Corregido
- Studio y los importadores escribían sin comillas textos como `1e3`, `.inf` o `.NaN`. Al releer el fichero eran números, o el YAML ni siquiera se abría. Ahora van entre comillas, y antes de escribir se comprueba que el YAML se relee igual: si no, no se guarda nada.
- Con un `profile.yaml` que no era YAML válido, Studio respondía "internal error". Ahora indica la línea y la columna del fallo y pide corregirlo en un editor de texto. No abre el formulario vacío, porque guardarlo pisaría el fichero.
- `doctor` no revisaba skills, la ubicación y las tecnologías de cada puesto, el stack de los proyectos, los idiomas, las certificaciones (ni su enlace) ni el nombre de los labs. Un `TODO` o una URL de ejemplo en esos campos se publicaba. Ahora bloquea la publicación, y `TodoCounter` cuenta los mismos campos.
- El README y la salida de `studio` decían que el token era "de un solo uso". Vale mientras Studio esté abierto, y ahora lo dicen así.
- En la consola de Windows, `doctor` mostraba "?" en lugar de "→", "–" o "…". Ahora usa ASCII en lo que añade, y la CLI escribe en la codificación de la terminal (UTF-8 si se redirige a un fichero), así que los acentos del perfil se leen bien.
- La ayuda de `cv` no mencionaba `cv.docx`, y los subcomandos no aceptaban `--help` (salían con código 2).
- Login de LinkedIn: cualquier página local podía abortarlo con una petición al callback que llegara antes que la de LinkedIn, y el parámetro `error` se escribía tal cual en la terminal. Ahora solo cuenta el callback con el `state` correcto (el resto recibe un 400 y el login sigue esperando), y `error` se limpia antes de mostrarse.
- Un fork que borrara `profiles/adan` rompía CI y 6 tests, y el servicio RAG y Docker Compose usaban ese perfil por defecto. CI y los tests validan ahora los perfiles que haya, `generate` se prueba con un perfil de prueba propio y los valores por defecto apuntan a `profiles/example`.
- Los importadores con `--force` podían sobrescribir el perfil original si `--out` era otro nombre del mismo fichero (un enlace, o mayúsculas distintas en macOS). Ahora se comparan los ficheros, no solo las rutas. Además escriben de forma atómica (temporal y renombrado): un fallo a medias ya no deja el destino truncado.
- `build` no avisaba de los `TODO` que la web iba a mostrar. Ahora lo dice, igual que `cv`, y remite a `doctor`.
- El CV imprimía fechas que nadie había escrito. Cuando LinkedIn no da fecha de inicio, o solo da el año, el importador guarda una fecha provisional (el esquema exige mes), y el CV la mostraba como "Jan 2019 – Jan 2019". Ahora el importador lo marca con `TODO: dates`, `doctor` lo bloquea, el CV omite ese periodo y el merge conserva las fechas del perfil propio si las tiene.
- Avisos del compilador: posibles fugas de recursos al arrancar Studio, un Javadoc suelto y un `serialVersionUID`. `javac -Xlint:all` no da avisos.

### Cambiado
- `proofstack --version` lee la versión del pom, así que basta con `mvn versions:set` para publicar una versión nueva. El workflow de release falla si el tag no coincide con esa versión.

- `proofstack.jar` conservaba un solo `META-INF/NOTICE` y un `LICENSE` de 6 líneas que solo remite a la licencia. Ahora incluye los NOTICE de todas las dependencias y el texto completo de Apache-2.0, como pide la licencia.

### Seguridad
- Servicio RAG: `POST /api/ask;x=1` llegaba al controlador sin pasar por el rate limit, porque el filtro comparaba la URI sin normalizar. Ahora compara la misma ruta que usa Spring MVC. Lo comprueba un test contra Tomcat real. Detrás de Caddy esa ruta no se enrutaba, pero sí al ejecutar el jar directamente.
- CI falla si una dependencia de ejecución tiene una vulnerabilidad conocida en OSV (`.github/scripts/osv_check.py`). Dependabot propone cada semana actualizaciones de Maven, de las acciones de GitHub y de la imagen Docker.
- Dependencias con vulnerabilidades conocidas: Jackson 3.1.5 → 3.1.8 (DoS en jackson-core y jackson-databind) y Spring Boot 4.1.1 → 4.1.3 (Tomcat 11.0.24 → 11.0.27). OSV no da avisos para las 133 dependencias de ejecución.

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

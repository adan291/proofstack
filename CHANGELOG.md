# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Las versiones siguen [SemVer](https://semver.org/lang/es/).

## [Unreleased]

### Añadido
- Studio: si el fichero cambió en disco al guardar, combina esos cambios con los tuyos y pregunta solo por los campos que tocasteis los dos. Antes la única salida era recargar y perder lo editado. Añade *Descargar mis cambios*, y lo no guardado sobrevive a una recarga de la pestaña.
- Fechas con la precisión que conoces: `startDate`, `endDate` y la fecha de una certificación aceptan solo el año (`2018`, con comillas o sin ellas). El CV y la web lo muestran como «2018 – 2020», y el importador de LinkedIn guarda el año sin `TODO` (ADR-015).
- Tests de Studio en Chrome (`mvn -Pe2e verify`, con Playwright): editar y guardar, listas, errores, conflicto en disco, avisos, vista previa, teclado y auditoría axe-core. CI los ejecuta en un job aparte. Encontraron tres fallos del editor, ya corregidos.
- Lanzador `proofstack` (`proofstack.cmd` en Windows) en la raíz del repositorio: `./proofstack <comando>` funciona en PowerShell, macOS y Linux sin crear alias. Usa el jar compilado, o un `proofstack.jar` descargado, y si no hay ninguno lo compila con Maven.
- Guía (`docs/guide/`): instalación, recetas para el día a día, piezas opcionales (borradores con IA, MCP en Kiro, asistente) y qué hacer si cambias el código o publicas una versión.

### Corregido
- Studio: cada guardado pisaba `profile.yaml.bak`, y tras dos guardados el fichero escrito a mano solo quedaba en Git. Ahora el primer guardado de la sesión hace la copia y los siguientes no la tocan.
- Studio: un `profile.yaml` guardado con el Bloc de notas (UTF-8 con BOM) perdía los comentarios de cabecera al guardar.
- `cv --job`: contaba como carencia lo que el perfil dice en el resumen, la formación, las evidencias, los labs o los idiomas (por ejemplo, «inglés» con `Inglés: B2`).
- Studio: el identificador de un elemento nuevo se fijaba con la primera tecla («Proofstack» quedaba en «p»), y dos elementos con la misma inicial no se podían guardar. Ahora sigue al nombre completo hasta que el usuario lo escribe, añade -2, -3 si otro elemento ya lo usa y deja de cambiar al guardar.
- Studio: al combinar con el disco, una lista con un identificador repetido o vacío perdía elementos sin avisar. Ahora es un conflicto que decide el usuario.
- Studio: al añadir, borrar o mover un enlace dentro de un proyecto, el proyecto se cerraba y parecía que no había pasado nada.
- Studio: marcar «Es mi puesto actual» vacía y desactiva la fecha de fin, en vez de dejar rellenar las dos y fallar al guardar.
- Studio: cerrar con Esc el diálogo de conflicto ofrece recargar, igual que el botón Cancelar.
- Studio: si el fichero no se puede escribir de forma fiable, el guardado explica el motivo en vez de responder «internal error».
- ADR-012: la importación por API pide siete dominios de LinkedIn, no cinco.
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
- `import ... --merge-into` emparejaba los puestos por el nombre exacto de la empresa: "Example Corp" y "Example Corporation, S.L." eran puestos distintos, y el tuyo desaparecía del fichero nuevo con sus evidencias, sin aviso. Ahora se ignoran mayúsculas, acentos, puntuación y forma jurídica. Los puestos de tu perfil que LinkedIn no tiene se listan en un `WARN`; siguen fuera del fichero nuevo, porque la trayectoria la manda LinkedIn (ADR-012).
- `doctor`, el contador de `TODO` y el CV recorrían los campos del perfil cada uno a su manera. Por eso `doctor` no veía un `TODO` en la URL de un enlace que el contador sí contaba. Ahora los tres usan un único recorrido del dominio (`ProfileTexts`).
- Tras un `git pull`, `./proofstack` ejecutaba sin avisar el jar compilado con el código anterior. Ahora avisa si algún fuente es más reciente que `proofstack-cli/target/proofstack.jar` y dice cómo recompilar. Con un `proofstack.jar` descargado no compara nada.
- Studio: añadir una skill o una evidencia y guardar fallaba con «required property 'area' not found». Pulsar un aviso de un elemento de lista cerrado no llevaba al campo. Las URL salían como un cuadro de texto de varias líneas. Cada carga provocaba un 404 en `/favicon.ico`.
- Los importadores e `init` podían escribir un perfil que luego no pasaba `validate`, porque el dominio admite textos más largos que el esquema (300 caracteres en nombres y títulos). Ahora el writer valida contra el esquema antes de escribir y no deja ningún fichero. `init` explica el problema en vez de mostrar una traza.
- Servicio RAG: con el modelo de embeddings caído, una pregunta respondía 500 en vez del 503 «the assistant is temporarily unavailable» que documenta ADR-007. Comprobado con la imagen real y Ollama inaccesible.
- Avisos del compilador: posibles fugas de recursos al arrancar Studio, un Javadoc suelto y un `serialVersionUID`. `javac -Xlint:all` no da avisos.

### Cambiado
- Diseño del CV (HTML y PDF): color de acento en el nombre, los títulos y las viñetas; fecha y lugar alineados a la derecha del puesto; empresa resaltada; habilidades en dos columnas; resultado de cada proyecto destacado y enlace junto al título; idiomas en una línea. Sigue siendo una sola columna de texto y cabe en 2 páginas. En el DOCX, el nombre y los títulos de sección llevan el mismo color.
- `proofstack-in-yaml` y `proofstack-out-yaml` pasan a ser un solo módulo, `proofstack-yaml`, con un único codec para leer y escribir `profile.yaml` (ADR-016). Los ficheros que se escriben no cambian ni un byte.
- `proofstack --version` lee la versión del pom, así que basta con `mvn versions:set` para publicar una versión nueva. El workflow de release falla si el tag no coincide con esa versión.

- `proofstack.jar` conservaba un solo `META-INF/NOTICE` y un `LICENSE` de 6 líneas que solo remite a la licencia. Ahora incluye los NOTICE de todas las dependencias y el texto completo de Apache-2.0, como pide la licencia.

### Seguridad
- Releases: el build (Maven, plugins y tests) se ejecuta con permisos de solo lectura y sin las credenciales guardadas. Solo el job que publica la release puede escribir en el repositorio, y comprueba el SHA-256 de los jar antes.
- Studio: la vista previa (`/preview/*`) se servía a cualquier página. Otra web no podía leerla, pero sí podía saber que Studio estaba abierto y en qué puerto. Ahora se rechaza si el navegador indica que la petición viene de otro sitio (`Sec-Fetch-Site`). Quedan fuera de esta regla el CSS y el JS públicos de la vista previa, porque el iframe aislado los pide con origen opaco.
- Servicio RAG: además del límite por IP, ahora hay un límite global (`PROOFSTACK_GLOBAL_RATE_LIMIT`, 60 por minuto) y otro de preguntas a la vez (`PROOFSTACK_MAX_CONCURRENT`, 2). En IPv6 se cuenta por /64. Antes, muchas direcciones juntas no tenían tope, y un /64 rotando direcciones contaba como clientes distintos. Amplía las mitigaciones de ADR-007.
- Servicio RAG: `POST /api/ask;x=1` llegaba al controlador sin pasar por el rate limit, porque el filtro comparaba la URI sin normalizar. Ahora compara la misma ruta que usa Spring MVC. Lo comprueba un test contra Tomcat real. Detrás de Caddy esa ruta no se enrutaba, pero sí al ejecutar el jar directamente.
- Workflows: las acciones se fijan por SHA (con la versión en un comentario). En Pages, solo el job que despliega tiene `pages: write` e `id-token: write`. CI añade un job en Windows que compila, pasa los tests y comprueba que `proofstack.cmd` devuelve el código de salida.
- El workflow de la imagen del servicio RAG ya no da `packages: write` a las pull requests. Las construye sin publicar, y solo el job de los tags `v*` puede escribir en GHCR. Además fallaba en todas las pull requests, porque etiquetaba la imagen con `<n>/merge`, que no es un tag válido de Docker.
- CI falla si una dependencia de ejecución tiene una vulnerabilidad conocida en OSV (`.github/scripts/osv_check.py`). Las vulnerabilidades nuevas las avisan las alertas de seguridad de Dependabot (se activan en Settings); no hay PR semanales de versiones nuevas.
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
- `proofstack-in-yaml` y `proofstack-out-yaml` pasan a ser un solo módulo, `proofstack-yaml`, con un único codec para leer y escribir `profile.yaml` (ADR-016). Los ficheros que se escriben no cambian ni un byte.
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

# Proofstack

> Tu carrera como código: un `profile.yaml`, muchas salidas, todo demostrable.

Proofstack es una herramienta de línea de comandos en Java que genera **tu web de portfolio y tu CV** a partir de un único fichero, `profile.yaml`, guardado en tu repositorio de GitHub. Los datos los trae de **LinkedIn** y **GitHub**, los editas en un **editor en el navegador** y se publican solos en **GitHub Pages** con cada `git push`.

Está pensada para perfiles backend que quieren enseñar pruebas y no listas de tecnologías. Cada área de tu carrera (Backend, Arquitectura, IA, Liderazgo…) enlaza a:
- **proyectos:** qué problema había, cómo lo resolviste y qué aprendiste;
- **evidencias:** el contexto, lo que hiciste tú y el resultado.

Nada se inventa: lo que no está en tu perfil no aparece, y lo que está a medias (`TODO`) bloquea la publicación.

Cuando lo publiques, tu web quedará en `https://<usuario>.github.io/<repo>/` y la guía visual en `https://<usuario>.github.io/<repo>/guide/` (en el repositorio: [`docs/guide/index.html`](docs/guide/index.html)). El perfil de ejemplo, ficticio, está en [`profiles/example/profile.yaml`](profiles/example/profile.yaml).

## Arranque rápido

**Necesitas:** JDK 25 ([Temurin](https://adoptium.net/)), Git y una cuenta de GitHub. Maven 3.9 solo si compilas desde el código; si no, baja `proofstack.jar` de la última [Release](../../releases) y déjalo en la carpeta del repositorio.

```bash
# 1. Tu copia: pulsa "Use this template" en GitHub y clónala
git clone https://github.com/<usuario>/<repo>.git
cd <repo>

# 2. Se abre un asistente en el navegador: tú, GitHub, LinkedIn, proyectos y publicar
./proofstack setup

# 3. Mira el resultado en tu equipo
./proofstack build profiles/<tu-nombre>/profile.yaml --out dist --with-cv

# 4. Publica
git add -A && git commit -m "Mi perfil" && git push
```

`./proofstack` es un lanzador que funciona igual en PowerShell, macOS y Linux. La primera vez compila la herramienta (un par de minutos). Ejecútalo siempre desde la carpeta del repositorio: las rutas como `profiles/…` son relativas a ella.

Antes del primer `git push`, configura GitHub Pages: son dos ajustes, explicados en [Publicar y automatizar](#publicar-y-automatizar).

## Qué hace

### Entrada: tus datos sin teclearlos

- **`setup`** crea el perfil y abre un **asistente por pasos en el navegador**:
  1. tus datos;
  2. tus repositorios de GitHub, en una lista con casillas y buscador para elegir cuáles entran;
  3. LinkedIn: crear tu app la primera vez y traer tu trayectoria;
  4. tus proyectos, de uno en uno: qué problema resolvían, cómo y qué aprendiste;
  5. revisar y publicar, con enlaces directos a los ajustes de tu repositorio.

  Cada paso guarda al terminar, así que puedes dejarlo y volver: retoma donde lo dejaste.
- **Studio** es un editor en el navegador, con un formulario por sección y los avisos junto a cada campo. Tiene los botones *Traer de LinkedIn* y *Traer repos de GitHub*, y una vista previa del CV y de la web.
- Los importadores **nunca traen tu email ni tu teléfono**, y **nunca pisan** tus proyectos ni tus evidencias.

### Salida: lo que publicas

- **Web de portfolio estática:**
  - proyectos destacados visibles nada más entrar;
  - un mapa de tu carrera por áreas y la línea de tiempo de experiencia y formación;
  - efectos visuales que se desactivan si el visitante pide menos movimiento;
  - accesible, con CSP y sin dependencias externas.
- **CV en PDF, Word, HTML y Markdown**, de una columna y apto para filtros ATS:
  - la experiencia no técnica pasa a una línea por puesto, en «Otra experiencia»;
  - se puede adaptar a una oferta concreta o centrar en un área;
  - el teléfono se añade solo en la copia que envías, nunca en la publicada.

### Automatización: se mantiene solo

- **Cada `git push`** publica la web, el CV y esta guía en GitHub Pages.
- **Cada lunes** te llega un pull request con tus repositorios nuevos de GitHub.
- **Cada tres meses** te llega una issue para traer LinkedIn. LinkedIn no permite que un programa lea tu perfil sin que tú lo autorices.

### Extras opcionales

- Borradores de posts, README o guiones con un modelo local (Ollama) o Gemini.
- Un asistente que responde preguntas sobre tu perfil citando las fuentes.
- Un servidor MCP para consultar tu perfil desde Kiro.

## El día a día

| Quiero… | Hago… |
|---|---|
| Empezar o poner al día todo de una vez | `./proofstack setup` |
| Cambiar algo de mi perfil | `./proofstack studio profiles/<yo>/profile.yaml` y después `git push` |
| Traer lo nuevo de LinkedIn o GitHub | Botones *Traer de LinkedIn* o *Traer repos de GitHub* en Studio |
| Saber si puedo publicar | `./proofstack doctor profiles/<yo>/profile.yaml` |
| Ver la web y el CV antes de publicar | `./proofstack build profiles/<yo>/profile.yaml --out dist --with-cv` |
| Un CV para una oferta | `./proofstack cv profiles/<yo>/profile.yaml --out dist/cv-oferta --job oferta.txt` |
| Un CV con mi teléfono para enviarlo | `./proofstack cv profiles/<yo>/profile.yaml --out dist/cv-privado --phone "600 11 22 33"` |
| Que un repositorio no vuelva a ofrecerse | Añadir su nombre a `profiles/<yo>/github-ignore.txt` |

La guía (`https://<usuario>.github.io/<repo>/guide/#recetas`) tiene cada receta paso a paso.

Para abrir lo generado:

```powershell
Start-Process dist\index.html          # la web
Start-Process dist\cv\cv.html          # el CV; Ctrl+P → Guardar como PDF
Start-Process dist\cv\cv.docx          # el CV en Word
```
En macOS: `open dist/index.html`. En Linux: `xdg-open`. En local, el botón *Descargar CV* abre `cv.html`; el PDF lo genera el despliegue.

## Comandos

En los ejemplos, `proofstack` es el lanzador: escribe `./proofstack` desde la carpeta del repositorio. `--help` en cualquier comando muestra todas sus opciones.

| Comando | Para qué |
|---|---|
| `setup [DIR]` | Crea el perfil si hace falta y abre el asistente por pasos (con `--yes`, lo hace todo en la terminal) |
| `studio PROFILE [--wizard]` | Editor en el navegador, con importación, avisos y vista previa; `--wizard` abre el asistente |
| `doctor PROFILE` | Qué falta antes de publicar: `TODO`, enlaces de ejemplo, avisos |
| `validate PROFILE` | Solo comprueba que el fichero cumple el esquema |
| `build PROFILE --out DIR [--with-cv]` | Genera la web, y con `--with-cv`, también el CV |
| `build … --alternate en=en/` | Enlaza la misma web en otro idioma. Si existe `profile.en.yaml` junto al perfil, el despliegue la publica en `/en/` |
| `cv PROFILE --out DIR` | Genera el CV (`--job`, `--focus`, `-p`, `--phone`) |
| `import linkedin ZIP` · `import linkedin-api` · `import github USER` | Importadores sueltos, siempre a un fichero nuevo |
| `init DIR --name … --headline …` | Un perfil vacío para rellenar a mano |
| `generate FORMAT PROFILE -s PROYECTO` | Borradores de contenido con IA |

### setup

```bash
proofstack setup                                 # pregunta lo que no encuentra
proofstack setup profiles/yo --github yo         # sin preguntas sobre GitHub
proofstack setup --linkedin-zip Basic_LinkedInDataExport.zip   # LinkedIn con el ZIP en vez de la API
proofstack setup profiles/yo --skip-linkedin --no-studio --yes # sin preguntas, como en un workflow
```

Con una persona delante, `setup` solo crea el fichero si no existe (te pregunta tu nombre) y abre el **asistente** en el navegador. También se llega a él desde Studio, con el botón *Asistente*. Con `--yes` o `--no-studio` hace los mismos pasos en la terminal, como en un workflow:

1. **Perfil:** usa `profiles/<tu-nombre>/profile.yaml`, o lo crea.
2. **GitHub:** toma tu usuario del enlace de GitHub del perfil, o te lo pregunta. Trae los repositorios públicos que aún no están y te deja elegir cuáles entran. Los que descartas se apuntan en `github-ignore.txt` y no vuelven a ofrecerse.
3. **LinkedIn:** la primera vez te explica cómo crear tu app de LinkedIn (5 minutos) y te pide el Client ID y el Client Secret. Los guarda en `~/.proofstack/linkedin.properties`, fuera del repositorio y legible solo por tu usuario. Después abre LinkedIn para que pulses *Permitir* e importa tu trayectoria. El token de acceso nunca se guarda.
4. **Lo que falta:** ejecuta `doctor` y abre Studio.

Puedes repetirlo cuando quieras. Si no hay nada nuevo, el fichero no cambia ni un byte; si cambia, conserva los comentarios de la cabecera.

### studio

```bash
proofstack studio profiles/yo/profile.yaml
```

- **Un formulario por sección**, con los avisos de `doctor` junto a cada campo y una vista previa del CV y de la web.
- ***Traer de LinkedIn*** y ***Traer repos de GitHub*** mezclan lo nuevo en lo que estás editando, con las mismas reglas que `import`, y te enseñan qué ha entrado. No guardan nada hasta que pulses *Guardar*.
- **Seguridad:** solo escucha en `127.0.0.1`, y la URL lleva un token secreto válido mientras Studio siga abierto. No compartas esa URL.
- **Guardado:**
  - nunca guarda un perfil inválido;
  - el primer guardado de cada sesión deja el fichero anterior en `profile.yaml.bak`;
  - si el fichero cambia en disco mientras editas, combina esos cambios con los tuyos y te pregunta solo por los campos que tocasteis los dos.
- ***Descargar mis cambios*** baja lo editado como `profile.edited.json`. Lo no guardado sobrevive a una recarga accidental de la pestaña.

Decisiones en [ADR-014](docs/adr/ADR-014-studio-local-editor.md).

### cv

```bash
proofstack cv profiles/yo/profile.yaml --out dist/cv                        # cv.md, cv.html y cv.docx
proofstack cv profiles/yo/profile.yaml --out dist/cv-ia --focus ai -p 4     # un área primero, 4 proyectos
proofstack cv profiles/yo/profile.yaml --out dist/cv-oferta --job oferta.txt
proofstack cv profiles/yo/profile.yaml --out dist/cv-privado --phone "600 11 22 33"
```

- **Sin IA:** el CV solo contiene lo que dice tu perfil, y los `TODO` se omiten.
- **Apto para ATS:** una columna y solo texto; el Word usa estilos de título nativos.
- **Selección:** elige los proyectos mejor documentados (con resultado, problema y solución, y enlace), y en caso de empate respeta el orden del perfil.
- **Otra experiencia:** si unos puestos listan tecnologías y otros no, los segundos van en una línea cada uno.
- **Fechas:** escribe solo lo que sabes, el año (`2018`) o el año y el mes (`2018-05`) ([ADR-015](docs/adr/ADR-015-year-precision-dates.md)).
- **`--job`:** lo que coincide con la oferta pasa delante, y la salida lista lo que la oferta pide y **tu perfil nunca menciona**. Decides tú si lo añades. Es una heurística y puede listar alguna palabra corriente.
- **`--phone`:** también se puede guardar en la variable `PROOFSTACK_PHONE`. Nunca se guarda en el perfil, que es público.
- **PDF:** lo genera el despliegue con Chrome. En local, abre `cv.html` y usa *Imprimir → Guardar como PDF*.

Decisiones en [ADR-013](docs/adr/ADR-013-cv-renderer.md).

### import

```bash
proofstack import linkedin Basic_LinkedInDataExport.zip --out profiles/yo/profile.yaml
proofstack import linkedin-api --merge-into profiles/yo/profile.yaml --out profiles/yo/profile.linkedin.yaml
proofstack import github tu-usuario --profile profiles/yo/profile.yaml --out profiles/yo/profile.github.yaml
```

- **Siempre escriben un fichero nuevo:** tu perfil no se toca, y lo que falte queda marcado como `TODO`.
- **`--merge-into`:** LinkedIn aporta la trayectoria y tu perfil conserva sus proyectos y evidencias.
- **API de LinkedIn:** solo para cuentas del EEE y Suiza. La configuración está en [`docs/linkedin-api.md`](docs/linkedin-api.md). Con cualquier otra cuenta, usa el ZIP (*Ajustes → Privacidad de datos → Obtener una copia de tus datos*).
- **GitHub:** se salta forks, archivados y lo que hay en `github-ignore.txt`. `GITHUB_TOKEN` es opcional y solo sube el límite de peticiones.

### generate

```bash
proofstack generate linkedin-post profiles/yo/profile.yaml --subject mi-proyecto -m llama3.1    # Ollama, local
export GEMINI_API_KEY=...        # PowerShell: $env:GEMINI_API_KEY="..."
proofstack generate readme profiles/yo/profile.yaml -s mi-proyecto -p gemini -o README.draft.md
```

Formatos: `linkedin-post`, `readme`, `cv-bullets`, `video-script`, `evidence-draft` y `notebooklm-brief`. Si el modelo falla no se inventa nada, y cualquier número que añada y no esté en tu perfil sale como `WARNING` ([ADR-006](docs/adr/ADR-006-honest-content.md)).

## Publicar y automatizar

La primera vez, en tu repositorio de GitHub:

1. **Settings → Pages → Source:** *GitHub Actions*.
2. **Settings → Secrets and variables → Actions → Variables → Repository variables:** `PROOFSTACK_PROFILE` = `profiles/tu-nombre/profile.yaml`. Tiene que ser una variable de repositorio, no de un *environment*.
3. **Settings → Actions → General → Workflow permissions:** marca *Allow GitHub Actions to create and approve pull requests*. Hace falta para el pull request semanal.

Después, los workflows trabajan solos:

| Workflow | Cuándo | Qué hace |
|---|---|---|
| *Deploy portfolio* | Cada push a `main` | Pasa `doctor`, construye la web y el CV, genera el PDF y publica en `https://<usuario>.github.io/<repo>/` |
| *Sync profile* | Cada lunes | Abre un pull request con tus repositorios públicos nuevos como proyectos en borrador |
| *Sync profile* | Cada tres meses | Abre una issue para que traigas LinkedIn desde Studio |
| *CI* | Cada push a `main` y cada pull request | Compila y pasa los tests y las reglas de arquitectura |
| *Release CLI* | Cada tag `v*` | Publica `proofstack.jar` con su SHA-256 |

Mientras un proyecto tenga `TODO`, `doctor` bloquea el despliegue: un pull request aceptado sin completar no cambia tu web.

## Privacidad y seguridad

- **Importadores:** nunca traen email ni teléfono. El email solo aparece si lo escribes tú en el perfil, y el teléfono nunca se publica.
- **Claves de LinkedIn:** se guardan en tu carpeta de usuario, con permisos solo para ti. El token de acceso vive en memoria y nunca se escribe.
- **Studio:** solo es accesible desde tu equipo, con un token por sesión, comprobación de origen y CSP.
- **Web y CV:** estáticos y sin scripts de terceros. La CSP impide cargar nada de fuera.
- **Ficheros fuera de Git:** `profile.linkedin.yaml` y `dist/` no se suben.

## Arquitectura

Java 25 con arquitectura hexagonal: un núcleo sin frameworks y un adaptador por cada entrada y cada salida.

```
LinkedIn ────► in-linkedin ──┐                              ┌─► out-web ─► web estática
GitHub ──────► in-github ────┼─► core (dominio, casos de uso ├─► out-cv  ─► CV (md, html, docx)
navegador ───► in-studio ────┘   y puertos)                 └─► out-llm ─► Ollama / Gemini ─► borradores
                                       ▲
                                       ▼
                         yaml ◄─► profile.yaml (leer, editar y escribir)
```

| Módulo | Responsabilidad |
|---|---|
| `proofstack-core` | Dominio, casos de uso y puertos. Solo Java, sin frameworks |
| `adapters/proofstack-yaml` | `profile.yaml` en los dos sentidos, con un único codec y el esquema JSON ([ADR-016](docs/adr/ADR-016-one-adapter-per-persistence-format.md)) |
| `adapters/proofstack-in-linkedin` | ZIP y API de portabilidad de LinkedIn, y la app guardada de cada usuario |
| `adapters/proofstack-in-github` | Repositorios públicos como proyectos en borrador |
| `adapters/proofstack-in-studio` | Editor local: servidor HTTP del JDK en `127.0.0.1` y frontend sin build |
| `adapters/proofstack-out-web` | Web estática accesible, con CSP, en `es` y `en` |
| `adapters/proofstack-out-cv` | CV en Markdown, HTML y Word, y ajuste a una oferta |
| `adapters/proofstack-out-llm` | Prompts versionados y salida validada, con Ollama y Gemini |
| `proofstack-cli` | Raíz de composición y comandos |
| `proofstack-ai-service` | Asistente «pregúntale a mi portfolio» (Spring AI), opcional |
| `proofstack-mcp-server` | Servidor MCP de solo lectura, opcional |

ArchUnit comprueba en cada build que el núcleo no conoce los adaptadores y que los adaptadores no se conocen entre sí. Hay unos 360 tests, entre ellos tests de navegador con Playwright y una auditoría de accesibilidad con axe-core (`mvn -Pe2e verify`). Las decisiones están en [`docs/adr`](docs/adr) y cada fase empezó como una spec en [`docs/specs`](docs/specs).

### Piezas opcionales

<details>
<summary>«Pregúntale a mi portfolio» (servicio RAG)</summary>

Responde preguntas usando **solo** tu perfil y cita las fuentes. Si no encuentra la respuesta, dice que no está ([ADR-007](docs/adr/ADR-007-rag-service.md)).

```bash
ollama pull nomic-embed-text && ollama pull llama3.1
PROOFSTACK_PROFILE=profiles/yo/profile.yaml PROOFSTACK_ALLOWED_ORIGINS=https://yo.github.io \
java -jar proofstack-ai-service/target/proofstack-ai-service-*.jar
proofstack build profiles/yo/profile.yaml --out dist --assistant-url https://ai.yo.dev   # añade el widget
```

| Variable | Por defecto |
|---|---|
| `PROOFSTACK_PROFILE` | `profiles/example/profile.yaml` |
| `OLLAMA_HOST` | `http://localhost:11434` |
| `PROOFSTACK_CHAT_MODEL` / `PROOFSTACK_EMBEDDING_MODEL` | `llama3.1` / `nomic-embed-text` |
| `PROOFSTACK_ALLOWED_ORIGINS` | vacío (solo mismo origen) |
| `PROOFSTACK_RATE_LIMIT` | `10` preguntas por minuto y por IP (en IPv6, por /64) |
| `PROOFSTACK_GLOBAL_RATE_LIMIT` | `60` preguntas por minuto entre todos los visitantes |
| `PROOFSTACK_MAX_CONCURRENT` | `2` preguntas respondiéndose a la vez |
| `PROOFSTACK_MIN_SCORE` | `0.5` |

La API es pública y sin autenticación, por diseño. La protegen el rate limit, el límite de longitud, el CORS restringido y unos errores que no revelan detalles internos. Despliegue con Docker Compose, Caddy, Prometheus y Grafana en [`deploy/`](deploy) ([ADR-009](docs/adr/ADR-009-observability-and-deployment.md)).
</details>

<details>
<summary>Tu perfil dentro de Kiro (MCP)</summary>

`proofstack-mcp-server` es un servidor MCP local (stdio) de **solo lectura** ([ADR-008](docs/adr/ADR-008-mcp-server-and-jackson-3.md)), con las herramientas `get_profile_summary`, `list_projects`, `get_project`, `search_portfolio` y `get_content_facts`. Configúralo con [`docs/kiro-mcp.example.json`](docs/kiro-mcp.example.json) y pide, por ejemplo: *«Escribe un post de LinkedIn sobre mi proyecto agent-life usando proofstack»*.
</details>

<details>
<summary>Laboratorio de Valhalla</summary>

[`labs/valhalla-lab`](labs/valhalla-lab/RESULTS.md) compara con JMH un `record` y un `value record` (JEP 401, JDK 28 EA). Está fuera del build principal ([ADR-010](docs/adr/ADR-010-valhalla-lab-results.md)).
</details>

## Desarrollo

```bash
mvn verify                                    # compila, tests y reglas de arquitectura
mvn -Pe2e verify                              # además, tests en Chrome
mvn -DskipTests package -pl proofstack-cli -am   # para que ./proofstack use tu cambio
```

El diseño de la web está en `adapters/proofstack-out-web/src/main/resources/themes/architecture/`. El del CV, en `adapters/proofstack-out-cv/src/main/resources/cv/`. Un campo nuevo del perfil se añade en tres sitios: `schema/profile.schema.json`, el dominio de `proofstack-core` y `adapters/proofstack-yaml`; Studio lo muestra solo. Cómo publicar una versión nueva: en la guía, sección *Si cambias el código*.

## Roadmap

- [x] Fase 0–1: cimientos y portfolio estático
- [x] Fase 2: motor de contenido con IA e importador de LinkedIn
- [x] Fase 3: «pregúntale a mi portfolio» (RAG)
- [x] Fase 4: servidor MCP e importador de GitHub
- [x] Fase 5: observabilidad, contenedores y laboratorio de Valhalla
- [x] Fase 6: plantilla pública (`init`, `doctor`, releases)
- [x] Fase 7: CV completo (PDF, Word, ajuste a ofertas), API de LinkedIn y Studio
- [x] Fase 8: `setup`, importación desde Studio y sincronización automática
- [ ] `doctor --check-links`: comprobar que los enlaces del perfil responden

Historial de versiones en [`CHANGELOG.md`](CHANGELOG.md).

## Licencia

[Apache-2.0](LICENSE)

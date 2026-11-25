# Proofstack

> Tu carrera como código: un `profile.yaml`, muchas salidas, todo demostrable.

Proofstack genera el portfolio técnico y el CV de un perfil backend desde un único fichero versionado en Git. En lugar de listar tecnologías, cada área (Backend, Arquitectura, IA, Liderazgo…) enlaza a **proyectos** (problema → solución → aprendizajes) y a **evidencias** (contexto → acción → resultado). Nada se inventa: lo que no está en tu perfil no aparece, y lo que está a medias (`TODO`) bloquea la publicación.

**Ejemplo real:** [adan291.github.io/proofstack](https://adan291.github.io/proofstack/), generado desde [`profiles/adan/profile.yaml`](profiles/adan/profile.yaml).

## Qué obtienes con un solo fichero

| Salida | Comando |
|---|---|
| Web estática, accesible y con CSP, publicada en GitHub Pages | `proofstack build` |
| CV en PDF (desde la web), HTML, Markdown y Word, apto para filtros ATS | `proofstack cv` |
| CV adaptado a una oferta, sin añadir nada que no tengas | `proofstack cv --job oferta.txt` |
| Editor en el navegador, con validación y vista previa | `proofstack studio` |
| Importación desde LinkedIn (ZIP o API) y GitHub | `proofstack import …` |
| Borradores de posts, README y guiones con un LLM local o Gemini | `proofstack generate …` |
| Respuestas sobre tu perfil desde Kiro u otro cliente MCP | `proofstack-mcp-server` |

## Empieza desde cero

**Requisitos:** JDK 25 ([Temurin](https://adoptium.net/)). Maven 3.9 solo si compilas desde el código. Git y una cuenta de GitHub para publicar.

**Guía visual paso a paso:** [adan291.github.io/proofstack/guide](https://adan291.github.io/proofstack/guide/) (en tu copia, `https://<usuario>.github.io/<repo>/guide/`). La misma guía en texto: [`docs/getting-started.md`](docs/getting-started.md).

### 1. Prepara la herramienta

Pulsa **Use this template**, clona tu copia y entra en su carpeta. El repositorio trae un lanzador, `proofstack`, que funciona igual en PowerShell, macOS y Linux:

```bash
./proofstack --version
```

La primera vez compila la herramienta con Maven (un par de minutos). Sin Maven, baja `proofstack.jar` de la última [Release](../../releases) y déjalo en la carpeta del repositorio. Ejecuta siempre desde esa carpeta: las rutas como `profiles/…` son relativas a ella.

### 2. Crea y rellena tu perfil

```bash
./proofstack init   profiles/tu-nombre --name "Tu Nombre" --headline "Backend Engineer" --language es
./proofstack studio profiles/tu-nombre/profile.yaml     # editor en el navegador; Ctrl+C para cerrarlo
./proofstack doctor profiles/tu-nombre/profile.yaml     # ¿se puede publicar? Bloquea TODOs y enlaces de ejemplo
```

### 3. Lanza la web y el CV en tu equipo

```bash
./proofstack build profiles/tu-nombre/profile.yaml --out dist --with-cv
```

Ábrelos así:

```powershell
# Windows (PowerShell)
Start-Process dist\index.html          # la web, con el botón "Descargar CV"
Start-Process dist\cv\cv.html          # el CV; Ctrl+P → Guardar como PDF
Start-Process dist\cv\cv.docx          # el CV en Word
```
```bash
# macOS (en Linux usa xdg-open)
open dist/index.html dist/cv/cv.html dist/cv/cv.docx
```

En local, el botón *Descargar CV* abre `cv.html`. El PDF lo genera el workflow al publicar; si lo quieres ya, imprímelo desde el navegador.

### 4. Publícalo

Sigue [Publicar en GitHub Pages](#publicar-en-github-pages). Cada `git push` a `main` vuelve a construir y publicar la web, el CV en PDF y en Word, y la guía.

**Para el día a día** (actualizar el perfil, un CV por oferta, traer LinkedIn otra vez, publicar una versión nueva) la guía tiene [recetas paso a paso](https://adan291.github.io/proofstack/guide/#recetas).

## Comandos

En los ejemplos, `proofstack` es el lanzador: escribe `./proofstack` desde la carpeta del repositorio.

### Edita tu perfil sin tocar YAML

```bash
proofstack studio profiles/yo/profile.yaml
```

Abre en el navegador un editor con un formulario por sección, los avisos de `doctor` junto a cada campo y una vista previa del CV y de la web.
- Solo escucha en `127.0.0.1` y la URL lleva un token de un solo uso.
- Nunca guarda un perfil inválido, y antes de cada guardado deja una copia `profile.yaml.bak`.
- Si el fichero cambia en disco mientras editas (otro editor, `git pull`), te pide recargar en vez de pisar esos cambios.
- Conserva los comentarios de la cabecera del YAML; los que haya dentro del fichero, no.

Decisiones en [ADR-014](docs/adr/ADR-014-studio-local-editor.md).

### Genera tu CV

```bash
proofstack cv profiles/yo/profile.yaml --out dist/cv                        # cv.md, cv.html y cv.docx
proofstack cv profiles/yo/profile.yaml --out dist/cv-ia --focus ai -p 4     # un CV por objetivo
proofstack build profiles/yo/profile.yaml --out dist --with-cv              # la web con botón "Descargar CV"
```

- **Sin IA:** el CV solo contiene lo que dice tu perfil, y los `TODO` se omiten.
- **Apto para ATS:** una columna, solo texto, títulos nativos en el Word.
- **PDF:** el workflow de Pages lo genera con Chrome en cada despliegue, y el botón de la web lo descarga. En local, abre `cv.html` y usa *Imprimir → Guardar como PDF*.
- Incluye idiomas y certificaciones si tu perfil los tiene.

**Adáptalo a una oferta.** Guarda el texto de la oferta en un fichero:

```bash
proofstack cv profiles/yo/profile.yaml --out dist/cv-oferta --job oferta.txt
```

Los proyectos y las skills que coinciden pasan delante, y la salida te dice qué pide la oferta que **tu perfil nunca menciona**. Decides tú si lo tienes y lo añades. Es una heurística y puede listar alguna palabra corriente. Decisiones en [ADR-013](docs/adr/ADR-013-cv-renderer.md).

### Importa tu trayectoria

```bash
# LinkedIn, export ZIP (Ajustes → Privacidad de datos → Obtener una copia de tus datos)
proofstack import linkedin Basic_LinkedInDataExport.zip --out profiles/yo/profile.yaml

# LinkedIn, API oficial (miembros del EEE y Suiza). Configuración en docs/linkedin-api.md
proofstack import linkedin-api --merge-into profiles/yo/profile.yaml --out profiles/yo/profile.linkedin.yaml

# GitHub: añade como proyectos tus repos públicos que aún no estén en el perfil
proofstack import github tu-usuario --profile profiles/yo/profile.yaml --out profiles/yo/profile.github.yaml
```

- Importa perfil, experiencia, formación, proyectos, skills, idiomas y certificaciones. **Nunca importa email ni teléfono.**
- Con `--merge-into`, LinkedIn aporta la trayectoria y tu perfil conserva sus proyectos y evidencias.
- Siempre escribe en un fichero nuevo: tu perfil original no se toca. Lo que falte queda marcado como `TODO`.
- GitHub se salta forks y archivados (`--include-forks` para incluir los forks). `GITHUB_TOKEN` es opcional y solo sube el límite de peticiones.

### Genera contenido a partir de tus proyectos

```bash
proofstack generate linkedin-post profiles/yo/profile.yaml --subject mi-proyecto -m llama3.1    # Ollama, local
export GEMINI_API_KEY=...        # PowerShell: $env:GEMINI_API_KEY="..."
proofstack generate readme profiles/yo/profile.yaml -s mi-proyecto -p gemini -o README.draft.md
```

Formatos: `linkedin-post`, `readme`, `cv-bullets`, `video-script`, `evidence-draft` y `notebooklm-brief`. Reglas de honestidad ([ADR-006](docs/adr/ADR-006-honest-content.md)):
- Si el modelo falla, no se inventa nada: obtienes un error.
- Cualquier número que el modelo añada y no esté en tu perfil sale como `WARNING`.

### "Pregúntale a mi portfolio" (opcional)

Servicio RAG con Spring Boot y Spring AI que responde preguntas usando **solo** tu perfil y cita las fuentes ([ADR-007](docs/adr/ADR-007-rag-service.md)). Si no encuentra la respuesta, dice que no está.

```bash
ollama pull nomic-embed-text && ollama pull llama3.1
PROOFSTACK_PROFILE=profiles/yo/profile.yaml PROOFSTACK_ALLOWED_ORIGINS=https://yo.github.io \
java -jar proofstack-ai-service/target/proofstack-ai-service-*.jar
proofstack build profiles/yo/profile.yaml --out dist --assistant-url https://ai.yo.dev   # añade el widget
```

| Variable | Por defecto |
|---|---|
| `PROOFSTACK_PROFILE` | `profiles/adan/profile.yaml` |
| `OLLAMA_HOST` | `http://localhost:11434` |
| `PROOFSTACK_CHAT_MODEL` / `PROOFSTACK_EMBEDDING_MODEL` | `llama3.1` / `nomic-embed-text` |
| `PROOFSTACK_ALLOWED_ORIGINS` | vacío (solo mismo origen) |
| `PROOFSTACK_RATE_LIMIT` | `10` preguntas por minuto y por IP |
| `PROOFSTACK_MIN_SCORE` | `0.5` |

La API es pública y sin autenticación, por diseño. La protegen el rate limit, el límite de longitud, el CORS restringido y unos errores que no revelan detalles internos. Despliegue con Docker Compose, Caddy, Prometheus y Grafana en [`deploy/`](deploy) y [ADR-009](docs/adr/ADR-009-observability-and-deployment.md).

### Úsalo desde Kiro (MCP)

`proofstack-mcp-server` es un servidor MCP local (stdio) de **solo lectura** ([ADR-008](docs/adr/ADR-008-mcp-server-and-jackson-3.md)): `get_profile_summary`, `list_projects`, `get_project`, `search_portfolio` y `get_content_facts`. Configúralo con [`docs/kiro-mcp.example.json`](docs/kiro-mcp.example.json) y pide, por ejemplo: *"Escribe un post de LinkedIn sobre mi proyecto tomcat-jakarta-migration usando proofstack"*.

## Publicar en GitHub Pages

1. **Settings → Pages → Source:** *GitHub Actions*.
2. **Settings → Secrets and variables → Actions → Variables → Repository variables:** `PROOFSTACK_PROFILE` = `profiles/tu-nombre/profile.yaml`. Tiene que ser una variable de repositorio, no de entorno.
3. Haz push a `main`. El workflow pasa `doctor`, construye la web con el CV, genera el PDF y publica.

## Arquitectura

```
profile.yaml ─► in-yaml ─────┐                          ┌─► out-web  ─► web estática
LinkedIn ────► in-linkedin ──┤                          ├─► out-cv   ─► CV (md, html, docx)
GitHub ──────► in-github ────┼─► core (dominio+puertos) ├─► out-llm  ─► Ollama / Gemini ─► borradores
navegador ───► in-studio ────┘                          └─► out-yaml ─► profile.yaml
```

| Módulo | Responsabilidad |
|---|---|
| `proofstack-core` | Dominio, casos de uso y puertos. Solo Java, sin frameworks. |
| `adapters/proofstack-in-yaml` | YAML → JSON Schema → dominio, y el perfil como documento editable (escritura atómica) |
| `adapters/proofstack-in-linkedin` | Export ZIP y API de portabilidad de LinkedIn → perfil borrador |
| `adapters/proofstack-in-github` | Repos públicos → proyectos borrador |
| `adapters/proofstack-in-studio` | Editor local: servidor HTTP del JDK en `127.0.0.1` y frontend sin build |
| `adapters/proofstack-out-web` | Web estática accesible, con CSP, en `es` y `en` |
| `adapters/proofstack-out-cv` | CV en Markdown, HTML y Word, y ajuste a una oferta |
| `adapters/proofstack-out-llm` | Prompts versionados y salida validada, con Ollama y Gemini |
| `adapters/proofstack-out-yaml` | Escribe `profile.yaml` (lo usan los importadores) |
| `proofstack-cli` | Raíz de composición y CLI |
| `proofstack-ai-service` | Servicio RAG opcional (Spring AI) |
| `proofstack-mcp-server` | Servidor MCP de solo lectura |

ArchUnit comprueba en cada build las reglas hexagonales: el core no conoce los adaptadores y los adaptadores no se conocen entre sí. Las decisiones están en [`docs/adr`](docs/adr) y cada fase empezó como una spec en [`docs/specs`](docs/specs).

[`labs/valhalla-lab`](labs/valhalla-lab/RESULTS.md) compara con JMH un `record` y un `value record` (JEP 401, JDK 28 EA). Está fuera del build principal ([ADR-010](docs/adr/ADR-010-valhalla-lab-results.md)).

## Roadmap

- [x] Fase 0–1: cimientos y portfolio estático
- [x] Fase 2: motor de contenido con IA e importador de LinkedIn
- [x] Fase 3: "pregúntale a mi portfolio" (RAG)
- [x] Fase 4: servidor MCP e importador de GitHub
- [x] Fase 5: observabilidad, contenedores y laboratorio de Valhalla
- [x] Fase 6: plantilla pública (`init`, `doctor`, releases)
- [x] Fase 7: CV completo (PDF, Word, ajuste a ofertas), API de LinkedIn y Studio
- [ ] `doctor --check-links`: comprobar que los enlaces del perfil responden

Historial de versiones en [`CHANGELOG.md`](CHANGELOG.md).

## Licencia

[Apache-2.0](LICENSE)

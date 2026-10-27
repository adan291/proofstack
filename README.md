# Proofstack

> Tu carrera como código: un `profile.yaml`, muchas salidas, todo demostrable.

Proofstack genera portfolios técnicos para perfiles backend desde un único fichero versionado en Git. En lugar de listar tecnologías, cada área (Backend, Arquitectura, IA, Hardware, Liderazgo…) enlaza a **proyectos** (problema → solución → aprendizajes) y a **evidencias** (contexto → acción → resultado). Con el mismo fichero genera borradores de posts, README, viñetas de CV y guiones de vídeo.

El portfolio de su autor está hecho con Proofstack: [`profiles/adan`](profiles/adan/profile.yaml).

## Uso rápido

**¿Quieres el tuyo?** Usa esta plantilla y sigue [`docs/getting-started.md`](docs/getting-started.md). Son unos 15 minutos y no necesitas saber Java.

```bash
proofstack init   profiles/tu-nombre --name "Tu Nombre" --headline "Backend Engineer" --language es
proofstack doctor profiles/tu-nombre/profile.yaml     # ¿se puede publicar ya? Bloquea TODOs y enlaces de ejemplo
proofstack build  profiles/tu-nombre/profile.yaml --out dist
```

Para compilar desde el código necesitas JDK 25 y Maven 3.9 o superior:

```bash
mvn verify                                   # build + tests + reglas de arquitectura
alias proofstack="java -jar proofstack-cli/target/proofstack.jar"
```

### Empieza desde tu LinkedIn

Descarga tus datos en LinkedIn: *Ajustes → Privacidad de datos → Obtener una copia de tus datos*. Después:

```bash
proofstack import linkedin Basic_LinkedInDataExport.zip --out profiles/yo/profile.yaml
```

Importa perfil, experiencia, formación, proyectos y skills (clasificadas por área). **Nunca importa email ni teléfono.** Lo que falte queda marcado como `TODO`.

### Genera contenido a partir de tus proyectos

```bash
# Local y gratis con Ollama (por defecto)
proofstack generate linkedin-post profiles/yo/profile.yaml --subject mi-proyecto -m llama3.1

# Gemini: la clave solo se lee de la variable de entorno
export GEMINI_API_KEY=...        # PowerShell: $env:GEMINI_API_KEY="..."
proofstack generate readme profiles/yo/profile.yaml -s mi-proyecto -p gemini -o README.draft.md
```

Formatos disponibles: `linkedin-post`, `readme`, `cv-bullets`, `video-script` (para grabar con OBS) y `notebooklm-brief` (fuente para NotebookLM).

Reglas de honestidad ([ADR-006](docs/adr/ADR-006-honest-content.md)):
- Si el modelo falla, no se inventa nada: obtienes un error.
- Cualquier número que el modelo añada y no esté en tu perfil sale como `WARNING`.

### "Pregúntale a mi portfolio" (opcional)

Servicio RAG con Spring Boot 4.1 y Spring AI 2.0 que responde preguntas usando **solo** tu perfil y cita las fuentes ([ADR-007](docs/adr/ADR-007-rag-service.md)). Si no encuentra la respuesta en el perfil, dice que no está.

```bash
ollama pull nomic-embed-text && ollama pull llama3.1
PROOFSTACK_PROFILE=profiles/yo/profile.yaml \
PROOFSTACK_ALLOWED_ORIGINS=https://yo.github.io \
java -jar proofstack-ai-service/target/proofstack-ai-service-0.1.0-SNAPSHOT.jar

curl -X POST localhost:8080/api/ask -H 'Content-Type: application/json' -d '{"question":"¿Ha usado Kafka?"}'
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

La API es pública y no tiene autenticación, por diseño. La protegen el rate limit, el límite de longitud de las preguntas, el CORS restringido y unos errores que no revelan detalles internos. Si la pones detrás de un proxy, activa `server.forward-headers-strategy` para que el rate limit vea la IP real.

### Importa tus repositorios de GitHub

```bash
proofstack import github tu-usuario --profile profiles/yo/profile.yaml --out profiles/yo/profile.github.yaml
```

Añade como proyectos los repos públicos que aún no estén en tu perfil, comparando por id y por URL. Se salta forks y archivados (`--include-forks` para incluir los forks). Escribe **siempre en un fichero nuevo**: tu perfil original no se toca. `GITHUB_TOKEN` es opcional y solo sirve para subir el límite de peticiones a la API.

### Úsalo desde Kiro (MCP)

`proofstack-mcp-server` es un servidor MCP local (stdio) de **solo lectura** ([ADR-008](docs/adr/ADR-008-mcp-server-and-jackson-3.md)). Ofrece estas herramientas:

| Tool | Para qué |
|---|---|
| `get_profile_summary` | Quién eres y qué áreas cubre tu portfolio |
| `list_projects` / `get_project` | Proyectos con problema, solución, stack y evidencias |
| `search_portfolio` | Búsqueda por palabras clave. Sin resultados = "no está en el portfolio" |
| `get_content_facts` | Hechos y reglas de honestidad para que el asistente redacte posts o README sin inventar |

Configúralo en `.kiro/settings/mcp.json` (tienes un ejemplo en [`docs/kiro-mcp.example.json`](docs/kiro-mcp.example.json)) y pregunta, por ejemplo: *"Escribe un post de LinkedIn sobre mi proyecto tomcat-jakarta-migration usando proofstack"*.

### Despliegue y observabilidad del asistente

```bash
mvn -pl proofstack-ai-service -am -DskipTests package
GRAFANA_ADMIN_PASSWORD=... docker compose -f deploy/compose.yaml up --build
# Grafana http://localhost:3000 · Prometheus http://localhost:9090 · API http://localhost:8080/api/ask

# Producción, con Caddy y TLS automático. Solo se publican los puertos 80 y 443:
PROOFSTACK_DOMAIN=ai.tu-dominio.dev PROOFSTACK_ALLOWED_ORIGINS=https://tu.github.io GRAFANA_ADMIN_PASSWORD=... \
docker compose -f deploy/compose.yaml -f deploy/compose.prod.yaml up -d
```

- **Métricas** (`proofstack_ask_seconds{status}`, `proofstack_ask_rate_limited_total`, `proofstack_knowledge_chunks`) y llamadas al LLM (`gen_ai_client_operation_*` de Spring AI).
- **Endpoints de gestión** en el puerto 8081, que nunca se publica.
- **Privacidad:** el texto de las preguntas nunca llega a métricas ni a logs.

Más detalle en [ADR-009](docs/adr/ADR-009-observability-and-deployment.md).

### Laboratorio de Valhalla (JDK 28 EA)

[`labs/valhalla-lab`](labs/valhalla-lab/RESULTS.md) compara con JMH un `record` y un `value record` (JEP 401). Está fuera del build principal y en este equipo dio estos resultados:
- **Recorrer arrays:** entre 2,1× y 4,4× más rápido con `value record`.
- **Crear objetos en masa:** sin mejora, y con un 40 % más de memoria asignada en este caso.

Decisión: no se adopta hasta que sea estable en un LTS ([ADR-010](docs/adr/ADR-010-valhalla-lab-results.md)).

### Crea el tuyo

1. Haz fork del repo.
2. Importa tu LinkedIn o copia `profiles/example` a `profiles/<tu-nombre>`.
3. `proofstack validate` te dice exactamente qué falla y dónde (`$.projects[0].name: …`).
4. Publica `dist/` en GitHub Pages con [`.github/workflows/pages.yml`](.github/workflows/pages.yml).

## Arquitectura

```
LinkedIn ZIP ─► in-linkedin ─┐                         ┌─► out-web  ─► dist/ (web estática)
                             ├─► core (dominio+puertos) ├─► out-llm  ─► Gemini / Ollama ─► borradores
profile.yaml ─► in-yaml ─────┘                         └─► out-yaml ─► profile.yaml
```

| Módulo | Responsabilidad |
|---|---|
| `proofstack-core` | Dominio, casos de uso, puertos y `GroundingCheck`. Solo Java, sin frameworks. |
| `adapters/proofstack-in-yaml` | YAML → JSON Schema → dominio, con errores localizados |
| `adapters/proofstack-in-linkedin` | Export ZIP/CSV de LinkedIn → perfil borrador, con límites anti zip-bomb |
| `adapters/proofstack-out-web` | Web estática accesible y con CSP, en `en`/`es` |
| `adapters/proofstack-out-llm` | Prompts versionados, salida estructurada validada, Gemini y Ollama |
| `adapters/proofstack-out-yaml` | Escribe `profile.yaml` (usado por los importadores) |
| `proofstack-cli` | Raíz de composición y CLI |
| `proofstack-ai-service` | Servicio RAG opcional: Spring AI (`VectorStore` + `ChatModel`), REST, rate limit, CORS |
| `adapters/proofstack-in-github` | Repos públicos de GitHub → proyectos borrador (paginación segura, token opcional) |
| `proofstack-mcp-server` | Servidor MCP stdio de solo lectura (Spring AI `@McpTool`) |

Las reglas hexagonales (el core no conoce los adaptadores y los adaptadores no se conocen entre sí) se comprueban con ArchUnit en cada build. Las decisiones están en [`docs/adr`](docs/adr).

## Roadmap

- [x] Fase 0–1: cimientos y portfolio estático
- [x] Fase 2: motor de contenido con IA e importador de LinkedIn
- [x] Fase 3: RAG "pregúntale a mi portfolio" (servicio opcional con Spring AI)
- [x] Fase 4: servidor MCP e importador de GitHub
- [x] Fase 5: observabilidad, contenedores y laboratorio de Valhalla (JDK 28 EA)
- [x] Fase 6: plantilla pública (`init`, `doctor`, publicación bloqueada si hay placeholders, releases)
- [ ] Hardware Lab: ESP32 + MQTT + Spring Boot ("prepárame un café")

## Licencia

[Apache-2.0](LICENSE)

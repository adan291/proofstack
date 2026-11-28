# Specs (Spec-Driven Development)

Cada fase de Proofstack empezó como un spec de Kiro con tres partes: requisitos (formato EARS), diseño y tareas. Después se implementó tarea por tarea. Los ADR de [`../adr`](../adr) recogen las decisiones que salieron de cada uno.

| Fase | Spec |
|---|---|
| 0–1 · Cimientos y web estática | [`proofstack-mvp`](proofstack-mvp/requirements.md) |
| 2 · Motor de contenido e importador de LinkedIn | [`proofstack-content-engine`](proofstack-content-engine/requirements.md) |
| 3 · "Pregúntale a mi portfolio" (RAG) | [`proofstack-ask-portfolio`](proofstack-ask-portfolio/requirements.md) |
| 4 · Servidor MCP e importador de GitHub | [`proofstack-mcp-github`](proofstack-mcp-github/requirements.md) |
| 5 · Observabilidad, contenedores y Valhalla | [`proofstack-ops-valhalla`](proofstack-ops-valhalla/requirements.md) |
| 6 · Plantilla pública | [`proofstack-template`](proofstack-template/requirements.md) |
| 7 · Studio: editor local, CV en Word y PDF | [`proofstack-studio`](proofstack-studio/requirements.md) |

Tras la revisión de noviembre de 2026:

| Tema | Spec |
|---|---|
| Tests de Studio en un navegador real (H9), hecha | [`proofstack-studio-browser-tests`](proofstack-studio-browser-tests/requirements.md) |
| Fechas con precisión de año (H3), hecha | [`proofstack-year-dates`](proofstack-year-dates/requirements.md) |
| Conflictos en Studio sin perder ediciones (H23), hecha | [`proofstack-studio-merge`](proofstack-studio-merge/requirements.md) |
| Un solo codec YAML (H10), hecha | [`proofstack-yaml-codec`](proofstack-yaml-codec/requirements.md) |
| Asistente de textos con IA en Studio, con cualquier proveedor, hecha | [`proofstack-ai-assistant`](proofstack-ai-assistant/requirements.md) |

En las fases, las tareas sin marcar (`- [ ]`) son pasos que dependen de cuentas o hardware del autor. En las propuestas, están por hacer.

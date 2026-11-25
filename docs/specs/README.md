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

Las tareas sin marcar (`- [ ]`) son pasos que dependen de cuentas o hardware del autor.

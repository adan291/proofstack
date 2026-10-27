# ADR-005: Adaptador LLM con JDK HttpClient (Spring AI se reserva para el servicio)

- Estado: aceptada
- Fecha: 2026-10

## Contexto

La Fase 2 genera contenido desde la CLI: un proceso corto que hace una sola petición y termina. Spring AI 2.0 ofrece abstracciones de proveedor muy completas, pero exige un contexto de Spring Boot 4.1. Para un comando de un solo uso eso supone más tiempo de arranque, un JAR más pesado y bastante configuración.

## Decisión

- El adaptador `proofstack-out-llm` habla directamente con las APIs REST de Gemini (`generateContent`, con `responseFormat` y JSON Schema) y de Ollama (`/api/chat`, con `format`), usando `java.net.http.HttpClient` y Jackson.
- Los proveedores implementan una interfaz interna `LlmClient`. El core solo conoce el puerto `ContentGenerator`.
- Los prompts son recursos versionados (`src/main/resources/prompts/*.md`), no strings en el código.
- La clave de Gemini solo se lee de `GEMINI_API_KEY`. Viaja en la cabecera `x-goog-api-key` y se oculta en cualquier mensaje de error.
- Spring AI se adoptará en `proofstack-ai-service` (Fase 3, RAG + MCP), donde ya hay un servidor de vida larga.

## Consecuencias

- Hay cero dependencias nuevas aparte de Jackson y el validador de esquemas, que ya se usaban, y el arranque de la CLI es instantáneo.
- Añadir un proveedor (por ejemplo OpenAI) supone una clase de unas 80 líneas y un test con servidor HTTP fake.
- Hay que seguir los cambios de las APIs REST de cada proveedor. Queda mitigado porque los tests fijan el contrato que esperamos.

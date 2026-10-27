# ADR-007: "Pregúntale a mi portfolio" como servicio RAG opcional

- Estado: aceptada
- Fecha: 2026-10

## Contexto

Queremos que un reclutador pueda preguntar "¿qué ha hecho con Kafka?" y reciba una respuesta con fuentes. ADR-002 exige que la web siga siendo estática y funcione sin backend. ADR-006 exige que no se invente nada.

## Decisión

- **Servicio aparte** (`proofstack-ai-service`), con Spring Boot 4.1 y Spring AI 2.0. Es la primera pieza de larga vida del proyecto, así que aquí sí compensa usar Spring (ver ADR-005).
- **El dominio del RAG vive en el core:**
  - `ProfileKnowledge` trocea el perfil en fragmentos con ids estables (`project:<id>`, `evidence:<id>`…) y sin email ni ubicación.
  - `AskService` aplica las reglas: sin fragmentos relevantes (`minScore`) responde `UNKNOWN` **sin llamar al LLM**; las citas tienen que apuntar a fragmentos recuperados; una respuesta sin citas válidas pasa a `UNKNOWN`; las cifras que no salen en las fuentes generan avisos.
- **Adaptadores Spring AI:**
  - Índice: `VectorStore` (`SimpleVectorStore` en memoria). Se reindexa al arrancar porque el perfil es pequeño.
  - Embeddings: `nomic-embed-text`, con sus prefijos de tarea.
  - Respuesta: `ChatModel` + `BeanOutputConverter`. No uso `ChatClient` con plantillas para que llaves o sintaxis de plantilla en los datos del perfil nunca se interpreten.
- **API pública sin autenticación**, por diseño: es un portfolio público. Mitigaciones:
  - rate limit por IP (10/min), preguntas de 3 a 500 caracteres;
  - CORS solo para el origen del portfolio;
  - errores genéricos sin detalles internos (503);
  - `remoteAddr` en lugar de `X-Forwarded-For` sin validar.
- **Widget opcional**: `proofstack build --assistant-url`. Solo admite https (http únicamente en localhost) y añade `connect-src` al CSP limitado a ese origen. Pinta la respuesta con `textContent` y sin JS no aparece.

## Consecuencias

- Si el servicio se cae, el portfolio sigue funcionando y el widget muestra "no disponible".
- Cambiar Ollama por Gemini u OpenAI es cambiar de starter de Spring AI. El core no se toca.
- El rate limit y el índice viven en memoria. Si se despliegan varias instancias (Fase 5) habrá que llevarlos a Redis o a un vector store persistente.
- `minScore` depende del modelo de embeddings y hay que calibrarlo. Con `nomic-embed-text` y 0.5, las preguntas fuera de tema ("¿sabe cocinar paella?") devolvieron `UNKNOWN` en la prueba real.

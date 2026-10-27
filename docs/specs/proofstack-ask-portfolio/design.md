# Design: Proofstack Fase 3

```
profile.yaml ─► in-yaml ─► Profile ─► ProfileKnowledge.chunks() ─► KnowledgeIndex.index()   (al arrancar)

POST /api/ask ─► AskController ─► AskService ─► KnowledgeIndex.search() ─► AnswerGenerator.answer() ─► Answer
  (in.rest)       rate limit      (core)         Spring AI VectorStore       Spring AI ChatClient
                  CORS                           (SimpleVectorStore +        (Ollama llama3.1)
                                                  nomic-embed-text)
```

## Core: `dev.proofstack.core.domain.knowledge`
- `KnowledgeChunk(id, ChunkType, title, text)`.
- `ProfileKnowledge.chunks(Profile)`. Ids: `person`, `experience:<id>`, `education:<id>`, `project:<id>`, `evidence:<id>`, `lab:<id>`, `skills:<area>`.
- `ScoredChunk(chunk, score)`.
- `Answer(Status ANSWERED|UNKNOWN, text, List<KnowledgeChunk> citations, warnings)`.
- `DraftAnswer(answerable, text, List<String> citedIds)`: lo que devuelve el generador.
- `InvalidQuestionException`.

## Puertos
- out `KnowledgeIndex`: `index(List<KnowledgeChunk>)`, `search(String query, int topK)` → `List<ScoredChunk>`.
- out `AnswerGenerator`: `answer(String question, String language, List<KnowledgeChunk> context)` → `DraftAnswer`. Lanza `ContentGenerationException` si falla.
- in `AskPortfolioUseCase`: `ask(String question)` → `Answer`.
- in `IndexKnowledgeUseCase`: `index()` → número de fragmentos indexados.

## `AskService` (core.application)
1. `question.strip()`. Si tiene menos de 3 o más de 500 caracteres → `InvalidQuestionException`.
2. `search(question, topK)` y filtro `score >= minScore`. Si queda vacío → `UNKNOWN` sin llamar al LLM.
3. `generator.answer(...)`.
4. Citas: se mantienen solo las de ids recuperados. Cada descarte genera el aviso "dropped citation".
5. Si no es `answerable`, el texto está vacío o no quedan citas → `UNKNOWN`.
6. `GroundingCheck` sobre el texto de la respuesta frente a los textos recuperados → avisos.

`RetrievalPolicy(topK=4, minScore=0.5)` es configurable.

## Módulo `proofstack-ai-service` (Spring Boot 4.1.1, Spring AI 2.0.1)
- `dev.proofstack.service.ProofstackAiServiceApplication` y `ServiceConfiguration`: raíz de composición. Carga el perfil con `YamlProfileSource` y crea `AskService`.
- `dev.proofstack.adapter.out.springai`:
  - `VectorStoreKnowledgeIndex`: `VectorStore` (por defecto `SimpleVectorStore`) con prefijos de embedding configurables. Por defecto son los de nomic: `search_document: ` / `search_query: `.
  - `ChatClientAnswerGenerator`: `ChatClient` con system prompt de recurso, contexto en `<portfolio-data>` y `.entity(DraftAnswerDto.class)`. Las excepciones de transporte se traducen a `ContentGenerationException(PROVIDER_UNAVAILABLE)`.
- `dev.proofstack.adapter.in.rest`:
  - `AskController`: `POST /api/ask` y `GET /api/status`.
  - `ApiExceptionHandler`: 400, 503 y 500, con mensajes genéricos.
  - `RateLimitFilter`: ventana fija por `remoteAddr`, solo en `/api/ask`.
  - `CorsConfig`.
- Configuración: `application.yml`, con `OLLAMA_HOST`, el modelo y `PROOFSTACK_PROFILE` (ruta) desde el entorno.
- Tests:
  - Unitarios de los adaptadores con un `EmbeddingModel` determinista y un `ChatModel` fake.
  - `@WebMvcTest` del controlador (o MockMvc standalone).
  - ArchUnit.

## Widget web
- `ArchitectureThemeRenderer(Optional<URI> assistantEndpoint)`. La CLI añade la opción `--assistant-url`.
- Plantilla: `<section class="ask" hidden data-endpoint=... data-msg-*>`, con formulario, `<output aria-live="polite">` y lista de citas.
- CSP: `connect-src <origen>`.
- `app.js`: quita `hidden`, hace `fetch` POST JSON, escribe con `textContent` y deshabilita el botón mientras espera.

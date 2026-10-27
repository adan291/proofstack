# Design: Proofstack Fase 2

## Visión general

```
profile.yaml ─► in-yaml ─► Profile ─► GenerateContentService ─► ContentGenerator (puerto)
                                         │                          │
                                         │                  out-llm: LlmContentGenerator
                                         │                    ├─ PromptLibrary (recursos versionados)
                                         │                    ├─ GeminiClient  ─┐ LlmClient
                                         │                    └─ OllamaClient  ─┘ (JDK HttpClient)
                                         └─► GroundingCheck (core) ─► GeneratedContent + avisos

LinkedIn export.zip ─► in-linkedin-export ─► Profile ─► out-yaml: YamlProfileWriter ─► profile.yaml
```

## Core (`dev.proofstack.core`)

**domain.content**
- `ContentFormat`: LINKEDIN_POST(3000), README, CV_BULLETS, VIDEO_SCRIPT, NOTEBOOKLM_BRIEF. Cada uno con `maxLength` (opcional) y un `slug` CLI.
- `ContentSubject`: sealed, con `ProjectSubject(Project)` y `EvidenceSubject(Evidence)`. Se crea con `ContentSubject.resolve(profile, id)`.
- `ContentBrief(format, language, PersonalInfo author, ContentSubject subject, List<Evidence> related)`: lo que se envía al generador.
- `GeneratedContent(format, title, body, hashtags, warnings)`, con el método `toMarkdown()`.
- `GroundingCheck.findUnsupportedNumbers(String generated, String source)`: lista de números o porcentajes que aparecen en `generated` pero no en `source`. Ignora años de 4 dígitos que sí aparecen en la fuente, y versiones como "Tomcat 10" o "Java 25" si están en la fuente.
- `ContentGenerationException(reason, Kind)`, donde Kind es PROVIDER_UNAVAILABLE o INVALID_OUTPUT.

**Puertos**
- in: `GenerateContentUseCase.generate(Path profile, String subjectId, ContentFormat, Optional<String> language)`.
- in: `ImportProfileUseCase.importFrom(Path source, Path target, boolean force)`, que devuelve `ImportReport(todoCount, counts)`.
- out: `ContentGenerator.generate(ContentBrief)`, que devuelve `GeneratedContent` sin avisos.
- out: `ProfileImporter.importProfile(Path)` y `ProfileWriter.write(Profile, Path)`.

**Servicios**
- `ContentService`: carga el perfil, resuelve el sujeto, construye el brief, llama al generador, aplica la validación de longitud (lanza INVALID_OUTPUT si se supera) y añade los avisos de veracidad.
- `ImportService`: importa, comprueba que el destino no exista salvo con `force`, escribe y cuenta los TODO.

## Adaptador `adapters/proofstack-out-llm`

- `LlmClient` (paquete interno): `String completeJson(String system, String user, String jsonSchema)`.
- `GeminiClient`: `POST {base}/v1beta/models/{model}:generateContent`.
  - Cabecera `x-goog-api-key`.
  - Cuerpo: `contents`, `systemInstruction` y `generationConfig.responseFormat.text{mimeType, schema}`.
  - Lee `candidates[0].content.parts[0].text`.
- `OllamaClient`: `POST {host}/api/chat` con `{model, messages, stream:false, format: schema, options:{temperature:0.4}}`. Lee `message.content`.
- Los dos clientes usan timeouts de 10 s para conectar y 120 s para la petición.
- Errores HTTP: se mapean a PROVIDER_UNAVAILABLE con el código de estado. El cuerpo de la respuesta se trunca a 300 caracteres y nunca incluye la clave.
- `PromptLibrary`: carga desde `prompts/<format>.md` y `prompts/system.md` y sustituye `{{language}}`. Los datos van en un bloque `<profile-data>` en JSON.
- `LlmContentGenerator`: construye el JSON de datos con Jackson, llama al cliente, valida la respuesta contra `content.schema.json` (networknt) y la mapea.
- `LlmProviders.create(provider, model, env)` es la factoría que usa la CLI. El entorno se inyecta como `Map<String,String>` para poder testear.

**Tests**: servidor HTTP fake (`com.sun.net.httpserver`) para Gemini y Ollama, que comprueba la cabecera, el path, el esquema enviado, las respuestas inválidas, el 401 y el timeout.

## Adaptador `adapters/proofstack-in-linkedin`
- `CsvParser`: RFC 4180 con comillas, saltos de línea y BOM.
- `LinkedInExportImporter implements ProfileImporter`.
  - Lee el ZIP con `ZipFile`. Máximo 200 entradas y 5 MiB por entrada leída. Busca por nombre de fichero sin tener en cuenta mayúsculas ni carpetas.
  - Columnas por nombre de cabecera, no por posición.
  - Fechas: `LinkedInDates.parse`, con meses en inglés y en español.
  - Ids: slug de empresa o proyecto. Si se repiten, se añade un sufijo `-2`, `-3`…
  - `SkillAreaClassifier`: mapa de palabras clave a áreas.

## Adaptador `adapters/proofstack-out-yaml`
- `YamlProfileWriter implements ProfileWriter`: convierte el dominio a un mapa ordenado y lo escribe con Jackson YAML (`MINIMIZE_QUOTES`). Las fechas se escriben como `YYYY-MM`.
- **Test de ida y vuelta**: escribir, leer con `YamlProfileSource` y comparar igualdad.

## CLI
```
proofstack generate <format> <profile> --subject <id> [--provider ollama|gemini] [--model m] [--language es] [--out f.md]
proofstack import linkedin <export.zip> --out <profile.yaml> [--force]
```
Los avisos de veracidad se imprimen por stderr.

## Decisiones
- ADR-005: HTTP propio (JDK HttpClient) para el adaptador LLM de la CLI. Spring AI se reserva para `ai-service` (Fase 3), porque arrancar un contexto de Spring en una CLI de un solo comando no compensa.
- ADR-006: honestidad del contenido. Sin fallbacks falsos y con avisos de veracidad.

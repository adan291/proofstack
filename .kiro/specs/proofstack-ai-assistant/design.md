# Design: asistente de textos con IA en Studio

## Decisiones

| Tema | Decisión | Alternativa descartada y motivo |
|---|---|---|
| Proveedores | Cuatro protocolos (`openai`, `anthropic`, `gemini`, `ollama`) y presets en un JSON | Un cliente por proveedor: clases casi iguales que envejecen a distinto ritmo |
| Salida estructurada en `openai` | `json_schema`, y ante un 400 `json_object` y después nada; se recuerda el formato que funcionó; siempre se valida en el cliente | Exigir `json_schema`: los servidores locales y algunos proveedores no lo admiten |
| Salida estructurada en `anthropic` | El esquema va en el prompt de sistema y se valida la respuesta | Una función en beta: añade una dependencia que puede cambiar |
| Configuración | `~/.proofstack/ai.json` (o `PROOFSTACK_HOME`), nombre de la variable de la clave, entorno > fichero > preset | Guardarla en `profile.yaml` (se publica en Git) o en el navegador |
| Contexto | El perfil guardado en disco, sin teléfono ni email | El documento del navegador: peores respuestas y un canal para mandar contenido arbitrario al proveedor |
| Consentimiento | Lo aplica el servidor (409 hasta aceptar) y se recuerda por host en `ai.json` | Solo en el navegador: cualquier otro cliente se lo saltaría |
| Dónde se decide el propósito | En el core (`FieldPath`), el adaptador lee el texto de `purposes.json` | En el adaptador: el límite y el propósito dependen del modelo de datos, no del proveedor |
| Traducción | Ruta por ids (`projects[tomcat].summary`), escritura en el otro fichero con `ProfileEditingService` | Posiciones: dos ficheros ordenados distinto apuntarían a campos distintos |

## Hexagonal

- **Core** (`proofstack-core`, solo depende de Java):
  - `domain.assist`: `FieldPath` (ruta → propósito, límite de longitud, texto guardado), `AssistMode`, `AssistantBrief`, `Draft`, `Suggestion`, `UnsupportedFieldException`.
  - Puerto de entrada `TextAssistant`. Puerto de salida `TextDrafter`.
  - `TextAssistantService`: valida los límites, quita `phone` y `email` del contexto, llama al `TextDrafter`, recorta al límite del campo, limita cambios (5) y preguntas (3) y calcula `unsupportedNumbers` con `GroundingCheck`.
- **`proofstack-out-llm`**:
  - `OpenAiClient`, `AnthropicClient`, y `GeminiClient` y `OllamaClient` con temperatura y cabeceras.
  - `AiSettings` (con la clave enmascarada en `toString`), `AiSettingsStore` (lee, escribe y resuelve), `ai/presets.json`.
  - `AssistantPrompts` (recursos de `prompts/assistant/`, una sola pasada de sustitución y etiquetas del prompt desactivadas dentro de los datos), `LlmTextDrafter` (esquema y un reintento) y `LlmAssistants` (fábrica y prueba de conexión).
- **`proofstack-in-studio`**: `StudioAssistant` (la interfaz que necesita), `StudioTranslation` (el otro fichero), `AssistRoutes` (las rutas como funciones de JSON a JSON, sin socket) y `JsonFieldEditor` (escribe un texto en un documento por su ruta con ids). Frontend: `assist.js`.
- **`proofstack-cli`**: `AiStudioAssistant` implementa `StudioAssistant` con el almacén y el adaptador, y `StudioCommand` lo cablea junto con la traducción. Ningún adaptador conoce a otro: ArchUnit pasa sin excepciones.

## Rutas

| Ruta | Respuesta |
|---|---|
| `GET /api/ai/status` | `{configured, protocol, model, local, keySource}` |
| `GET /api/ai/config` | `{file, effective, envOverrides, presets, translation, problem?}`, sin clave |
| `PUT /api/ai/config` | Escribe `ai.json`; devuelve lo mismo que el `GET` y un aviso si la clave queda en el fichero |
| `POST /api/ai/test` | `{ok, model, millis, error}` |
| `POST /api/ai/consent` | Acepta enviar el perfil al host pendiente |
| `POST /api/ai/suggest` | `{text, changes, questions, unsupportedNumbers}` o un error con `code` |
| `POST /api/ai/apply-translation` | Escribe el texto en el mismo campo del otro fichero |

Códigos de `POST /api/ai/suggest`: 400 (`bad-field`, `bad-request`), 409 (`not-configured`, `consent-required`, `saved-invalid`), 429 (`busy`), 502 (`provider`, `invalid-output`). `mode` admite `improve`, `translate` y `job-offer` (la oferta pegada va en `request`).

## Interfaz

- `schema-form.js` llama a un gancho `assist` en cada campo de texto y lista de texto; `assist.js` decide si ese campo es uno de los suyos y añade el botón. Sin proveedor el gancho no hace nada.
- La ruta del formulario (`$.projects[0].summary`) se traduce a la ruta por ids al pulsar el botón. Los campos de lista añaden el índice de la línea elegida.
- El panel es un `<dialog>` modal: el foco entra en «¿Qué quieres?», pasa al título de la propuesta o al mensaje de error al terminar, y vuelve al botón que lo abrió. Los estados usan `role="status"` o `role="alert"`.
- Las diferencias se calculan por palabras en el navegador y se pintan con `<del>` e `<ins>` con `textContent`.

## Seguridad

- El prompt separa reglas (sistema) y datos (`<profile-data>`, `<current-text>`, `<request>`, `<job-offer>`). Una etiqueta del prompt dentro de un dato pierde su forma (`<\/profile-data>`).
- Los errores del proveedor pasan por `ProviderErrors`: en 401 y 403 no se muestra el cuerpo, y `HttpJson.redact` enmascara `Bearer …` y cadenas con forma de clave además de la clave exacta.
- `AiSettingsStore` rechaza esquemas distintos de `http` y `https`, usuario y contraseña en la URL, `http` a hosts no locales sin confirmación, y cabeceras con saltos de línea.

## Tests

`FieldPathTest`, `TextAssistantServiceTest`, `OpenAiClientTest`, `AnthropicClientTest`, `AiSettingsStoreTest`, `LlmTextDrafterTest` (incluye la inyección y la cadena completa con `GroundingCheck`), `JsonFieldEditorTest`, `StudioAssistTest` (guardia, límites, consentimiento, una petición a la vez, traducción), `AiStudioAssistantTest` (límites iguales a los del esquema) y los tests de navegador `StudioAssistantE2E`, `StudioNoAssistantE2E`, `StudioAiSettingsE2E` y `StudioAiConsentE2E`, con un proveedor falso que habla el protocolo `openai`.

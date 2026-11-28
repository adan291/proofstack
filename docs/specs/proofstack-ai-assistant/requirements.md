# Requirements: asistente de textos con IA en Studio (opcional y con cualquier proveedor)

## Introducción

Redactar el resumen, los logros o las frases por área cuesta más que rellenar el formulario. Studio puede ayudar con un asistente de IA que mejora y traduce los textos del perfil, sin atar al autor a un proveedor: APIs en la nube (OpenAI, Anthropic, Gemini, Mistral, Groq, OpenRouter, DeepSeek, Together, Azure OpenAI...) y modelos locales (Ollama, LM Studio, llama.cpp server, vLLM, LocalAI...). Sin proveedor configurado, Studio funciona como hasta ahora y no hay botones de IA en los campos.

Fuera de alcance: generar la web o el CV con IA (el CV sigue sin IA, ADR-006), guardar automáticamente, aplicar cambios a varios campos a la vez, un chat libre y leer los README de los repositorios (mejora futura).

## R1: Proveedores

1. El adaptador `proofstack-out-llm` SHALL hablar cuatro protocolos, cada uno en su clase `LlmClient`: `openai` (`POST {baseUrl}/chat/completions`), `anthropic` (`POST {baseUrl}/v1/messages` con `x-api-key` y `anthropic-version`), `gemini` y `ollama` (los clientes que ya existen).
2. El cliente `openai` SHALL pedir `response_format` con `json_schema`. IF el proveedor responde 400 THEN SHALL reintentar con `{"type":"json_object"}` y después sin `response_format`, y SHALL validar el JSON en el cliente en todos los casos.
3. Cada configuración SHALL tener `protocol`, `baseUrl`, `model`, una clave opcional, una cabecera de autenticación configurable (por defecto `Authorization: Bearer`; Azure usa `api-key`), cabeceras extra opcionales, `timeoutSeconds` y `temperature`.
4. Los presets (OpenAI, Anthropic, Gemini, Mistral, Groq, OpenRouter, DeepSeek, Azure OpenAI, Ollama, LM Studio y Personalizado) SHALL ser entradas de un recurso JSON que solo rellenan valores por defecto. Añadir un preset SHALL NOT requerir código.
5. «Probar conexión» SHALL hacer una petición mínima, sin datos del perfil, e informar del resultado y del modelo.

## R2: Configuración y secretos

1. La configuración SHALL vivir en `~/.proofstack/ai.json` (en Windows, `%USERPROFILE%\.proofstack\ai.json`), con permisos solo para el usuario cuando el sistema lo permita, y SHALL NOT estar en `profile.yaml`, en el repositorio ni en el navegador.
2. La clave SHALL poder darse por variable de entorno. `ai.json` SHALL guardar su **nombre** (`apiKeyEnv`). Guardar el valor SHALL ser posible, y Studio SHALL avisar de que es menos seguro.
3. `PROOFSTACK_AI_PROTOCOL`, `PROOFSTACK_AI_BASE_URL`, `PROOFSTACK_AI_MODEL` y `PROOFSTACK_AI_API_KEY` SHALL tener prioridad sobre el fichero, y el fichero sobre los presets.
4. La clave SHALL NOT llegar al navegador: `GET /api/ai/status` SHALL devolver solo `{configured, protocol, model, local, keySource}`. La clave SHALL NOT aparecer en logs, mensajes de error ni excepciones.
5. `GEMINI_API_KEY` y `OLLAMA_HOST` SHALL seguir valiendo, y el comando `generate` SHALL NOT cambiar.

## R3: Privacidad

1. Antes de la primera petición a un proveedor cuyo host no sea `127.0.0.1`, `localhost` ni `::1`, Studio SHALL mostrar, una vez por proveedor, «Tu perfil se enviará a {proveedor} para generar la propuesta». Sin aceptarlo SHALL NOT enviarse nada, y el servidor SHALL aplicarlo.
2. El contexto SHALL NOT incluir `person.phone` ni el email. El resto del perfil SHALL enviarse como datos.
3. Nada SHALL guardarse solo: la propuesta cambia el campo cuando el usuario pulsa Aceptar, y el fichero cuando pulsa Guardar.

## R4: Interfaz

1. IF hay proveedor THEN cada campo de texto del perfil (`summary`, `headline`, `problem`, `solution`, `learnings`, `highlights`, `areaNotes.*`, `contactMessage`, `context`, `action`, `result`, `description` de labs y `title` de evidencias) SHALL mostrar un botón «IA» con `aria-label="Mejorar con IA: {campo}"`. Sin proveedor el botón SHALL NOT existir en el DOM.
2. El botón SHALL abrir un panel con: «¿Qué quieres?» y atajos (Mejorar, Más corto, Más técnico, Para una oferta, Corregir faltas, Traducir); la propuesta junto al texto actual con las diferencias marcadas; «Cambios»; «Para hacerlo más fuerte»; un aviso visible si `GroundingCheck` detecta cifras que no están en el perfil; y los botones Aceptar, Editar antes de aceptar, Probar otra vez y Descartar.
3. «Traducir» SHALL escribir en el mismo campo del otro fichero (`profile.yaml` y `profile.en.yaml`), con la misma ruta por ids. IF el otro fichero no existe THEN el botón SHALL NOT aparecer.
4. Una pantalla «Ajustes de IA» SHALL permitir elegir preset, URL, modelo y origen de la clave, y probar la conexión.
5. Los estados de carga, error y cancelación SHALL ser claros, con el foco gestionado y `aria-live`. La auditoría axe SHALL seguir sin hallazgos serios.
6. Los textos de la interfaz SHALL estar en español.

## R5: Servidor

1. `StudioServer` SHALL ofrecer, detrás de `StudioGuard`: `GET /api/ai/status`, `GET|PUT /api/ai/config` (nunca devuelve la clave), `POST /api/ai/test` y `POST /api/ai/suggest` con `{path, request, currentText, mode, targetLanguage?}`. Además, `POST /api/ai/consent` y `POST /api/ai/apply-translation`.
2. El contexto SHALL construirse con el perfil **guardado en disco**, salvo `currentText`, que puede ser un borrador.
3. `currentText` SHALL tener como máximo 4.000 caracteres, `request` 2.000, y SHALL haber como mucho una petición a la IA a la vez.
4. La respuesta de la IA SHALL validarse contra un JSON Schema: si no lo cumple, un reintento y después un error comprensible. El texto propuesto SHALL recortarse a la longitud máxima del campo según el esquema del perfil.

## R6: Núcleo

1. El core SHALL ofrecer el puerto `TextAssistant` con `suggest(Profile, FieldPath, currentText, request, mode, targetLanguage)` y `Suggestion(text, changes, questions, unsupportedNumbers)`.
2. El core SHALL decidir el propósito de cada campo a partir de su ruta y quitar los datos privados. El adaptador SHALL solo hablar con el proveedor.
3. `unsupportedNumbers` SHALL salir de `GroundingCheck`, comparando con el perfil y con `currentText`.

## R7: Prompts

Los prompts SHALL ser recursos versionados en `proofstack-out-llm/src/main/resources/prompts/assistant/`: `system.md`, `task.md`, `purposes.json` (un propósito por tipo de campo) y `tasks.json` (`improve`, `translate`, `job-offer`).

## R8: Seguridad

1. Las peticiones salientes SHALL ir solo al `baseUrl` configurado, con `http` o `https`. `http` a un host que no sea local SHALL requerir confirmación expresa en Ajustes.
2. La respuesta del modelo SHALL tratarse como texto (`textContent`), nunca como HTML, y SHALL NOT usarse como ruta, URL ni comando.
3. Un perfil que contenga «ignore previous instructions» SHALL NOT cambiar las reglas, y el resultado SHALL pasar por el esquema y por `GroundingCheck`.

## R9: Tests

Un test por cliente contra un servidor HTTP falso (cuerpo, cabeceras, reintento sin `json_schema`, timeouts, 401, 429 y 5xx sin la clave); configuración (entorno > fichero > presets, `apiKeyEnv`, la clave nunca en `status` ni `config`); `StudioGuard` en `/api/ai/*`; core (propósito por ruta, contexto sin teléfono ni email, `unsupportedNumbers`); inyección; y tests de navegador con un proveedor falso (sin proveedor no hay botones, mejorar → Aceptar → Guardar, descartar, traducir, aviso de cifras, axe).

## R10: Documentación

ADR-017, una sección en el README y en la guía con ejemplos de configuración (OpenAI, Anthropic, Gemini, Ollama, LM Studio, OpenRouter y uno personalizado), y esta spec.

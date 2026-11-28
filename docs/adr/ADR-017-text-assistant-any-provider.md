# ADR-017: Asistente de textos con cualquier proveedor
- Estado: aceptada
- Fecha: 2026-11

## Contexto

Escribir bien el resumen, los logros o las frases por área cuesta más que rellenar el formulario. Studio ya valida y previsualiza, pero no ayuda a redactar. Una IA puede ayudar, con tres condiciones:

- Es opcional. Sin proveedor configurado, Studio es el de siempre.
- No ata al autor a un proveedor. Hay modelos en la nube y modelos en su equipo, y cambian cada pocos meses.
- Cumple la honestidad de contenido de [ADR-006](ADR-006-honest-content.md): no inventa datos, y el CV sigue sin IA.

[ADR-005](ADR-005-llm-adapter-without-spring-ai.md) ya fijó cómo se habla con un modelo (`LlmClient`, JDK `HttpClient`, Jackson, prompts versionados). Esta decisión añade protocolos, configuración y la parte de interfaz.

## Decisión

**Cuatro protocolos, no una lista de proveedores.** Cada uno es una clase `LlmClient` en `proofstack-out-llm`:

| Protocolo | Petición | Lo hablan |
|---|---|---|
| `openai` | `POST {baseUrl}/chat/completions` | OpenAI, Azure OpenAI, Mistral, Groq, OpenRouter, DeepSeek, Together, LM Studio, llama.cpp server, vLLM, LocalAI y Ollama en `/v1` |
| `anthropic` | `POST {baseUrl}/v1/messages` con `x-api-key` y `anthropic-version` | Anthropic |
| `gemini` | `generateContent` (el cliente que ya existía) | Gemini |
| `ollama` | `/api/chat` con `format` (el cliente que ya existía) | Ollama |

- **`openai` pide `json_schema` y baja solo si hace falta.** Si el proveedor responde 400 a `response_format`, reintenta con `{"type":"json_object"}` y después sin `response_format`. Recuerda el formato que funcionó, así solo la primera llamada de una sesión puede costar peticiones extra. En todos los casos el JSON se valida en el cliente.
- **`anthropic` recibe el esquema como instrucción** en el prompt de sistema. No depende de ninguna función en beta, y el resultado se valida igual.
- **Un proveedor es configuración, no código.** Los presets (OpenAI, Anthropic, Gemini, Mistral, Groq, OpenRouter, DeepSeek, Azure OpenAI, Ollama, LM Studio y Personalizado) son entradas de `ai/presets.json` que rellenan protocolo, URL, modelo, variable de la clave, cabecera de autenticación y cabeceras extra. Añadir uno es añadir una entrada.

**Dónde vive la configuración y cómo se guardan los secretos.**

- `~/.proofstack/ai.json` (en Windows, `%USERPROFILE%\.proofstack\ai.json`; con `PROOFSTACK_HOME`, la misma carpeta que usa LinkedIn). Fuera del repositorio, con permisos solo para el usuario cuando el sistema lo permite (modo POSIX 600, o una ACL solo del propietario en Windows). Nunca en `profile.yaml` ni en el navegador.
- Se guarda el **nombre** de la variable que contiene la clave (`"apiKeyEnv": "OPENAI_API_KEY"`), no la clave. Guardar el valor en `ai.json` se permite, y Studio avisa de que es menos seguro.
- Prioridad: variables `PROOFSTACK_AI_PROTOCOL`, `_BASE_URL`, `_MODEL` y `_API_KEY` > `ai.json` > valores del preset. Sirve para CI y para quien no quiere ficheros.
- `GEMINI_API_KEY` y `OLLAMA_HOST` siguen valiendo: son la variable de la clave y la URL por defecto cuando el protocolo es `gemini` u `ollama`. Por sí solas no activan la IA en Studio. El comando `generate` no cambia.
- La clave nunca llega al navegador. `GET /api/ai/status` devuelve solo `{configured, protocol, model, local, keySource}`; `GET /api/ai/config` devuelve la configuración sin clave. `AiSettings.toString()` la enmascara y los errores de los clientes la quitan. Las respuestas 401 y 403 no muestran el cuerpo del proveedor, porque algunos devuelven parte de la clave.

**Quién decide qué.**

- El **core** decide el propósito de cada campo a partir de su ruta (`FieldPath`), el límite de longitud, qué datos entran en el contexto y qué se comprueba de la respuesta. Puerto de entrada `TextAssistant`, servicio `TextAssistantService`, puerto de salida `TextDrafter`. El contexto es el perfil **guardado en disco** sin `person.phone` ni `person.email`; solo `currentText`, que puede ser un borrador, viene del navegador.
- El **adaptador** (`LlmTextDrafter`) solo habla con el proveedor: monta los prompts de `prompts/assistant/`, valida la respuesta contra un JSON Schema y reintenta una vez.
- `unsupportedNumbers` sale de `GroundingCheck`, comparando la propuesta con el perfil y con el texto actual.
- **Studio no conoce ningún proveedor.** Define la interfaz `StudioAssistant` y la implementa `AiStudioAssistant` en la CLI (la raíz de composición), como ya hace con `StudioImports`. ArchUnit sigue pasando sin excepciones.

**Privacidad.**

- Antes de la primera petición a un proveedor cuyo host no sea `127.0.0.1`, `localhost` ni `::1`, Studio muestra «Tu perfil se enviará a {proveedor} para generar la propuesta». El servidor responde 409 `consent-required` hasta que se acepta, y recuerda la aceptación por host en `ai.json` (`acceptedHosts`). El navegador no puede aceptar un host distinto del que está configurado.
- «Probar conexión» envía solo una palabra («ping»), nunca datos del perfil, y no necesita consentimiento.
- Nada se guarda solo. Aceptar escribe la propuesta en el campo del formulario, y el fichero solo cambia con *Guardar*. La excepción es traducir: *Guardar en profile.en.yaml* escribe en el otro fichero, con el mismo `ProfileEditingService` y sus mismas garantías (validación, escritura atómica, control de versión); el botón dice el nombre del fichero que va a escribir.

**Seguridad.**

- Las peticiones salen solo al `baseUrl` configurado, con `http` o `https`, sin usuario ni contraseña en la URL. `http` a un host que no sea esta máquina solo se admite con confirmación expresa en Ajustes (`allowInsecureHttp`).
- Lo que dice el modelo es texto. Se muestra con `textContent`, nunca como HTML, y no se usa como ruta, URL ni comando.
- Los datos van entre etiquetas (`<profile-data>`, `<current-text>`, `<request>`, `<job-offer>`) y cualquier etiqueta del prompt que aparezca dentro de ellos se desactiva, así que un perfil que diga «ignore previous instructions» no puede cerrar su bloque ni añadir una tarea. Las reglas del sistema dicen que todo eso son datos. Aun así la respuesta pasa por el esquema y por `GroundingCheck`, porque una regla en un prompt no es una garantía.
- Una sola petición a la IA a la vez. `currentText` hasta 4.000 caracteres y `request` hasta 2.000.

**Interfaz.** El botón «IA» aparece en los campos de texto del resumen, titular, proyectos, logros, frases por área, evidencias, mensaje de contacto y labs, solo si hay proveedor. El panel muestra la propuesta junto al texto actual con las diferencias marcadas, los cambios, las preguntas de la IA y el aviso de cifras. Los botones son Aceptar, Editar antes de aceptar, Probar otra vez y Descartar. Los campos de lista (aprendizajes, logros) trabajan sobre una línea, que se elige en el panel.

**Una desviación de la letra del encargo:** el botón *Ajustes de IA* de la barra existe siempre, también sin proveedor, porque es la forma de configurar uno desde Studio. Es el único elemento de IA visible sin proveedor.

## Alternativas descartadas

- **Un cliente por proveedor.** Seis o siete clases casi iguales que envejecen cada una a su ritmo. Con el protocolo `openai` basta una.
- **Spring AI o LangChain4j.** Mismo motivo que en [ADR-005](ADR-005-llm-adapter-without-spring-ai.md): cientos de dependencias para cuatro llamadas HTTP.
- **Guardar la configuración en `profile.yaml` o en el navegador.** El perfil se publica en Git y el navegador no es un lugar para secretos.
- **Enviar el perfil que tiene el navegador como contexto.** Un borrador con datos a medias da peores respuestas, y haría de la API un canal para enviar contenido arbitrario al proveedor. El contexto sale del disco.
- **Consentimiento solo en el navegador.** Un cliente que no sea Studio, o un bug, se lo saltaría. Lo aplica el servidor.
- **Reescribir el CV con IA.** Sigue sin IA a propósito ([ADR-006](ADR-006-honest-content.md)): es el documento que más pesa y el que menos admite un dato inventado.

## Consecuencias

- Studio puede enviar texto del perfil a un tercero. Por eso el consentimiento, la exclusión de teléfono y email, y que la IA sea opcional y esté apagada por defecto.
- El resto del perfil (nombre, ubicación, enlaces, experiencia, proyectos) sí se envía. Quien no quiera, usa un modelo local.
- La protección contra inyección reduce el riesgo y no lo elimina. Lo que sí garantizan el esquema, `GroundingCheck` y *Aceptar* es que un resultado malo no cambia nada sin que el autor lo vea.
- `GroundingCheck` detecta números inventados, no afirmaciones inventadas sin cifras («lideré el equipo»). El autor sigue siendo quien revisa.
- Los mensajes de error de los proveedores llegan en el idioma del proveedor, dentro de una frase en español.
- Cancelar una petición libera la interfaz al instante, pero el servidor no interrumpe la llamada en curso: hasta que termine o venza su tiempo de espera, otra petición recibe «ya hay una en curso».
- Si un proveedor cambia su API, se corrige en una clase y un test. Si solo cambian el modelo o la URL, se corrige un preset.

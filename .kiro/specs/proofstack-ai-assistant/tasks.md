# Tasks: asistente de textos con IA en Studio

- [x] 1. Core: `FieldPath` (propósito, límite y texto guardado por ruta con ids), `TextAssistant`, `TextDrafter` y `TextAssistantService` (contexto sin teléfono ni email, recorte, `unsupportedNumbers`), con tests (R6)
- [x] 2. `OpenAiClient` con reintento `json_schema` → `json_object` → nada, y `AnthropicClient`, con tests contra un servidor HTTP falso (cuerpo, cabeceras, 401, 429, 5xx, timeout, sin la clave en los mensajes) (R1.1–R1.2, R9)
- [x] 3. `AiSettings`, `AiSettingsStore` y `ai/presets.json`: fichero, entorno, presets, `apiKeyEnv`, permisos y enmascarado, con tests (R1.3–R1.4, R2)
- [x] 4. Prompts de `prompts/assistant/`, `AssistantPrompts` y `LlmTextDrafter`, con tests de inyección y de esquema (R5.4, R7, R8.3)
- [x] 5. `LlmAssistants`: fábrica y «Probar conexión» sin datos del perfil (R1.5)
- [x] 6. Studio: `StudioAssistant`, `AssistRoutes`, rutas `/api/ai/*`, consentimiento, una petición a la vez, límites y traducción con `JsonFieldEditor`, con tests de `StudioGuard` y de servidor (R3.1, R5, R8)
- [x] 7. Composición en la CLI: `AiStudioAssistant`, `StudioTranslation` y `StudioCommand`, con un test de que los límites del core son los del esquema (R2.5, R6)
- [x] 8. Frontend: botón «IA», panel con diferencias, cambios, preguntas y aviso de cifras, traducción, consentimiento, cancelación y «Ajustes de IA» (R4)
- [x] 9. Tests de navegador con un proveedor falso: sin proveedor no hay botones, mejorar → Aceptar → Guardar, descartar, traducir, cifras, consentimiento, ajustes y axe (R9)
- [x] 10. ADR-017, README, guía y esta spec (R10)

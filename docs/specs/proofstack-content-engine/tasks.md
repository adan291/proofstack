# Tasks — Proofstack Fase 2

- [x] 1. Core de contenido
  - [x] 1.1 `ContentFormat`, `ContentSubject`, `ContentBrief`, `GeneratedContent`, `ContentGenerationException`
  - [x] 1.2 `GroundingCheck` con tests
  - [x] 1.3 Puertos y `ContentService` con tests (generador fake)
  - _Requisitos: R1.1–R1.4, R3.3, R3.4_

- [x] 2. Adaptador `out-llm`
  - [x] 2.1 `PromptLibrary` + prompts por formato + `content.schema.json`
  - [x] 2.2 `GeminiClient`, `OllamaClient`, `LlmProviders`
  - [x] 2.3 `LlmContentGenerator` con validación de salida
  - [x] 2.4 Tests con servidor HTTP fake
  - _Requisitos: R2, R3.1, R3.2, R3.5_

- [x] 3. Importador LinkedIn + writer YAML
  - [x] 3.1 `CsvParser`, `LinkedInDates`, `SkillAreaClassifier`
  - [x] 3.2 `LinkedInExportImporter` con límites ZIP
  - [x] 3.3 `YamlProfileWriter` + test de ida y vuelta
  - [x] 3.4 `ImportService`
  - _Requisitos: R4_

- [x] 4. CLI y documentación
  - [x] 4.1 Comandos `generate` e `import linkedin`
  - [x] 4.2 ArchUnit actualizado para los módulos nuevos
  - [x] 4.3 ADR-005, ADR-006, README
  - _Requisitos: R1.5, R2.5, R4.7_

- [x] 5. Prueba manual con proveedor real: Ollama `llama3.1`, post de LinkedIn de `tomcat-jakarta-migration` generado en ~9 s
- [ ] 6. Prueba manual con Gemini (requiere `GEMINI_API_KEY`)
- [ ] 7. Prueba con un export real de LinkedIn (requiere tu ZIP)

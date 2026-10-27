# ADR-001 — Arquitectura hexagonal

- Estado: aceptada
- Fecha: 2026-10

## Contexto

Proofstack tiene que leer perfiles de varias fuentes (YAML hoy; export de LinkedIn y GitHub mañana) y producir varias salidas (web, CV en PDF, README, posts, guiones). Más adelante se añadirán proveedores de IA intercambiables (Gemini, OpenAI, Ollama).

## Decisión

- `proofstack-core` contiene el dominio, los casos de uso y los puertos, y es Java puro: solo depende de `java.*`.
- Cada fuente y cada salida es un adaptador en su propio módulo Maven.
- La CLI es la raíz de composición.
- Las reglas se hacen cumplir con ArchUnit:
  - `CoreArchitectureTest`: el core solo depende de Java.
  - `HexagonalArchitectureTest`: capas, adaptadores aislados entre sí y sin acceso a `core.application`.

## Consecuencias

- Añadir un renderer o una fuente nueva no toca el dominio. Por ejemplo, el renderer Astro de ADR-004.
- Hay más módulos que en un proyecto plano. Para un CRUD sería sobreingeniería; aquí se justifica por el número de adaptadores previstos.
- Si un cambio necesita romper una regla de ArchUnit, eso es una señal para revisar el diseño, no para borrar el test.

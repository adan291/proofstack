# ADR-016: Un adaptador por formato de persistencia

- Estado: aceptada
- Fecha: 2026-11
- Matiza: ADR-001
- Relacionada con: ADR-014

## Contexto

ADR-001 dice: «Cada fuente y cada salida es un adaptador en su propio módulo Maven». Para `profile.yaml` eso dio dos módulos:

- `proofstack-in-yaml`, que lee y valida. Desde ADR-014 también escribe, con `YamlProfileDocumentStore` para Studio.
- `proofstack-out-yaml`, que escribe (`YamlProfileWriter`, para `init` y los importadores).

Como ArchUnit no deja que un adaptador use otro, cada módulo tenía su propio `YAMLMapper`. Ya divergieron una vez: el fallo de comillas de H1 hubo que corregirlo en los dos, y `TextQuoting` acabó copiado en ambos.

La regla de ADR-001 busca que añadir una fuente o una salida no toque las demás. Aquí no hay dos piezas independientes: hay un formato con dos direcciones que deben producir exactamente los mismos bytes.

## Decisión

- Un formato que se lee y se escribe es **un solo adaptador**: `adapters/proofstack-yaml`, paquete `dev.proofstack.adapter.yaml`. Sustituye a `proofstack-in-yaml` y `proofstack-out-yaml`.
- Dentro, `YamlCodec` es el único sitio que configura YAML. Contiene el mapper, las comillas (`TextQuoting`), la comprobación de que lo escrito se relee igual y la escritura atómica.
- ArchUnit trata `adapter.<formato>` como un adaptador más: no depende de los demás ni ellos de él (`persistenceAdaptersAreIndependentOfTheOthers`).
- El resto de ADR-001 no cambia. Las fuentes y salidas de una sola dirección (LinkedIn, GitHub, web, CV, LLM, Studio) siguen siendo un módulo cada una, en `adapter.in.*` o `adapter.out.*`.

## Consecuencias

- Cambiar el formato del fichero es un cambio en un solo sitio. Un test de caracterización (`YamlBytesCharacterizationTest`) fija los bytes que producen los dos escritores.
- El cambio no altera el contenido de ningún fichero. Los dos escritores dan los mismos bytes que antes para los perfiles de ejemplo, los de los tests y `profiles/adan`.
- El artefacto `proofstack-in-yaml` desaparece. Quien lo usara como dependencia debe cambiar a `proofstack-yaml`; dentro del repositorio ya están cambiados la CLI, el servicio RAG y el servidor MCP.
- Las specs antiguas (`proofstack-mvp`, `proofstack-content-engine`, `proofstack-studio`) siguen nombrando los módulos de entonces. Son registro histórico y no se reescriben.

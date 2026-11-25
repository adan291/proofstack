# Design: un solo codec YAML

## Opciones

| Opción | Qué supone | Valoración |
|---|---|---|
| A. Un módulo `adapters/proofstack-yaml` (paquete `dev.proofstack.adapter.yaml`) con lectura, Studio y escritura | Desaparecen `proofstack-in-yaml` y `proofstack-out-yaml`. Hay que cambiar 6 pom, 11 ficheros Java y 7 documentos | **Elegida.** El fichero es uno solo, en los dos sentidos: separar «entrada» y «salida» del mismo formato es lo que provocó la duplicación |
| B. Mover `YamlProfileWriter` a `proofstack-in-yaml` | Cambio pequeño, pero el paquete `adapter.in.yaml` contendría un adaptador de salida | Descartada: el nombre mentiría. Ya pasa en parte con `YamlProfileDocumentStore` (ADR-014) |
| C. Librería compartida `proofstack-yaml-codec`, fuera de `adapter..`, usada por los dos módulos | Tres módulos para un formato. ArchUnit necesitaría una regla nueva para que el codec no conozca el dominio | Descartada: añade un módulo en vez de quitar uno |

## ADR afectados

- **ADR-001** dice: «Cada fuente y cada salida es un adaptador en su propio módulo Maven». La opción A lo contradice para YAML. Por qué ya no vale en este caso: la regla buscaba que añadir una fuente o una salida no tocara las demás, y aquí no hay dos piezas independientes. Hay un formato con dos direcciones que deben coincidir byte a byte (R2.1), y tenerlas separadas dio dos copias que ya divergieron una vez (H1). Se añadirá un ADR nuevo (ADR-016, «Un adaptador por formato de persistencia») que matiza ADR-001 para formatos de lectura y escritura. El resto de ADR-001 no cambia.
- **ADR-014** dice que `YamlProfileDocumentStore` vive en `proofstack-in-yaml`. Pasa a `proofstack-yaml`; la decisión de fondo (guardar desde el árbol del documento) no cambia.

## Estructura de `proofstack-yaml`

- `YamlCodec` (paquete privado): el único `YAMLMapper` de lectura y el único de escritura, `TextQuoting`, `header()` y `requireSameWhenReadBack()`.
- `YamlProfileSource`, `DocumentMapper` y `ProfileDocument`: sin cambios de comportamiento.
- `YamlProfileDocumentStore` y `YamlProfileWriter`: usan `YamlCodec`. La escritura atómica (temporal y renombrado) también se comparte.
- ArchUnit: la regla de slices `adapter.(*).(*)..` pasa a `adapter.(*)..` para cubrir `adapter.yaml`. Se mantienen las reglas in/out para el resto.

## Migración

1. Test de caracterización antes de mover nada: escribir con los dos writers todos los perfiles de `profiles/` y de los fixtures, y guardar los bytes (R2.1).
2. Crear el módulo, mover las clases con `git mv` para conservar el historial y ajustar paquetes.
3. Unificar el codec y repetir el test de caracterización.
4. Actualizar los pom de CLI, servicio RAG y MCP, `docker.yml`, README, guía y specs. Las specs antiguas se dejan como registro histórico, con una nota.

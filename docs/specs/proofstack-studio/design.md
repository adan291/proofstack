# Design: Proofstack Fase 7 (Studio)

## Decisiones

| Tema | Decisión | Alternativa descartada y motivo |
|---|---|---|
| Servidor HTTP | `com.sun.net.httpserver` del JDK, como el callback OAuth de LinkedIn | Spring Boot: arranque lento y decenas de dependencias para una herramienta local de un solo usuario |
| Frontend | HTML, CSS y JavaScript sin paso de build, servidos desde el jar | React de LK-main: obliga a añadir Node y un build al repo Java, y su modelo `CVData` no coincide con el perfil |
| Formularios | Generados desde el JSON Schema | Formularios escritos a mano: cada campo nuevo exigiría cambiar la interfaz (R3.2) |
| Formato en el cable | JSON con la misma forma que el YAML | Un DTO propio: el esquema ya describe el formato y valida |
| DOCX | Office Open XML escrito a mano en `proofstack-out-cv` | La librería `docx` de FolioMark (JavaScript) o Apache POI (unos 20 MB): para un documento de texto con títulos y listas no hacen falta |

## Hexagonal

- **Nuevo adaptador de entrada `adapters/proofstack-in-studio`** (driving): traduce HTTP a casos de uso. No conoce ningún adaptador de salida; recibe los puertos ya cableados.
- **Core** (solo depende de Java, lo exige `CoreArchitectureTest`). El documento viaja como **texto JSON**, nunca como un tipo de Jackson.
  - Puerto in `EditProfileUseCase`: `Snapshot load()` → `Snapshot(String json, String version, List<Finding> findings)`, y `SaveResult save(String json, String expectedVersion)` → `Saved(version, findings)` | `Invalid(violations)` | `Conflict(currentVersion)`. `version` es el SHA-256 del fichero en disco.
  - Puerto out `ProfileDocumentStore`: `read()`, `parse(json)` (esquema y reglas de dominio, sin tocar disco) y `replace(json, expectedVersion)` (escritura atómica con copia `.bak`, o `VersionConflict`).
  - `ProfileEditingService` implementa el caso de uso con ese puerto y `PublishReadiness`.
- **`YamlProfileDocumentStore`** en `proofstack-in-yaml`, que ya tiene Jackson y el esquema. Guarda a partir del árbol del documento recibido, no reconstruyéndolo desde el dominio, así que respeta el orden de claves del usuario y conserva los comentarios de la cabecera del fichero (el resto de comentarios se pierde, como con `import`).
- La vista previa usa `PortfolioRenderer` (web y CV) sobre el perfil guardado, en memoria: nunca escribe en disco.
- Composición en la CLI (`StudioCommand`), como el resto de comandos. ArchUnit añade el paquete `adapter.in.studio` a las reglas existentes.

## Servidor (`StudioServer`)

- Rutas: `GET /` (index), `GET /assets/*` (estáticos del classpath, lista blanca de nombres), `GET /api/schema`, `GET|PUT /api/profile`, `GET /preview/cv`, `GET /preview/site`.
- Filtros, en este orden: `Host` permitido → límite de tamaño → token en `/api/**` (comparación en tiempo constante) → `Origin` en métodos de escritura.
- Errores en JSON `{error, violations?}`. Nunca se devuelven trazas ni rutas absolutas.
- Puerto por defecto `0` (aleatorio) para no chocar con otros servicios; se imprime la URL.

## Frontend (`resources/studio/`)

- `index.html`, `studio.css`, `studio.js` (módulo ES). Sin dependencias de terceros.
- `schema-form.js` recorre el esquema (`object`, `array`, `string` con `pattern`/`enum`, `boolean`, `$ref` a `$defs`) y crea controles con `<label>`, `aria-describedby` y mensajes de error.
- Guardar envía `PUT` con `If-Match: <version>`. Si el servidor responde 409, la interfaz ofrece recargar.
- La vista previa es un `<iframe sandbox>` que apunta a `/preview/*`, con recarga tras cada guardado.

## DOCX (`CvDocx`)

- Parte del mismo modelo `Cv` que `CvMarkdown`.
- Escribe `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/styles.xml` y `word/numbering.xml` en un ZIP.
- Estilos `Title`, `Heading1`, `Heading2` y lista con viñetas. Texto escapado para XML.
- `Artifact` pasa a admitir contenido binario (`byte[]`) además de texto, sin romper los adaptadores actuales.

## Pruebas

- Core: `ProfileEditingService` con fakes (válido, inválido, conflicto, copia previa).
- Servidor: arranque en puerto 0 y peticiones reales con `HttpClient` (token ausente o erróneo, `Host` ajeno, `Origin` ajeno, cuerpo grande, 422, 409, guardado correcto).
- DOCX: abrir el ZIP y comprobar estilos, orden de secciones, escape de XML y ausencia de `TODO`.
- Frontend: test de humo que carga `index.html` y comprueba que no hay recursos externos.

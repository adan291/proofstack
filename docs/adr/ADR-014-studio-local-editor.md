# ADR-014: Studio, un editor local sin dependencias de frontend

- Estado: aceptada
- Fecha: 2026-11

## Contexto

Editar `profile.yaml` a mano exige conocer el esquema y es fácil romper la indentación. Queremos un editor visual sin renunciar a que el YAML versionado en Git sea la fuente de verdad (ADR-002), y sin convertir Proofstack en un servicio con cuentas de usuario.

## Decisión

- **`proofstack studio <profile>`** sirve un editor web en `127.0.0.1` con el `HttpServer` del JDK, el mismo que ya usa el callback OAuth de LinkedIn. Es un adaptador de entrada nuevo (`proofstack-in-studio`) que solo habla con el core a través de `EditProfileUseCase`.
- **El core no conoce JSON como tipo.** El documento viaja como texto. `ProfileDocumentStore` (puerto out) lo valida con el esquema y las reglas de dominio, y lo escribe. La implementación vive en `proofstack-in-yaml`, que ya tiene Jackson y el esquema.
- **Nunca se escribe un perfil inválido.** Se valida antes de guardar. La escritura es atómica (temporal y renombrado) y deja `profile.yaml.bak`. La versión es el SHA-256 del fichero, y un guardado con una versión antigua responde 409 en vez de pisar cambios hechos en otro editor o con Git.
- **El YAML se guarda desde el árbol del documento**, no reconstruido desde el dominio: se respeta el orden de claves y se conservan los comentarios de cabecera.
- **Frontend sin build ni dependencias** (HTML, CSS y un módulo ES). Los formularios se generan desde el JSON Schema, de modo que un campo nuevo del esquema aparece sin tocar la interfaz.
- **Seguridad de un servidor local**, porque cualquier web que abra el usuario puede intentar hablarle:
  - `Host` permitido solo si es `127.0.0.1` o `localhost` con el puerto propio (DNS rebinding).
  - Token de 256 bits en una cabecera para toda la API. Llega al navegador en el fragmento de la URL, que no se envía a ningún servidor ni queda en logs.
  - Escrituras solo con el `Origin` propio y cuerpo `application/json` (CSRF).
  - Cuerpo limitado a 1 MiB, ficheros servidos desde una lista cerrada, CSP estricta y errores sin trazas ni rutas.
- **Vista previa** del perfil guardado con los mismos renderers que `build` y `cv`, en memoria, dentro de un `iframe` aislado (`sandbox`).

## Alternativas descartadas

- **Spring Boot:** decenas de dependencias y arranque lento para una herramienta de un solo usuario.
- **Reutilizar LK-main (React):** obligaría a añadir Node y un paso de build al repo Java, y su modelo `CVData` no coincide con el perfil.
- **Editar el dominio y reescribir el YAML con `YamlProfileWriter`:** perdería el orden y los comentarios del usuario.

## Consecuencias

- Los comentarios dentro del YAML (no los de cabecera) se pierden al guardar desde Studio. Se avisa en el README.
- La vista previa se sirve con `Cross-Origin-Resource-Policy: cross-origin`, porque el `iframe` aislado tiene un origen opaco. Solo contiene el portfolio público; la API y el editor siguen en `same-origin`.
- Studio no se despliega en internet. Si algún día se quisiera, haría falta autenticación de verdad, no un token en la URL.

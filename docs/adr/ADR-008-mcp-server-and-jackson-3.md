# ADR-008: Servidor MCP local de solo lectura y migración a Jackson 3

- Estado: aceptada
- Fecha: 2026-10

## Contexto

- Queremos que un asistente (Kiro u otro cliente MCP) pueda consultar el portfolio y redactar a partir de datos reales sin copiar y pegar.
- Al añadir `spring-ai-starter-mcp-server` 2.0.1 apareció un conflicto. El SDK de MCP 2.0.0 necesita `json-schema-validator` 3.0.0, basado en Jackson 3. El proyecto fijaba la 1.5.6, basada en Jackson 2. Con dos versiones en el classpath, el servidor fallaba al arrancar con `NoClassDefFoundError: com/networknt/schema/dialect/Dialects`.

## Decisión

1. **Servidor MCP por stdio** (`proofstack-mcp-server`):
   - El servidor arranca en la máquina del desarrollador y no abre ningún puerto.
   - Ofrece cinco herramientas de solo lectura: `get_profile_summary`, `list_projects`, `get_project`, `search_portfolio` y `get_content_facts`.
   - Todas declaran `readOnlyHint=true` y `destructiveHint=false`.
   - ArchUnit impide que el adaptador `adapter.in.mcp` use `java.io`, `java.nio.file`, `java.net` o los puertos de salida, así que el solo lectura está garantizado por construcción, no por disciplina.
   - `search_portfolio` usa `KeywordSearch` del core: es léxica y no necesita LLM ni embeddings.
   - `get_content_facts` devuelve los hechos junto con las reglas de honestidad de ADR-006. El texto lo escribe el LLM del asistente.
   - stdout queda reservado para JSON-RPC:
     - el perfil se valida antes de arrancar Spring y los errores van a stderr;
     - el banner está desactivado;
     - los logs van a stderr (`logback-spring.xml`);
     - no se expone el email.
2. **Migración de todo el proyecto a Jackson 3 (3.1.5) y networknt 3.0.0.** Así hay una sola versión de cada librería en el classpath, alineada con Spring Boot 4.1 y Spring AI 2.0. Descarté hacer shading de la 1.5.6 porque solo aplazaba el problema.
   - Cambios de API:
     - paquetes `tools.jackson.*` (las anotaciones siguen en `com.fasterxml.jackson.annotation`);
     - `JacksonException` no comprobada;
     - `TokenStreamLocation`;
     - `asString()`;
     - `YAMLWriteFeature`;
     - en networknt, `SchemaRegistry`, `Schema` y `Error`.
   - Para mantener las rutas de error `$.projects[0].id` se usa `PathType.LEGACY`.

## Consecuencias

- Todos los módulos comparten una sola versión de Jackson.
- Los tests existentes, incluido el de ida y vuelta YAML → dominio → YAML, siguieron pasando tras la migración. Eso confirma que el contrato público (`profile.schema.json` y los mensajes de error) no ha cambiado.
- El servidor MCP solo lee. Si algún día escribe (por ejemplo, "añade este proyecto"), irá a otro adaptador con confirmación explícita.

# ADR-011: Proofstack como plantilla pública, con un control de publicación

- Estado: aceptada
- Fecha: 2026-10

## Contexto

El objetivo de producto (V2 del roadmap) es que cualquier desarrollador backend pueda tener su portfolio sin escribir código. El riesgo principal no es técnico sino de credibilidad: publicar un portfolio con `TODO`, con `jane.doe@example.com` o con un proyecto que solo enumera tecnologías contradice el lema "pruebas, no promesas".

## Decisión

- **Repositorio plantilla** ("Use this template") en lugar de un SaaS. No hay cuentas, base de datos ni costes. El usuario es dueño de sus datos en su propio Git (ADR-002).
- **`proofstack init`** genera un perfil inicial que pasa la validación y deja marcado con `TODO` todo lo que falta. La primera línea es un modeline relativo hacia `schema/profile.schema.json`, para tener autocompletado en VS Code o Kiro.
- **`proofstack doctor`** distingue dos cosas: que el perfil sea **válido** (`validate`, que comprueba esquema e invariantes) y que sea **publicable** (`PublishReadiness`, en el core).
  - **ERROR:** placeholders, URLs de ejemplo o genéricas, email de ejemplo.
  - **WARNING:** proyectos sin problema/solución o sin enlace, evidencias sueltas, perfil sin resumen, sin experiencia o sin skills.
  - Con `--strict`, los warnings también hacen fallar el comando.
- **El workflow de Pages ejecuta `doctor` antes de construir.** Si hay errores, no se despliega. El perfil a publicar se elige con la variable `PROOFSTACK_PROFILE`, sin tocar el YAML del workflow.
- **Releases:** al crear un tag `v*` se publican `proofstack.jar`, `proofstack-mcp-server.jar` y un fichero `SHA256SUMS`. Así quien use la plantilla no necesita Maven.

## Consecuencias

- El ejemplo incluido (`profiles/example`) **no** pasa `doctor`, a propósito, porque usa un email de ejemplo. Un fork no puede publicar a "Jane Doe" por accidente.
- El perfil de Adán tampoco se publica hasta que complete sus `TODO`. En el momento de escribir esto `doctor` reporta 9 errores y 5 avisos.
- `PublishReadiness` es heurístico: detecta lo evidente, no si el contenido es bueno. La revisión humana sigue siendo necesaria.

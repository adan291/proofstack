# Requirements: Proofstack Fase 4 (servidor MCP e importador de GitHub)

## Introducción
- **Servidor MCP local (stdio):** expone el perfil a asistentes como Kiro para que puedan consultarlo y redactar a partir de datos reales.
- **Importador de GitHub:** convierte los repositorios públicos en borradores de proyectos.

Fuera de alcance: herramientas MCP de escritura, transporte HTTP remoto para MCP y repositorios privados.

## R1: Servidor MCP
1. El servidor SHALL usar transporte stdio y leer el perfil de `PROOFSTACK_PROFILE`.
2. IF el perfil no es válido THEN el servidor SHALL no arrancar y SHALL escribir el motivo en stderr.
3. Todas las herramientas SHALL ser de solo lectura y SHALL declararlo con `readOnlyHint=true` y `destructiveHint=false`.
4. Herramientas:
   - `get_profile_summary`: persona y áreas con conteos.
   - `list_projects`: id, nombre, resumen y áreas de cada proyecto.
   - `get_project(id)`: detalle del proyecto y sus evidencias.
   - `search_portfolio(query, limit)`: búsqueda por palabras clave que devuelve fragmentos con id. No requiere LLM.
   - `get_content_facts(subjectId)`: hechos de un proyecto o evidencia más las reglas de honestidad, para que el asistente redacte sin inventar.
5. IF un id no existe THEN la herramienta SHALL devolver un error con los ids disponibles.
6. stdout SHALL contener solo mensajes del protocolo. El banner y los logs SHALL ir a stderr o desactivarse.
7. El servidor SHALL NOT exponer el email.

## R2: Importador de GitHub
1. `proofstack import github <usuario> --profile <yaml> --out <yaml>` SHALL añadir como proyectos los repositorios públicos del usuario que todavía no estén en el perfil.
2. Por defecto SHALL excluir forks y repositorios archivados. `--include-forks` los incluye.
3. Un repositorio ya existe en el perfil IF coincide su id (slug del nombre) OR su URL ya aparece en los enlaces de un proyecto.
4. Mapeo de cada repositorio:
   - descripción → `summary` (o `TODO` si no tiene);
   - lenguaje y topics → `techStack`;
   - lenguaje y topics → áreas, mediante `AreaClassifier`;
   - `html_url` → enlace;
   - `problem` y `solution` → `TODO`.
5. El token SHALL leerse solo de `GITHUB_TOKEN` (opcional) y SHALL NOT aparecer en mensajes de error.
6. SHALL seguir la paginación (`Link: rel="next"`), con un máximo de 5 páginas de 100 repositorios.
7. Errores:
   - 404: "usuario no encontrado";
   - 403/429: límite de peticiones (sugerir `GITHUB_TOKEN`);
   - fallo de red: error de entorno (código 2).
8. IF `--out` existe THEN el sistema SHALL NOT sobrescribirlo salvo con `--force`. El perfil original nunca se modifica.

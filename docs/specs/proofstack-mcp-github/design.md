# Design: Proofstack Fase 4

## Core
- `domain.AreaClassifier`: clasificador por palabras clave. Se mueve aquí desde `in-linkedin` porque es lógica de dominio que comparten varios importadores.
- `domain.knowledge.KeywordSearch`:
  - normaliza el texto (quita acentos, pasa a minúsculas y descarta tokens de menos de 3 caracteres);
  - puntúa con el solape `|q ∩ c| / |q|`, más un bonus si el término aparece en el título.
- Puerto out `ProjectSource.fetchProjects(account, includeForks)`, que devuelve `List<Project>`.
- Puerto in `MergeProjectsUseCase.merge(profile, account, includeForks, target, overwrite)`, que devuelve `MergeReport(added, skipped)`.
- `ProjectMergeService` en `core.application`.

## Adaptador `adapters/proofstack-in-github`
- `GitHubProjectSource` usa JDK HttpClient y Jackson 2:
  - llama a `GET {base}/users/{user}/repos?type=owner&sort=pushed&per_page=100`;
  - envía las cabeceras `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28` y `User-Agent: proofstack`;
  - si hay token, añade `Authorization: Bearer`.
- La base de la API es configurable con `PROOFSTACK_GITHUB_API`, que usan los tests con el servidor fake.
- El nombre de usuario se valida con `^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$` para evitar inyección en el path.
- Los errores se reportan como `ImportException(kind NOT_FOUND | RATE_LIMITED | UNAVAILABLE)`, una excepción de dominio en `core.port.out`.

## Módulo `proofstack-mcp-server`
- Spring Boot sin web con `spring-ai-starter-mcp-server` (stdio).
- Paquetes:
  - `dev.proofstack.service.mcp`: raíz de composición.
  - `dev.proofstack.adapter.in.mcp`: la clase `PortfolioMcpTools`, con métodos `@McpTool` que solo usan el core.
- Configuración:
  - `spring.main.banner-mode=off`, `web-application-type=none`;
  - logs a fichero o desactivados en consola;
  - `spring.ai.mcp.server.stdio=true`, con nombre y versión;
  - `instructions` le explica al asistente que no debe inventar.
- Tests:
  - unitarios de las tools, sobre `profiles/example`;
  - ArchUnit;
  - prueba stdio real: `initialize`, `tools/list` y `tools/call` contra el JAR.

## CLI
`import github` como subcomando de `import`. La raíz de composición construye `ProjectMergeService` con `YamlProfileSource`, `GitHubProjectSource` y `YamlProfileWriter`.

# Design: Proofstack Fase 5

## Observabilidad (proofstack-ai-service)
- `spring-boot-starter-actuator` y `micrometer-registry-prometheus`.
- `management.server.port=8081` y `management.endpoints.web.exposure.include=health,prometheus`.
- `dev.proofstack.service.ObservedAskPortfolio` decora `AskPortfolioUseCase` en la raíz de composición. Así el core y el adaptador REST no conocen Micrometer.
- `RateLimitFilter` recibe un `Runnable onRejected`, que la configuración conecta con un contador. El adaptador REST sigue sin depender de Micrometer.
- `KnowledgeIndexHealthIndicator` (en `service`) está UP si el índice tiene fragmentos y DOWN en caso contrario.
- Readiness group: `readinessState` + `knowledgeIndex`.
- Perfil `prod`: `logging.structured.format.console=ecs`, `server.forward-headers-strategy=framework` (solo detrás de Caddy) y `PROOFSTACK_ALLOWED_ORIGINS` obligatorio.

## Contenedores (`deploy/`)
- `proofstack-ai-service/Dockerfile`:
  - extrae las capas del JAR con `java -Djarmode=tools -jar app.jar extract --layers --launcher`;
  - imagen final `temurin:25-jre`;
  - usuario `10001`;
  - `HEALTHCHECK` con `curl` contra `/actuator/health` en el puerto 8081. La imagen jre de Temurin no trae curl, así que se usa un `HealthProbe.java` mínimo (single-file source launch).
  - el perfil se monta como volumen de solo lectura.
- `deploy/compose.yaml`:
  - `ollama`;
  - `ollama-init`, que hace pull de `llama3.1` y `nomic-embed-text` y termina;
  - `ai-service`, que depende de `ollama-init` completado;
  - `prometheus` (scrape `ai-service:8081`);
  - `grafana`, con datasource y dashboard aprovisionados.
- `deploy/compose.prod.yaml`: `caddy` (puertos 80/443, `Caddyfile` con `{$PROOFSTACK_DOMAIN}`). Prometheus y Grafana solo quedan accesibles en localhost.
- `.github/workflows/docker.yml`: build con buildx. Solo hace push a `ghcr.io/<owner>/proofstack-ai-service` en tags, con permiso `packages: write`.

## Valhalla lab
- `labs/valhalla-lab` con `pom.xml` propio. El padre lo incluye en el perfil `valhalla` (`<modules>`).
- `maven.compiler.release=28`, `--enable-preview` en el compilador y en el fork de JMH.
- `PointRecord(int x, int y)` frente a `value record PointValue(int x, int y)`.
- Benchmarks:
  - `sumArray` (array de 1M, lee los campos);
  - `allocateAndSum` (crea N objetos en un bucle y acumula).
- Opciones de JMH: `-f 1 -wi 3 -i 5` y `-prof gc` para ver asignación por operación.

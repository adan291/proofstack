# Requirements: Proofstack Fase 5 (operación del servicio RAG y laboratorio de Valhalla)

## Introducción
Preparar `proofstack-ai-service` para producción y medir Project Valhalla (JEP 401) con un benchmark reproducible. Fuera de alcance: desplegarlo en una cuenta real, porque requiere tus credenciales.

## R1: Observabilidad
1. Actuator SHALL exponer solo `health` y `prometheus`, y SHALL hacerlo en un puerto de gestión separado (8081) que no se publica.
2. El sistema SHALL registrar estas métricas de dominio:
   - `proofstack.ask` (timer, con tag `status` = answered | unknown | invalid | unavailable);
   - `proofstack.ask.rate_limited` (contador);
   - `proofstack.knowledge.chunks` (gauge).
3. El health SHALL estar DOWN si el índice está vacío después de arrancar. Su readiness SHALL depender del índice.
4. Las métricas y los logs SHALL NOT contener el texto de las preguntas. Solo se registra su longitud.
5. Con el perfil `prod`, los logs SHALL salir en JSON estructurado (ECS).
6. Las métricas de Spring AI (observations de chat y embeddings) SHALL quedar disponibles en Prometheus.

## R2: Contenedores y despliegue
1. La imagen SHALL basarse en `eclipse-temurin:25-jre`, ejecutarse como usuario no root e incluir `HEALTHCHECK`.
2. `docker compose up` SHALL levantar Ollama (con los modelos descargados), el servicio, Prometheus y Grafana con un dashboard aprovisionado.
3. Un overlay de producción SHALL poner Caddy delante, con TLS automático. El servicio y el puerto de gestión SHALL NOT publicarse.
4. Un workflow SHALL construir la imagen en cada PR y publicarla en GHCR cuando se cree un tag `v*`.

## R3: Laboratorio de Valhalla
1. El módulo `labs/valhalla-lab` SHALL compilar solo con el perfil Maven `valhalla` y con JDK 28 EA y `--enable-preview`. El build normal SHALL NOT incluirlo.
2. SHALL comparar con JMH un `record` frente a un `value record` equivalente en:
   - suma sobre un array grande (localidad de memoria);
   - creación de muchos objetos en un bucle (presión de asignación).
3. Los resultados SHALL documentarse junto con su entorno (CPU, JDK, opciones) y con las salvedades del caso: es una build EA y un microbenchmark.

# ADR-009: Observabilidad y despliegue del servicio "pregúntale a mi portfolio"

- Estado: aceptada
- Fecha: 2026-10

## Contexto

El servicio RAG (ADR-007) es la única pieza de Proofstack que se ejecuta de forma continua. Llama a un LLM, así que necesitamos saber cuánto tarda, cuántas veces dice "no lo sé", cuánto se usa el modelo y si alguien abusa de la API. Todo ello sin registrar lo que preguntan los visitantes.

## Decisión

- **Métricas de dominio con un decorador** (`ObservedAskPortfolio`) en la raíz de composición. El core y el adaptador REST siguen sin depender de Micrometer. El rate limiter solo expone un hook `Runnable`.
  - `proofstack.ask` es un timer con histograma, etiquetado con `status`.
  - `proofstack.ask.rate_limited` y `proofstack.knowledge.chunks`.
  - Spring AI aporta `gen_ai.client.operation` (chat y embeddings, por modelo).
- **Privacidad:** de las preguntas solo se registra la longitud, nunca el texto. Hay un test que lo comprueba.
- **Actuator en un puerto de gestión aparte (8081):**
  - solo `health` (con liveness y readiness) y `prometheus`;
  - el puerto no se publica en la imagen ni detrás de Caddy;
  - la readiness depende de que el índice tenga fragmentos.
- **Imagen de contenedor:**
  - capas de Spring Boot extraídas;
  - `eclipse-temurin:25-jre`;
  - usuario sin privilegios (UID 10001);
  - `HEALTHCHECK` con una clase Java mínima en lugar de curl;
  - en compose: sistema de ficheros de solo lectura, `cap_drop: ALL` y `no-new-privileges`.
- **Compose local** (Ollama, servicio, Prometheus y Grafana con un dashboard aprovisionado), más un **overlay de producción** con Caddy:
  - TLS automático;
  - solo se enrutan `/api/ask` y `/api/status`, con un límite de 8 KB en el cuerpo;
  - cabeceras de seguridad.

  En producción, el servicio solo es accesible a través de Caddy, y por eso se puede confiar en `X-Forwarded-For` (`forward-headers-strategy=framework`).
- **Publicación de la imagen en GHCR** solo al crear un tag `v*`. En cada PR se construye la imagen pero no se publica.

## Consecuencias

- Grafana muestra latencia p50/p95, la tasa de `UNKNOWN`, las llamadas al LLM y el uso del rate limit.
- Se sigue usando una sola instancia: el índice y el rate limit viven en memoria (ver ADR-007).
- Sin GPU, Ollama en CPU puede tardar varios segundos por respuesta. La alternativa es usar un proveedor gestionado cambiando el starter de Spring AI.
- No he podido construir la imagen localmente porque no hay Docker Desktop. Sí verifiqué la extracción de capas, el arranque con `JarLauncher`, el health probe y los ficheros compose (`docker compose config`). El workflow `docker.yml` construirá la imagen en CI.
- Actualización (2026-11): la imagen se construyó y se ejecutó en local con Docker 29. Arranca como UID 10001 con el sistema de ficheros de solo lectura y `cap_drop: ALL`, sirve `/api/status` y, con Ollama caído, `/api/ask` responde 503 sin detalles internos.

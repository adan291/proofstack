# Requirements: Proofstack Fase 3, "Pregúntale a mi portfolio" (RAG)

## Introducción

Un servicio opcional (ADR-002) responde preguntas sobre el perfil usando solo lo que hay en `profile.yaml`. Por ejemplo: "¿Qué experiencia tiene con Kafka?". Cada respuesta cita sus fuentes. La web estática puede mostrar un widget que llama a este servicio, y sigue funcionando completa si el servicio está caído.

Fuera de alcance: autenticación de usuarios, persistencia del índice, despliegue en la nube (Fase 5) y MCP (Fase 4).

## Requisitos

### R1: Conocimiento derivado del perfil
1. El sistema SHALL convertir el perfil en fragmentos de conocimiento. Cada fragmento tiene un id estable, un tipo (persona, experiencia, formación, proyecto, evidencia, lab o skills), un título y un texto.
2. Los fragmentos SHALL NOT contener email ni ubicación exacta (minimización de datos).
3. Las skills SHALL agruparse en un fragmento por área.

### R2: Responder preguntas
**User story:** Como reclutador, quiero preguntar al portfolio por una tecnología o competencia, para saber en segundos qué ha hecho la persona y dónde verlo.

1. WHEN llega una pregunta THEN el sistema SHALL recuperar como máximo `topK` fragmentos con similitud ≥ `minScore`.
2. IF no hay fragmentos relevantes THEN el sistema SHALL responder con estado `UNKNOWN`, **sin llamar al LLM**.
3. El LLM SHALL recibir solo los fragmentos recuperados, delimitados como datos, y SHALL devolver `{answerable, answer, citations[]}`.
4. IF el modelo indica que no puede responder THEN el estado SHALL ser `UNKNOWN`.
5. Las citas SHALL limitarse a ids recuperados. Las desconocidas se descartan y generan un aviso.
6. IF la respuesta es `ANSWERED` sin ninguna cita válida THEN el sistema SHALL tratarla como `UNKNOWN`.
7. Los números de la respuesta que no aparezcan en los fragmentos SHALL generar avisos (`GroundingCheck`).
8. La respuesta SHALL darse en el idioma del perfil.

### R3: API HTTP
1. `POST /api/ask` con `{"question": "..."}` SHALL devolver `{status, answer, citations[{id,type,title}], warnings[]}`.
2. IF la pregunta tiene menos de 3 o más de 500 caracteres THEN SHALL responder 400.
3. IF el proveedor de IA no está disponible THEN SHALL responder 503 con un mensaje genérico, sin detalles internos.
4. SHALL limitar a N peticiones por minuto y por IP (10 por defecto) y responder 429 con `Retry-After`.
5. CORS SHALL permitir solo los orígenes configurados (`proofstack.cors.allowed-origins`), por defecto ninguno.
6. `GET /api/status` SHALL devolver el número de fragmentos indexados y el modelo.
7. El servicio SHALL indexar el perfil al arrancar. IF el perfil es inválido THEN SHALL no arrancar.

### R4: Widget web
1. WHEN se ejecuta `proofstack build --assistant-url <https-url>` THEN la web SHALL incluir un formulario "Pregúntale a mi portfolio".
2. La URL SHALL ser `https`, o `http` solo para `localhost`/`127.0.0.1`.
3. La CSP SHALL añadir `connect-src` con el origen del servicio y nada más.
4. El formulario SHALL estar oculto sin JavaScript. El resto de la página no cambia.
5. La respuesta SHALL insertarse como texto (nunca como HTML), en una región `aria-live`.
6. Los errores 429 y 503 y el estado `UNKNOWN` SHALL mostrarse con mensajes localizados.

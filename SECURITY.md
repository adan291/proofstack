# Seguridad

## Reportar una vulnerabilidad
No abras un issue público. Usa **Security → Report a vulnerability** (aviso de seguridad privado de GitHub) en este repositorio. Responderemos en un plazo de 7 días.

## Qué se considera en alcance
- XSS en la web generada (el tema escapa todo el contenido y aplica una CSP estricta).
- Que la respuesta de un LLM consiga saltarse la validación por esquema o las reglas de honestidad.
- Filtración de secretos (`GEMINI_API_KEY`, `GITHUB_TOKEN`) en logs, mensajes de error o artefactos.
- Formas de evitar el rate limit o el CORS de `proofstack-ai-service`.
- Path traversal o zip bombs en los importadores.
- Que el servidor MCP pueda escribir ficheros o hacer peticiones de red. Está diseñado para ser de solo lectura.

## Lo que el usuario debe saber
- La API de `proofstack-ai-service` es **pública por diseño** y no tiene autenticación. Despliégala siempre detrás del overlay con Caddy (`deploy/compose.prod.yaml`) y con `PROOFSTACK_ALLOWED_ORIGINS` configurado.
- Las claves solo se leen de variables de entorno. Nunca las pongas en `profile.yaml` ni en un commit.
- `proofstack doctor` no publica nada, pero sí avisa de datos de ejemplo. Revisa qué información personal publicas.

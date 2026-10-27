# ADR-002 — Static-first

- Estado: aceptada
- Fecha: 2026-10

## Contexto

Un portfolio se lee mucho y se cambia poco. Mantener un backend con un LLM encendido 24/7 cuesta dinero, aumenta la superficie de ataque y añade puntos de fallo. Además, quien haga fork no debería necesitar un servidor.

## Decisión

- El portfolio se genera como web estática (HTML, CSS y JS) desde `profile.yaml` y se publica en GitHub Pages o Cloudflare Pages.
- Las capacidades dinámicas, como el RAG "pregúntale a mi portfolio" (Fase 3), irán en un servicio opcional y separado. El portfolio seguirá funcionando si ese servicio está caído.
- La página incluye una CSP estricta (`default-src 'self'`) y funciona sin JavaScript.

## Consecuencias

- Hosting gratuito, y la web carga rápido y es segura por defecto.
- Las funciones interactivas con IA requieren un componente desplegado aparte, con su propio rate limiting y control de costes.

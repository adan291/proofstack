# ADR-004 — Render en Java (Mustache) para el MVP y Astro como renderer futuro

- Estado: aceptada
- Fecha: 2026-10

## Contexto

El roadmap proponía Astro para el tema web. Pero meter una cadena de build de Node en el MVP obliga a cualquiera que haga fork a tener dos toolchains (JDK y Node) solo para generar su página.

## Decisión

- El MVP renderiza con jmustache desde Java. El tema "architecture" son plantillas Mustache, CSS y JS sin dependencias.
- Las plantillas son logic-less y escapan HTML por defecto (`{{ }}`). Nunca se usa `{{{ }}}`.
- Los textos de la interfaz se traducen según `language` (`en`/`es`). El contenido lo escribe el usuario.
- Un renderer Astro (o cualquier otro) podrá añadirse como otro adaptador de `PortfolioRenderer`.

## Consecuencias

- Un solo `java -jar` genera el portfolio.
- Las interacciones complejas en el frontend son más limitadas. Es aceptable, porque el objetivo es demostrar backend.
- Poder añadir un segundo renderer sin tocar el core sirve como prueba práctica de ADR-001.

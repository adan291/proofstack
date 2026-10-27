# Requirements — Proofstack MVP (Fase 0 + Fase 1)

## Introducción

Proofstack genera portfolios técnicos para perfiles backend a partir de un único fichero `profile.yaml` versionado en Git. El MVP cubre los cimientos del repositorio (Fase 0) y la generación de una web estática con el tema "architecture" (Fase 1). El primer usuario es Adán (dogfooding); el diseño debe permitir que cualquier otra persona haga fork y genere su portfolio.

Fuera de alcance del MVP: IA, RAG, MCP, importadores (LinkedIn/GitHub), backend desplegado, hardware.

## Glosario

- **Profile**: agregado raíz con datos personales, experiencia, proyectos, skills, evidencias y labs.
- **Evidence**: prueba concreta de una competencia (contexto, acción, resultado, enlace opcional).
- **Lab**: experimento agrupado por tipo (`SOFTWARE`, `AI`, `HARDWARE`, `AUTOMATION`).
- **Renderer**: adaptador de salida que transforma un Profile en un artefacto (web, README…).

## Requisitos

### R1 — Cimientos del repositorio
**User story:** Como mantenedor, quiero un repo con build reproducible y reglas de arquitectura automatizadas, para que el proyecto sea creíble y mantenible.

1. El sistema SHALL compilar con Java 25 (`maven.compiler.release=25`) mediante Maven multimódulo.
2. WHEN se ejecuta `mvn verify` THEN el sistema SHALL ejecutar tests unitarios y tests de arquitectura.
3. IF una clase de `proofstack-core` depende de un paquete externo distinto de `java.*` THEN el test de arquitectura SHALL fallar.
4. IF un adaptador depende de otro adaptador THEN el test de arquitectura SHALL fallar.
5. WHEN se hace push o PR a `main` THEN GitHub Actions SHALL ejecutar `mvn verify` con JDK 25.
6. El repo SHALL incluir LICENSE (Apache-2.0), README y ADR-001 a ADR-004.

### R2 — Modelo de perfil y esquema
**User story:** Como usuario, quiero describir mi carrera en un YAML validable, para no depender de ninguna web ni base de datos.

1. El sistema SHALL definir `schema/profile.schema.json` (JSON Schema 2020-12).
2. WHEN el YAML no cumple el esquema THEN el sistema SHALL informar de cada error con su ruta (p. ej. `$.projects[0].name`) y no generar salida.
3. El dominio SHALL rechazar invariantes inválidas: nombre vacío, `endDate` anterior a `startDate`, experiencia `current` con `endDate`, IDs duplicados.
4. Un Project SHALL poder contener `problem`, `solution`, `techStack`, `learnings`, `links` y referencias a evidencias.
5. Una Evidence SHALL pertenecer a una categoría (`LEADERSHIP`, `COMMUNICATION`, `TECHNICAL`, `DOCUMENTATION`, `MENTORING`) y poder enlazarse desde proyectos o experiencias.

### R3 — CLI
**User story:** Como usuario, quiero un comando para validar y generar mi portfolio, para publicarlo sin servidor.

1. WHEN se ejecuta `proofstack validate <profile.yaml>` THEN el sistema SHALL devolver código 0 si es válido y 1 con la lista de errores si no.
2. WHEN se ejecuta `proofstack build <profile.yaml> --out <dir>` THEN el sistema SHALL generar `index.html`, `styles.css` y `app.js` en `<dir>`.
3. IF el directorio de salida no existe THEN el sistema SHALL crearlo.
4. El CLI SHALL distribuirse como un único JAR ejecutable.

### R4 — Tema web "architecture"
**User story:** Como visitante, quiero navegar el perfil como un diagrama de sistema, para entender rápidamente qué sabe hacer la persona.

1. La página SHALL mostrar cabecera (nombre, rol, resumen, enlaces) y un mapa de nodos por área (Backend, Architecture, AI, Hardware, Leadership…) derivado de las skills y labs.
2. WHEN el visitante activa un nodo (clic o teclado) THEN la página SHALL mostrar los proyectos y evidencias de esa área.
3. La página SHALL ser usable sin JavaScript (contenido completo visible, el JS solo mejora la navegación).
4. La página SHALL usar HTML semántico, foco visible, `aria-expanded`/`aria-controls` en nodos y contraste AA.
5. Todo texto del perfil SHALL escaparse en HTML (sin inyección).
6. La página SHALL incluir el pie "Built with Proofstack".

### R5 — Perfil real
1. El repo SHALL incluir `profiles/adan/profile.yaml` válido que genere el portfolio de Adán.
2. El repo SHALL incluir `profiles/example/profile.yaml` mínimo para quien haga fork.

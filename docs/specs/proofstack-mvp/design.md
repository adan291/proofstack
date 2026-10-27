# Design — Proofstack MVP

## Visión general

```
profile.yaml ──► [in-yaml adapter] ──► Profile (core) ──► [out-web renderer] ──► Artifacts ──► [filesystem sink] ──► dist/
                  JSON Schema +            invariantes        Mustache templates                   (CLI)
                  mapeo a dominio          + ArchitectureMap
```

Arquitectura hexagonal. `proofstack-core` es Java puro (solo `java.*`). La CLI es la raíz de composición.

## Módulos Maven

| Módulo | Contenido | Depende de |
|---|---|---|
| `proofstack-core` | dominio, casos de uso, puertos | — |
| `adapters/proofstack-in-yaml` | `YamlProfileSource`: YAML → JsonNode → JSON Schema → DTO → dominio | core, jackson-yaml, json-schema-validator |
| `adapters/proofstack-out-web` | `ArchitectureThemeRenderer`: Profile → `index.html`, `styles.css`, `app.js` | core, jmustache |
| `proofstack-cli` | Picocli, `FileSystemArtifactSink`, wiring, JAR ejecutable (shade) | core + adaptadores |

`schema/profile.schema.json` vive en la raíz (contrato público) y se empaqueta como recurso de `in-yaml`.

## Paquetes

```
dev.proofstack.core.domain        Profile, PersonalInfo, Link, Experience, Education, Project,
                                  Skill, Evidence, Lab, Period, Area, EvidenceCategory, LabType,
                                  ArchitectureMap, AreaNode, Violation, ProfileValidationException
dev.proofstack.core.port.in       ValidateProfileUseCase, BuildPortfolioUseCase
dev.proofstack.core.port.out      ProfileSource, PortfolioRenderer, ArtifactSink, Artifact
dev.proofstack.core.application   PortfolioService
dev.proofstack.adapter.in.yaml    YamlProfileSource (+ DTOs package-private)
dev.proofstack.adapter.out.web    ArchitectureThemeRenderer (+ view models)
dev.proofstack.cli                ProofstackCli, ValidateCommand, BuildCommand, FileSystemArtifactSink
```

## Modelo de dominio

Todo son `record` inmutables con validación en el constructor compacto y copias defensivas (`List.copyOf`).

- `Period(YearMonth start, YearMonth end)`: `end == null` ⇒ en curso; `end < start` ⇒ violación.
- `Experience(id, role, company, location, period, highlights, technologies, evidenceIds)`. `current()` se deriva de `period`. En YAML, `current: true` con `endDate` es una violación (lo detecta el mapper).
- `Project(id, name, summary, problem, solution, techStack, learnings, links, areas, evidenceIds)`.
- `Skill(name, area)`.
- `Evidence(id, category, title, context, action, result, link, areas)`. Si `areas` está vacío, se deriva de la categoría: LEADERSHIP/MENTORING → `Area.LEADERSHIP`; COMMUNICATION/DOCUMENTATION → `Area.COMMUNICATION`.
- `Lab(id, name, type, description, projectIds)`. `LabType → Area`: AI→AI, HARDWARE/AUTOMATION→HARDWARE, SOFTWARE→ninguna.
- `Profile` valida invariantes globales y acumula **todas** las violaciones antes de lanzar `ProfileValidationException(List<Violation>)`:
  - IDs únicos por colección.
  - Referencias (`evidenceIds`, `projectIds`) existentes.
- `Area` enum ordenado: BACKEND, ARCHITECTURE, MESSAGING, DATA, DEVOPS, AI, HARDWARE, LEADERSHIP, COMMUNICATION. Cada valor tiene un `label` legible.

`ArchitectureMap.from(Profile)` genera un `AreaNode(area, skills, projects, evidences, labs)` por cada área con contenido, en el orden del enum. Es lógica de dominio pura y testeable.

## Puertos y servicio

```java
interface ProfileSource       { Profile load(Path location); }      // lanza ProfileValidationException
interface PortfolioRenderer   { List<Artifact> render(Profile p); }
interface ArtifactSink        { void write(Path outDir, List<Artifact> artifacts); }
record Artifact(String relativePath, String content) {}              // valida path relativo sin ".."

interface ValidateProfileUseCase { ValidationResult validate(Path profile); }
interface BuildPortfolioUseCase  { List<Artifact> build(Path profile, Path outDir); }
```

`PortfolioService` implementa ambos casos de uso. `ValidationResult` es `valid()` + `List<Violation>`.

## Adaptador YAML

1. Jackson `YAMLMapper` → `JsonNode`. Jackson no instancia tipos arbitrarios, así que es seguro frente a deserialización de YAML.
2. Validación con networknt (draft 2020-12). Cada error pasa a `Violation(path, message)`.
3. `JsonNode` → DTO records → dominio. Las excepciones del dominio se envuelven con su ruta.
4. Un error de sintaxis YAML se convierte en `Violation("$", mensaje con línea)`.

## Renderer web (tema "architecture")

- Templates jmustache en `src/main/resources/themes/architecture/`. `{{ }}` escapa HTML por defecto y nunca se usa `{{{ }}}`.
- Estructura de la página: `header` → `nav` del mapa (nodo central con el nombre + nodos por área como `<button aria-expanded aria-controls>`) → `section` panel por área → Experiencia → Formación → `footer` "Built with Proofstack".
- **Mejora progresiva**: sin JS se ven todos los paneles. `app.js` añade la clase `js` a `<html>`, oculta los paneles y gestiona apertura, foco y `aria-expanded`. Las flechas permiten moverse entre nodos.
- CSS: estética de diagrama de sistema (fondo oscuro, nodos con borde, líneas con pseudo-elementos), contraste AA, `:focus-visible`, `prefers-reduced-motion`.
- Las URLs de enlaces solo se aceptan si son `http(s)` o `mailto`. Esto se valida en el esquema y en `Link`.

## CLI

```
proofstack validate <profile.yaml>               → 0 válido | 1 inválido (lista de violaciones) | 2 error E/S
proofstack build <profile.yaml> [--out dist]     → genera artefactos
```

## Testing

- **core**: tests de invariantes, `ArchitectureMap` y `PortfolioService` con fakes. Incluye ArchUnit: `core` solo depende de `java..`.
- **in-yaml**: YAML válido e inválido con fixtures; las rutas de error aparecen en el resultado.
- **out-web**: escape de HTML (`<script>` en un nombre), presencia de `aria-controls` y del pie de página.
- **cli**: ArchUnit (los adaptadores no se conocen entre sí, core no conoce adaptadores) y un test end-to-end `build` sobre `profiles/example`.

## Decisiones (ADRs)

- ADR-001: arquitectura hexagonal.
- ADR-002: static-first.
- ADR-003: Java 25 LTS, con Valhalla en un laboratorio aislado.
- ADR-004: render en Java (jmustache) para el MVP. Astro queda como renderer futuro; poder añadirlo es justamente la prueba de que los puertos funcionan.

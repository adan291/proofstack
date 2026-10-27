# Tasks — Proofstack MVP

- [x] 1. Cimientos del repositorio
  - Parent POM multimódulo (Java 25, versiones fijadas), `.gitignore`, LICENSE Apache-2.0, README, CI con GitHub Actions
  - _Requisitos: R1.1, R1.2, R1.5, R1.6_

- [x] 2. Dominio en `proofstack-core`
  - [x] 2.1 Records del dominio con invariantes (`Period`, `Link`, `Experience`, `Project`, `Evidence`, `Lab`, `Skill`, `Profile`)
  - [x] 2.2 `ArchitectureMap` / `AreaNode`
  - [x] 2.3 Puertos y `PortfolioService`
  - [x] 2.4 Tests unitarios + ArchUnit (core solo `java..`)
  - _Requisitos: R1.3, R2.3, R2.4, R2.5_

- [x] 3. Esquema y adaptador YAML
  - [x] 3.1 `schema/profile.schema.json` (2020-12)
  - [x] 3.2 `YamlProfileSource` con validación y mapeo a dominio con rutas de error
  - [x] 3.3 Tests con fixtures válidos e inválidos
  - _Requisitos: R2.1, R2.2_

- [x] 4. Renderer web "architecture"
  - [x] 4.1 Templates Mustache, CSS y JS con mejora progresiva y accesibilidad
  - [x] 4.2 Tests de escape HTML, ARIA y pie de página
  - _Requisitos: R4.1–R4.6_

- [x] 5. CLI
  - [x] 5.1 Comandos `validate` y `build`, `FileSystemArtifactSink`, JAR ejecutable
  - [x] 5.2 ArchUnit de capas + test end-to-end
  - _Requisitos: R1.4, R3.1–R3.4_

- [x] 6. Perfiles y ADRs
  - [x] 6.1 `profiles/example/profile.yaml` y `profiles/adan/profile.yaml`
  - [x] 6.2 ADR-001 a ADR-004
  - _Requisitos: R1.6, R5.1, R5.2_

- [ ] 7. Publicación (manual, requiere tu cuenta)
  - Crear el repo en GitHub, push, activar GitHub Pages con el workflow de despliegue
  - Completar los `TODO` de `profiles/adan/profile.yaml` con datos reales

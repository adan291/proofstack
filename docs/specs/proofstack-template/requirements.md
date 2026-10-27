# Requirements: Proofstack Fase 6 (plantilla pública)

## Introducción
Convertir el repositorio en una plantilla para que cualquier desarrollador pase de "Use this template" a un portfolio publicado en GitHub Pages sin tocar Java, y sin publicar placeholders ni datos a medias.

## R1: `proofstack init`
1. `proofstack init <dir> --name "<nombre>" --headline "<rol>" [--language es|en]` SHALL crear `<dir>/profile.yaml`. El fichero SHALL ser válido y contener un proyecto y una evidencia de ejemplo marcados con `TODO`.
2. El fichero SHALL empezar con un modeline `# yaml-language-server: $schema=<ruta relativa al schema>`, para que el editor ofrezca autocompletado y validación.
3. IF `<dir>/profile.yaml` existe THEN el comando SHALL NOT sobrescribirlo, salvo con `--force`.

## R2: `proofstack doctor`
1. `proofstack doctor <profile>` SHALL informar de los problemas que impiden publicar:
   - ERROR: cualquier `TODO`, enlaces a `example.com` o URL genéricas (`https://github.com/`, `https://www.linkedin.com/`).
   - WARNING: proyectos sin `problem` o `solution`, proyectos sin enlaces, evidencias sin vincular a ninguna experiencia ni proyecto, resumen personal vacío y experiencia vacía.
2. Cada hallazgo SHALL incluir la ruta (`$.projects[0].problem`) y una sugerencia.
3. Código de salida:
   - 0 si no hay errores;
   - 1 si hay errores;
   - con `--strict`, 1 también si hay warnings.
4. El workflow de Pages SHALL ejecutar `doctor` antes de publicar y SHALL NOT desplegar si hay errores.

## R3: Plantilla
1. El perfil publicado por Pages SHALL configurarse con la variable de repositorio `PROOFSTACK_PROFILE`, con un valor por defecto.
2. Al crear un tag `v*`, un workflow SHALL publicar una release con `proofstack.jar` y su SHA-256.
3. El repo SHALL incluir `CONTRIBUTING.md`, `SECURITY.md`, plantillas de issue y PR, y una guía de inicio rápido.

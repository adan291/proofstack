# Design: Proofstack Fase 6

## Core
- `domain.StarterProfile.create(name, headline, language)` devuelve un `Profile` con:
  - textos en `es` o `en`;
  - una skill, un proyecto (`my-first-project`) y una evidencia (`my-first-evidence`) enlazados entre sí;
  - los campos a rellenar con `TODO`.
- `domain.readiness.PublishReadiness.check(Profile)` devuelve `List<Finding(Severity, path, message, hint)>`. Es puro y determinista.
- Puerto in `ScaffoldProfileUseCase` y servicio `ScaffoldService(ProfileWriter)`.

## Adaptador out-yaml
- `YamlProfileWriter.write(profile, target, overwrite)` añade el modeline cuando encuentra `schema/profile.schema.json` subiendo directorios desde `target`. Es la ruta relativa que verá el usuario en su fork.
- Si no lo encuentra, no añade modeline. Las rutas absolutas no son portables.

## CLI
- `init DIR --name --headline [--language] [--force]`. Al terminar valida el resultado con `YamlProfileSource`.
- `doctor PROFILE [--strict]` imprime `ERROR`/`WARN` con la ruta, el mensaje y una sugerencia, y termina con un resumen.

## Workflows
- `pages.yml`:
  - lee `vars.PROOFSTACK_PROFILE` (por defecto `profiles/example/profile.yaml`);
  - ejecuta `doctor` y luego `build`;
  - usa `vars.PROOFSTACK_ASSISTANT_URL` si está definida.
- `release.yml`: en tags `v*` ejecuta `mvn verify`, calcula el sha256 y crea la release con `gh release create` (permiso `contents: write`).

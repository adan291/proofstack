# Tu portfolio en 15 minutos

Requisitos: JDK 25 ([Temurin](https://adoptium.net/)) y una cuenta de GitHub. No necesitas saber Java.

## 1. Crea tu copia
En GitHub, pulsa **Use this template → Create a new repository**. Después clónalo en tu equipo.

## 2. Consigue la CLI
Descarga `proofstack.jar` desde la última **Release** del repositorio. También puedes compilarla tú mismo con `mvn -DskipTests package` y la tendrás en `proofstack-cli/target/proofstack.jar`.

```bash
alias proofstack="java -jar proofstack.jar"   # PowerShell: function proofstack { java -jar proofstack.jar @args }
```

## 3. Crea tu perfil
Tienes tres formas de empezar; elige una:

```bash
proofstack init profiles/tu-nombre --name "Tu Nombre" --headline "Backend Engineer · Java" --language es
# o parte de tu LinkedIn (Ajustes → Privacidad de datos → Obtener una copia de tus datos):
proofstack import linkedin Basic_LinkedInDataExport.zip --out profiles/tu-nombre/profile.yaml
# y añade tus repos públicos:
proofstack import github tu-usuario -p profiles/tu-nombre/profile.yaml -o profiles/tu-nombre/profile.github.yaml
```

Abre `profile.yaml` en VS Code o Kiro con la extensión de YAML. La primera línea apunta al esquema, así que tendrás autocompletado y validación mientras escribes.

## 4. Rellena lo importante
- **Proyectos:** qué problema resolvían y cómo lo resolviste. Una lista de tecnologías no demuestra nada; esto sí.
- **Evidencias:** contexto, acción y resultado. **No inventes métricas.** Si no tienes un número, describe el resultado con palabras.
- Enlaza cada evidencia desde la experiencia o el proyecto al que corresponde.

## 5. ¿Está listo?
```bash
proofstack doctor profiles/tu-nombre/profile.yaml
proofstack build  profiles/tu-nombre/profile.yaml --out dist     # abre dist/index.html
```
`doctor` bloquea la publicación mientras queden `TODO` o enlaces de ejemplo.

## 6. Publica
1. En tu repositorio, ve a **Settings → Pages → Source: GitHub Actions**.
2. En **Settings → Secrets and variables → Actions → Variables**, crea `PROOFSTACK_PROFILE` = `profiles/tu-nombre/profile.yaml`.
3. Haz push a `main`. El workflow pasa `doctor`, construye la web y la publica.

## Extras
- Generar borradores de posts, README o guiones a partir de tus proyectos: `proofstack generate --help`.
- Usar tu perfil desde Kiro u otro cliente MCP: consulta el README, sección MCP.
- Un asistente "pregúntale a mi portfolio" en tu web: consulta `deploy/` y ADR-007 y ADR-009.

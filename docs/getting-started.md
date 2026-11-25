# Tu portfolio y tu CV en 15 minutos

Necesitas JDK 25 ([Temurin](https://adoptium.net/)) y una cuenta de GitHub. No necesitas saber Java. Hay una versión visual de esta guía en [`guide/index.html`](guide/index.html).

## 1. Crea tu copia

En GitHub, pulsa **Use this template → Create a new repository** y clónalo en tu equipo.

## 2. Consigue la CLI

Descarga `proofstack.jar` de la última **Release** del repositorio y ponlo en la carpeta del repo. También puedes compilarlo con `mvn -DskipTests package`; quedará en `proofstack-cli/target/proofstack.jar`.

```bash
alias proofstack="java -jar proofstack.jar"
# PowerShell (Windows): function proofstack { java -jar proofstack.jar @args }
```

El alias solo dura mientras tengas abierta la terminal. Ejecuta los comandos desde la carpeta del repositorio, porque las rutas como `profiles/...` son relativas a ella.

## 3. Crea tu perfil

Elige una forma de empezar:

```bash
# Desde cero
proofstack init profiles/tu-nombre --name "Tu Nombre" --headline "Backend Engineer · Java" --language es

# Desde tu LinkedIn (Ajustes → Privacidad de datos → Obtener una copia de tus datos)
proofstack import linkedin Basic_LinkedInDataExport.zip --out profiles/tu-nombre/profile.yaml

# Y después añade tus repos públicos
proofstack import github tu-usuario -p profiles/tu-nombre/profile.yaml -o profiles/tu-nombre/profile.github.yaml
```

Si vives en el EEE o en Suiza, puedes importar LinkedIn sin ZIP con `import linkedin-api` ([`linkedin-api.md`](linkedin-api.md)).

## 4. Rellénalo

```bash
proofstack studio profiles/tu-nombre/profile.yaml
```

Se abre un editor en el navegador. Si prefieres editar el YAML, ábrelo en VS Code o Kiro con la extensión de YAML: la primera línea apunta al esquema y tendrás autocompletado.

Lo importante:
- **Proyectos:** qué problema resolvían y cómo lo resolviste. Una lista de tecnologías no demuestra nada; esto sí.
- **Evidencias:** contexto, acción y resultado. **No inventes métricas.** Si no tienes un número, describe el resultado con palabras.
- Enlaza cada evidencia desde la experiencia o el proyecto al que corresponde, y cada proyecto a su repo o demo.
- Añade idiomas y certificaciones: salen en el CV.

## 5. ¿Está listo?

```bash
proofstack doctor profiles/tu-nombre/profile.yaml
proofstack build  profiles/tu-nombre/profile.yaml --out dist --with-cv   # abre dist/index.html
proofstack cv     profiles/tu-nombre/profile.yaml --out dist/cv          # cv.md, cv.html y cv.docx
```

`doctor` da **errores** (bloquean la publicación: `TODO`, enlaces de ejemplo) y **avisos** (sugerencias, como un proyecto sin enlace).

## 6. Publica

1. En tu repositorio, ve a **Settings → Pages** y elige **Source: GitHub Actions**.
2. Ve a **Settings → Secrets and variables → Actions**, pestaña **Variables**, y en **Repository variables** crea `PROOFSTACK_PROFILE` con el valor `profiles/tu-nombre/profile.yaml`. Ojo: tiene que ser una variable de repositorio, no de un *environment*.
3. Haz push a `main`, o lanza **Actions → Deploy portfolio → Run workflow**.

Tu web queda en `https://<usuario>.github.io/<repo>/`, con un botón **Descargar CV** que baja el PDF.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `proofstack` no se reconoce | El alias o la función no existen en esta terminal. Vuelve a definirlos (paso 2). |
| `File not found: profiles/...` | Estás en otra carpeta. Haz `cd` a la carpeta del repositorio. |
| El despliegue falla en *Ready to publish?* | `doctor` encontró errores, o `PROOFSTACK_PROFILE` no existe o no es de repositorio. Mira las líneas `ERROR` del log. |
| La web da 404 | El primer despliegue aún no terminó, o *Pages → Source* no está en *GitHub Actions*. |
| LinkedIn responde 401 | La app no usa la página "Member Data Portability (Member-Only Default Company Page)", o tu cuenta no está en el EEE. |

## Extras

- Borradores de posts, README o guiones a partir de tus proyectos: `proofstack generate --help`.
- Un CV para cada oferta: `proofstack cv ... --job oferta.txt`.
- Usar tu perfil desde Kiro u otro cliente MCP: sección MCP del README.
- Un asistente "pregúntale a mi portfolio" en tu web: `deploy/`, ADR-007 y ADR-009.

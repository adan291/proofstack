# Tu portfolio y tu CV en 15 minutos

Versión en texto de la [guía visual](https://adan291.github.io/proofstack/guide/) (en el repositorio, [`guide/index.html`](guide/index.html)). La guía visual tiene además recetas para el día a día, las piezas opcionales y qué hacer si cambias el código.

## 1. Instala Java y Git

JDK 25 ([Temurin](https://adoptium.net/)) y [Git](https://git-scm.com/). Maven 3.9 solo si vas a compilar desde el código. No necesitas saber Java.

## 2. Ten el repositorio en tu equipo

En GitHub, pulsa **Use this template → Create a new repository**. Después:

```bash
git clone https://github.com/<usuario>/<repo>.git
cd <repo>
./proofstack --version
```

`proofstack` es un lanzador que viene en el repositorio y funciona igual en PowerShell, macOS y Linux. La primera vez compila la herramienta con Maven. Sin Maven, descarga `proofstack.jar` de la última **Release** y déjalo en la carpeta del repositorio.

Ejecuta siempre desde la carpeta del repositorio: las rutas como `profiles/...` son relativas a ella. En el CMD clásico de Windows escribe `proofstack` sin `./`.

## 3. Crea tu perfil

```bash
./proofstack setup
```

Te pregunta tu nombre y tu usuario de GitHub. Crea el perfil, trae tus repositorios públicos (eliges cuáles), configura LinkedIn la primera vez y lo importa, te dice qué falta y abre Studio. Puedes repetirlo cuando quieras: no toca tus proyectos ni tus evidencias.

Si tu cuenta de LinkedIn no está en el EEE o Suiza, usa el ZIP (Ajustes → Privacidad de datos → Obtener una copia de tus datos): `./proofstack setup --linkedin-zip Basic_LinkedInDataExport.zip`.

Los pasos por separado siguen existiendo: `init`, `import linkedin`, `import linkedin-api` ([`linkedin-api.md`](linkedin-api.md)) e `import github`.

## 4. Rellénalo

```bash
./proofstack studio profiles/tu-nombre/profile.yaml
```

Se abre un editor en el navegador. Guarda con `Ctrl+S` y ciérralo con `Ctrl+C` en la terminal. Si prefieres el YAML, ábrelo en VS Code o Kiro con la extensión de YAML: la primera línea apunta al esquema y tendrás autocompletado.

Lo importante:
- **Proyectos:** qué problema resolvían y cómo lo resolviste. Una lista de tecnologías no demuestra nada; esto sí.
- **Evidencias:** contexto, acción y resultado. **No inventes métricas.** Si no tienes un número, describe el resultado con palabras.
- Enlaza cada evidencia desde la experiencia o el proyecto al que corresponde, y cada proyecto a su repo o demo.
- Añade idiomas y certificaciones: salen en el CV.

## 5. Lanza la web y el CV en tu equipo

```bash
./proofstack doctor profiles/tu-nombre/profile.yaml                       # 0 errores = se puede publicar
./proofstack build  profiles/tu-nombre/profile.yaml --out dist --with-cv  # web en dist/, CV en dist/cv/
```

Ábrelos con `Start-Process dist\index.html` (Windows) o `open dist/index.html` (macOS; en Linux, `xdg-open`). El CV está en `dist/cv/cv.html` (imprímelo como PDF) y `dist/cv/cv.docx` (Word).

`doctor` da **errores** (bloquean la publicación: `TODO`, enlaces de ejemplo) y **avisos** (sugerencias, como un proyecto sin enlace).

## 6. Publica

1. En tu repositorio, ve a **Settings → Pages** y elige **Source: GitHub Actions**.
2. Ve a **Settings → Secrets and variables → Actions**, pestaña **Variables**, y en **Repository variables** crea `PROOFSTACK_PROFILE` con el valor `profiles/tu-nombre/profile.yaml`. Tiene que ser una variable de repositorio, no de un *environment*.
3. Haz `git add -A`, `git commit` y `git push`, o lanza **Actions → Deploy portfolio → Run workflow**.

Tu web queda en `https://<usuario>.github.io/<repo>/`, con un botón **Descargar CV** que baja el PDF, y esta guía en `/guide/`.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `./proofstack` no se reconoce | Estás en otra carpeta. Entra en la del repositorio con `cd`. |
| `File not found: profiles/...` | La ruta es relativa a la carpeta en la que estás. Entra en la del repositorio. |
| Cambié el código y no se nota | El lanzador usa el último jar compilado: `mvn -DskipTests package -pl proofstack-cli -am`. |
| Studio dice «Falta el token» | Abre la URL completa que muestra la terminal, con su `#token=…`. |
| El despliegue falla en *Ready to publish?* | `doctor` encontró errores, o `PROOFSTACK_PROFILE` falta o no es de repositorio. Ejecuta `./proofstack doctor` en local. |
| La web da 404 | El primer despliegue aún no terminó, o *Pages → Source* no está en *GitHub Actions*. |
| LinkedIn responde 401 | La app no usa la página "Member Data Portability (Member-Only Default Company Page)", o tu cuenta no está en el EEE ni en Suiza. |

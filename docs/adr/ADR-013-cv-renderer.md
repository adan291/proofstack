# ADR-013: CV como renderer determinista

- Estado: aceptada
- Fecha: 2026-11

## Contexto

El mismo `profile.yaml` ya genera la web, y el formato `cv-bullets` redacta viñetas con un LLM. Un CV completo es otra cosa: debe caber en una o dos páginas, pasar filtros ATS y no contener nada que el perfil no demuestre. Un modelo puede inventar cifras (ADR-006), y un CV con una cifra falsa es peor que no tener CV.

## Decisión

- **Nuevo adaptador `proofstack-out-cv`** que implementa `PortfolioRenderer`, igual que el de la web. Es independiente de los demás adaptadores y la regla de ArchUnit lo comprueba.
- **Sin LLM.** `CvBuilder` solo selecciona y ordena lo que ya está en el perfil. El CV no puede decir nada que el perfil no diga.
- **Un modelo neutro (`Cv`) con dos salidas**: `cv.md` (editable a mano) y `cv.html` con `cv.css` (imprimible a PDF desde el navegador). Las dos salen del mismo modelo, así que no pueden contradecirse.
- **Formato apto para ATS:** una columna, solo texto, sin tablas, iconos ni imágenes.
- **Los `TODO` se omiten**, nunca se imprimen. El comando avisa de cuántos hay en el perfil.
- **Selección de proyectos** con una puntuación explicable: coincide con `--focus` (+4), tiene problema y solución (+2), tiene una evidencia con resultado (+2), tiene enlace (+1). Los empates conservan el orden que eligió el autor. Por defecto se muestran 5.
- **`--focus <área>`** pone primero las skills y los proyectos de esa área, para tener un CV por objetivo desde un solo perfil.
- **`--job oferta.txt`** adapta el orden a una oferta con coincidencia de palabras (`JobOffer`, `JobFit`), sin modelo. Solo reordena lo que el perfil ya dice: nunca añade texto. Descarta stop-words en español e inglés y tokens que empiezan por dígito (versiones, años, salarios), y conserva `c#`, `c++` o `node.js` enteros. Informa de los términos de la oferta que el perfil nunca menciona, como aviso para revisar a mano.
- **Idiomas y certificaciones** son secciones del perfil (`languages`, `certifications`) y del CV, y las importa LinkedIn (ZIP y API).
- **CSP estricta** en el HTML (`default-src 'none'; style-src 'self'`) y escape de HTML activado de forma explícita.

## Consecuencias

- El CV es tan bueno como el perfil. Un proyecto sin evidencia con resultado sale sin línea de "Resultado".
- `cv.docx` se escribe en Office Open XML sin librerías (`CvDocx`): una columna, estilos de título nativos y listas reales, sin tablas ni imágenes. Sale del mismo modelo `Cv`, así que dice lo mismo que el Markdown. La salida es reproducible: el mismo perfil da los mismos bytes.
- No hay PDF directo. Se genera desde el navegador (Imprimir → Guardar como PDF), para no añadir un motor de PDF como dependencia.
- Las etiquetas existen en español e inglés. Otros idiomas caen a inglés.

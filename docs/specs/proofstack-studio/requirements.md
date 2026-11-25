# Requirements: Proofstack Fase 7 (Studio)

## Introducción

Editar `profile.yaml` a mano exige conocer el esquema y produce errores de indentación. Studio es un editor web **local**: `proofstack studio <profile>` abre en el navegador un formulario por secciones, valida antes de guardar y muestra en vivo cómo quedan el CV y la web. El fichero YAML sigue siendo la fuente de verdad, versionado en Git.

Fuera de alcance: cuentas de usuario, despliegue en internet, reescritura con IA y OAuth con terceros.

## R1: Arranque seguro

1. `proofstack studio <profile> [--port N] [--no-browser]` SHALL servir la aplicación solo en `127.0.0.1` y abrir el navegador, salvo con `--no-browser`.
2. Al arrancar SHALL generar un token aleatorio de 256 bits. Toda petición a `/api/**` SHALL exigirlo en la cabecera `X-Studio-Token`; la URL que se abre lo entrega en el fragmento (`#token=`), que nunca viaja al servidor ni a los logs.
3. El servidor SHALL rechazar con 403 las peticiones cuyo `Host` no sea `127.0.0.1:<puerto>` o `localhost:<puerto>` (DNS rebinding), y las de escritura cuyo `Origin` no sea el propio.
4. Las respuestas SHALL llevar una CSP estricta (`default-src 'self'`), `X-Content-Type-Options: nosniff` y `Cache-Control: no-store`.
5. El cuerpo de una petición SHALL limitarse a 1 MiB.

## R2: Leer y guardar el perfil

1. `GET /api/profile` SHALL devolver el perfil como JSON, junto con el resultado de `doctor` (errores y avisos con su ruta).
2. `PUT /api/profile` SHALL validar el JSON con el mismo esquema y las mismas reglas de dominio que `validate`.
   - IF es inválido THEN SHALL responder 422 con las violaciones (ruta y mensaje) y SHALL NOT tocar el fichero.
   - IF es válido THEN SHALL escribirlo en YAML de forma atómica (fichero temporal y renombrado), guardando antes una copia `profile.yaml.bak`.
3. Studio SHALL editar únicamente el fichero indicado al arrancar. Ninguna petición puede elegir otra ruta.
4. IF el fichero ha cambiado en disco desde la última lectura THEN el guardado SHALL responder 409 en lugar de sobrescribir los cambios de otro editor.

## R3: Editor

1. La interfaz SHALL ofrecer una sección por bloque del perfil: persona, skills, experiencia, formación, proyectos, evidencias, labs, idiomas y certificaciones.
2. Los formularios SHALL generarse a partir de `schema/profile.schema.json`, de modo que un campo nuevo en el esquema aparezca sin tocar la interfaz.
3. SHALL permitir añadir, borrar y reordenar elementos de cada lista.
4. Los hallazgos de `doctor` SHALL mostrarse junto al campo al que se refieren.
5. SHALL ser usable solo con teclado y con lector de pantalla (etiquetas, foco visible, errores anunciados).

## R4: Vista previa

1. `GET /preview/cv` y `GET /preview/site` SHALL renderizar el perfil **guardado** con los mismos renderers que `cv` y `build`.
2. La vista previa de la web SHALL omitir el asistente "pregunta a mi portfolio".

## R5: CV en Word

1. `proofstack cv` SHALL generar también `cv.docx`, con el mismo contenido y orden que `cv.md` (un único modelo, ADR-013).
2. El documento SHALL ser apto para ATS: una columna, estilos de título nativos de Word, sin tablas, imágenes ni cuadros de texto.
3. SHALL generarse sin dependencias nuevas (Office Open XML escrito directamente).

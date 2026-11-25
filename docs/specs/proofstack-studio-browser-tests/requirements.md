# Requirements: tests de Studio en un navegador real

## Introducción

Los tests de Studio (`StudioServerTest`, `StudioUiTest`) prueban el servidor con HTTP en crudo y comprueban que el HTML no tiene scripts en línea. Nadie prueba `studio.js` ni `schema-form.js` (unas 600 líneas): guardar, el 409, los avisos junto a cada campo o la vista previa solo se han visto a mano. La revisión de noviembre lo marcó como H9; el cambio de `Sec-Fetch-Site` (H11) se comprobó con una captura de Chrome hecha a mano.

Fuera de alcance: probar la web publicada (`out-web`) y el CV, que ya tienen tests de su HTML; pruebas visuales píxel a píxel.

## R1: Flujo de edición

1. WHEN se abre la URL que imprime `proofstack studio` THEN el editor SHALL cargar el perfil y anunciar «Perfil cargado.» en la barra de estado.
2. WHEN se cambia un campo de texto y se pulsa Guardar (o Ctrl+S) THEN el fichero en disco SHALL contener el valor nuevo, y la barra SHALL anunciar «Guardado».
3. WHEN se añade, se borra o se reordena un elemento de una lista THEN el guardado SHALL reflejarlo en el YAML.
4. WHEN se guarda un documento inválido THEN el fichero SHALL NOT cambiar, y el campo culpable SHALL quedar marcado con `aria-invalid="true"` y con su mensaje.
5. WHEN el fichero cambia en disco antes de guardar THEN Studio SHALL mostrar el botón «Recargar desde el disco» y SHALL NOT sobrescribir el fichero.
6. WHEN se abre sin `#token=` THEN SHALL mostrar el mensaje «Falta el token».
7. WHEN `profile.yaml` no es YAML válido THEN SHALL mostrar la línea y la columna del error (H4).

## R2: Avisos y vista previa

1. Los hallazgos de `doctor` SHALL aparecer en la lista «Antes de publicar» y junto al campo al que se refieren. Pulsar un hallazgo SHALL llevar el foco a ese campo.
2. Las pestañas CV y Web de la vista previa SHALL cargar el documento con sus estilos, dentro del iframe aislado, y SHALL actualizarse tras cada guardado.
3. La consola del navegador SHALL NOT registrar violaciones de CSP ni errores de JavaScript durante los flujos de R1 y R2.

## R3: Accesibilidad

1. Todos los controles SHALL ser alcanzables con Tab y tener nombre accesible.
2. La página SHALL pasar una auditoría automática de accesibilidad (axe-core) sin violaciones de nivel serio o crítico.

## R4: Ejecución

1. Los tests SHALL ejecutarse con `mvn -Pe2e verify` y SHALL NOT formar parte de `mvn verify`, que debe seguir siendo rápido y no necesitar navegador.
2. CI SHALL ejecutarlos en un job aparte, en cada push a `main` y en cada PR que toque `adapters/proofstack-in-studio` o el esquema.
3. Cada test SHALL usar una copia temporal del perfil de ejemplo, nunca un perfil de `profiles/`.

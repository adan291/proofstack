# Requirements: conflictos en Studio sin perder ediciones

## Introducción

Si `profile.yaml` cambia en disco mientras se edita en Studio (otro editor, `git pull`), guardar responde 409 y la única salida es «Recargar desde el disco», que descarta lo editado. La guía pide copiarlo antes a mano, pero no hay forma de exportarlo (H23). El 409 protege el fichero; lo que falta es no perder el trabajo de quien edita.

Fuera de alcance: edición simultánea en tiempo real y varias pestañas sincronizadas.

## R1: Fusión

1. WHEN un guardado responde 409 THEN Studio SHALL proponer una fusión de tres vías: lo que se cargó (base), lo editado (mío) y lo que hay ahora en disco (suyo).
2. Un campo cambiado solo en un lado SHALL tomar el valor de ese lado sin preguntar.
3. Un campo cambiado en los dos lados con valores distintos SHALL ser un conflicto. Studio SHALL mostrarlo con los dos valores y el usuario SHALL elegir uno por conflicto.
4. Los elementos de listas con `id` (experiencia, proyectos, evidencias, labs, formación) SHALL emparejarse por `id`, no por posición. Las listas sin `id` (skills, highlights, enlaces...) SHALL compararse enteras.
5. El resultado de la fusión SHALL NOT guardarse solo: el usuario lo revisa y guarda, y ese guardado pasa la validación normal (esquema, dominio y versión).

## R2: No perder el trabajo

1. Studio SHALL ofrecer en todo momento «Descargar mis cambios», que baja el documento editado como `profile.edited.json`.
2. Lo editado y sin guardar SHALL sobrevivir a una recarga accidental de la pestaña, guardándose en `sessionStorage` de esa pestaña. Al volver, Studio SHALL ofrecer recuperarlo o descartarlo.
3. «Recargar desde el disco» con cambios sin guardar SHALL ofrecer antes descargarlos.

## R3: Seguridad

1. La fusión SHALL usar solo versiones que Studio sirvió en esta sesión. Una versión base desconocida SHALL responder 409 sin fusión.
2. Las mismas defensas que el resto de la API: token, `Host`, `Origin`, JSON y 1 MiB (ADR-014).

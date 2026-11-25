# Requirements: fechas con precisión de año

## Introducción

El esquema exige `YYYY-MM` en `startDate`, `endDate` y en la fecha de las certificaciones. Muchas personas solo saben el año de sus estudios, y LinkedIn a menudo da solo el año. Hoy hay dos salidas, y las dos son malas: inventar un mes, que el CV imprime como «Jan 2018», o marcar `TODO: dates`, que bloquea la publicación hasta que el usuario invente uno (H3, `2397db4`). Además, `startDate: 2018` sin comillas falla con «integer found, string expected».

Fuera de alcance: fechas con día y rangos aproximados («hacia 2015»).

## R1: Perfil

1. `startDate`, `endDate` y `certifications[].date` SHALL aceptar `YYYY` además de `YYYY-MM`.
2. Un año escrito sin comillas en el YAML (`startDate: 2018`) SHALL ser válido.
3. `endDate` SHALL NOT ser anterior a `startDate`. Con precisiones distintas se compara por año cuando una de las dos fechas no tiene mes: `2018` a `2018-05` es válido.
4. Los perfiles con `YYYY-MM` SHALL seguir siendo válidos y leerse igual (compatibilidad total).

## R2: Salidas

1. El CV (Markdown, HTML y Word), la web y el texto que indexa el RAG SHALL mostrar solo el año cuando la fecha no tiene mes: «2018 – 2020», nunca «Jan 2018».
2. El orden de experiencia y formación SHALL seguir siendo de más reciente a más antiguo. Ante un empate entre `2018` y `2018-05`, va primero la que tiene mes.
3. El writer de YAML y Studio SHALL conservar la precisión: lo que se lee como `2018` se escribe como `2018`.

## R3: Importadores

1. WHEN LinkedIn da solo el año THEN el importador SHALL guardar `YYYY` sin `TODO`.
2. WHEN LinkedIn no da fecha de inicio THEN el importador SHALL seguir marcándolo con `TODO: dates` (H3), porque no hay ningún dato que guardar.
3. La fecha de una certificación con solo el año SHALL guardarse como `YYYY` (hoy se convierte en enero).

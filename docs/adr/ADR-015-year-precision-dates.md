# ADR-015: Fechas con la precisión que conoce el usuario

- Estado: aceptada
- Fecha: 2026-11
- Relacionada con: ADR-006, ADR-013

## Contexto

El esquema exigía `YYYY-MM` en `startDate`, `endDate` y en la fecha de las certificaciones. Mucha gente solo sabe el año de sus estudios, y LinkedIn a menudo da solo el año. Las dos salidas posibles eran malas:

- inventar un mes, que el CV imprimía como «Jan 2018» (ADR-006 prohíbe inventar datos);
- marcarlo como `TODO`, que bloqueaba la publicación hasta que el usuario inventara un mes.

Además, `startDate: 2018` sin comillas fallaba con «integer found, string expected».

## Decisión

- **Dominio:** `ProfileDate(int year, Integer month)` sustituye a `YearMonth` en `Period` y `Certification`. No hay forma de convertir un año en un mes.
- **Esquema:** la definición `date` acepta `YYYY`, `YYYY-MM` o un entero entre 1900 y 2100 (el año sin comillas).
- **Salidas:** `ProfileDate.format` y `Period.format` son la única forma de imprimir fechas. Muestran «2018 – 2020» o «Mar 2021 – Present», nunca un mes que no está en el perfil. Las usan el CV y la web.
- **Orden:** por año y mes. Ante un empate entre `2018` y `2018-05`, en una lista de más reciente a más antiguo va primero la que tiene mes.
- **Validación:** si una de las dos fechas de un periodo no tiene mes, solo se comparan los años (`2018` a `2018-05` es válido).
- **Importadores:** un año de LinkedIn se guarda como año. Solo una fecha de inicio que falta se sigue marcando con `TODO: dates`.

## Consecuencias

- Los perfiles con `YYYY-MM` se leen y se escriben igual que antes. La web y el CV de `profiles/example` y `profiles/adan` salen idénticos byte a byte con esta versión y con la anterior.
- Studio pierde el selector de mes del navegador (`<input type=month>`), que no admite un año solo. El campo es de texto y el esquema lo valida.
- `ProfileDate` es pequeño, inmutable y se lee mucho: entra en la lista de candidatos a `value record` de ADR-010.

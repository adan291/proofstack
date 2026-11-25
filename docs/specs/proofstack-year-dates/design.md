# Design: fechas con precisión de año

## Decisiones

| Tema | Decisión | Alternativa descartada y motivo |
|---|---|---|
| Tipo en el dominio | Nuevo value record `ProfileDate(int year, Integer month)`, con `isYearOnly()`, `toString()` (`2018` o `2018-05`) y un comparador | Mantener `YearMonth` y añadir flags de precisión a `Period`: cada renderer tendría que acordarse de mirarlos, que es justo el fallo de H3. `LocalDate` con día 1: inventa un día además del mes |
| Esquema | `yearMonth` pasa a `oneOf`: string `^[0-9]{4}(-(0[1-9]\|1[0-2]))?$` o integer entre 1900 y 2100 (R1.2) | Convertir los enteros a texto antes de validar: el esquema dejaría de describir lo que acepta, y el autocompletado del editor lo marcaría como error |
| Formato de salida | Un único formateador en el dominio, `ProfileDate.format(Locale)`, que devuelve `MMM yyyy` o `yyyy` | Que cada renderer formatee por su cuenta, como hoy hacen `CvBuilder`, `ArchitectureThemeRenderer` y `ProfileKnowledge` |

Se registrará en un ADR nuevo (ADR-015, «Fechas con precisión de año»). No contradice ADR-013: el CV sigue siendo determinista y sin texto generado. Afina ADR-006: no inventar datos incluye no inventar meses.

## Cambios por módulo

- **core:** `ProfileDate` sustituye a `YearMonth` en `Period` y en `Certification.issued`. Los tipos usan `YearMonth` en 14 ficheros de producción; cada uso pasa a `ProfileDate` o a su clave de orden. `Period` valida R1.3.
- **in-yaml:** `DocumentMapper` lee los dos formatos y el entero. `profile.schema.json` cambia según la tabla.
- **out-yaml y `YamlProfileDocumentStore`:** escriben `ProfileDate.toString()`. Los años van entre comillas por `TextQuoting` (H1), para que se relean como texto.
- **out-cv, out-web y `ProfileKnowledge`:** usan `ProfileDate.format`.
- **in-linkedin:** `LinkedInDates.parse` devuelve `ProfileDate`. Desaparece el aviso de «solo el año» de H3; se mantiene el de «sin fecha de inicio».
- **Studio:** sin cambios de código. El formulario sale del esquema; se comprobará que `schema-form.js` muestra bien un `oneOf` de string e integer y, si no, se tratará el caso.

## Pruebas

- Ida y vuelta YAML → dominio → YAML con `2018`, `2018-05` y el entero `2018`.
- CV y web: «2018 – 2020» y orden con empates (R2.2).
- Los perfiles actuales (`profiles/*`) se leen igual antes y después, comparando los modelos de CV y web.

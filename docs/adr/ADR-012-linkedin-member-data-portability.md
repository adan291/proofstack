# ADR-012: Importar de LinkedIn con la Member Data Portability API

- Estado: aceptada
- Fecha: 2026-10

## Contexto

El objetivo es que el portfolio se alimente de LinkedIn sin escribir los datos a mano. La API estándar de LinkedIn solo da nombre, foto y email. Experiencia y formación requieren ser partner. El scraping viola las condiciones de uso. El export en ZIP funciona, pero es manual. Desde la DMA, LinkedIn ofrece a los miembros de la UE la Member Data Portability API (`r_dma_portability_self_serve`) para leer sus propios datos sin aprobación de partner.

## Decisión

- **Nuevo adaptador `LinkedInMemberDataSource`** (puerto `MemberProfileSource`).
  - Usa `GET /rest/memberSnapshotData` con `Linkedin-Version: 202312`, la única versión que acepta.
  - Solo pide los dominios PROFILE, POSITIONS, EDUCATION, SKILLS y PROJECTS.
  - Sigue la paginación, pero solo dentro del mismo origen.
  - Los campos son los mismos que en las columnas del ZIP, así que reutiliza el mismo mapeo (`LinkedInExportImporter.fromTables`).
- **OAuth de tres pasos para CLI.**
  - Callback en loopback, activo solo durante el login.
  - `state` aleatorio comparado en tiempo constante.
  - Página de callback estática, sin reflejar nada de la petición.
  - Token solo en memoria. `LINKEDIN_ACCESS_TOKEN` es opcional.
- **`ProfileMerge` en el core** decide qué gana en cada campo.
  - LinkedIn manda en la trayectoria: experiencia, formación y skills.
  - Tu perfil manda en las pruebas y en la presentación: proyectos, evidencias, labs, nombre y enlaces.
  - Un puesto importado hereda las evidencias y tecnologías del puesto que sustituye. Se emparejan por empresa, o por "puesto actual" si la empresa era `TODO`.
- **`CareerImportService`** escribe siempre en un fichero nuevo y se niega a sobrescribir el perfil original. La opción `--merge-into` vale también para el ZIP.
- **Formato `evidence-draft`:** borradores de evidencias a partir de los puestos que las referencian.
  - Si faltan datos deja `TODO` y no inventa nada (ADR-006).
  - El resultado nunca se escribe solo en el YAML.

## Consecuencias

- Configurarlo una vez exige una app en LinkedIn Developers. Debe estar asociada a la página "Member Data Portability (Member-Only Default Company Page)": con una página propia LinkedIn no concede el producto y la API responde 401 "You are not authorized to call this API".
- La API solo está disponible para miembros del Espacio Económico Europeo y Suiza. Fuera, sigue existiendo el importador ZIP.
- Verificado con una cuenta real: la importación devolvió 6 puestos, 3 estudios, 35 skills y 3 proyectos, y el mapeo compartido con el ZIP funcionó sin cambios.
- Los errores de la API se muestran con el código y el mensaje de LinkedIn (nunca el token), porque un 401 genérico llevó a diagnosticar mal el problema.

# Requirements: Proofstack Fase 2, motor de contenido e importador de LinkedIn

## Introducción

A partir de un único `profile.yaml`, Proofstack genera borradores de contenido (post de LinkedIn, README, viñetas de CV, guion de vídeo y briefing para NotebookLM) usando un LLM intercambiable: Gemini en la nube u Ollama en local. Además importa el export oficial de datos de LinkedIn para crear un primer `profile.yaml`.

Principios:
- **Nunca inventar.** Sin claves ni modelo disponible, se devuelve un error explícito; no hay fallbacks falsos como en LK-main.
- **Los números que no están en el perfil se señalan como sospechosos.**

Fuera de alcance: publicar en LinkedIn, el RAG (Fase 3) y la API de LinkedIn, que exige ser partner.

## Requisitos

### R1: Generación de contenido
**User story:** Como desarrollador, quiero generar borradores a partir de un proyecto o una evidencia de mi perfil, para publicar sobre lo que he construido sin empezar desde cero.

1. WHEN se ejecuta `proofstack generate <formato> <profile.yaml> --subject <id>` THEN el sistema SHALL generar contenido del formato pedido a partir del proyecto o la evidencia con ese id.
2. Formatos soportados: `linkedin-post`, `readme`, `cv-bullets`, `video-script`, `notebooklm-brief`.
3. IF el id no existe THEN el sistema SHALL fallar con código 1 y listar los ids disponibles.
4. El contenido SHALL generarse en el `language` del perfil, salvo que se indique `--language`.
5. WHEN se indica `--out <fichero>` THEN el sistema SHALL escribir el resultado en Markdown en ese fichero; IF no se indica THEN SHALL escribirlo por stdout.

### R2: Proveedores de LLM
**User story:** Como usuario, quiero elegir entre la nube y un modelo local, para controlar coste y privacidad.

1. El sistema SHALL soportar `--provider gemini|ollama`, con `ollama` por defecto (local y gratuito).
2. Gemini SHALL leer la clave **solo** de la variable de entorno `GEMINI_API_KEY`. Nunca de argumentos, para que no quede en el historial, ni de ficheros versionados.
3. El modelo SHALL poder configurarse con `--model`. Los valores por defecto son `gemini-3.8-flash` y `llama3.2`.
4. Ollama SHALL usar `OLLAMA_HOST`, por defecto `http://localhost:11434`.
5. IF el proveedor no está disponible (falta la clave, no hay conexión, HTTP 4xx/5xx o timeout) THEN el sistema SHALL fallar con código 2 y un mensaje accionable, sin generar contenido.
6. Las peticiones SHALL tener un timeout. La clave nunca SHALL aparecer en logs ni en mensajes de error.

### R3: Salida estructurada y validación
1. El sistema SHALL pedir salida estructurada con JSON Schema (`title`, `body`, `hashtags`).
2. IF la respuesta no cumple el esquema o el cuerpo está vacío THEN el sistema SHALL fallar sin mostrar la respuesta como válida.
3. Un post de LinkedIn SHALL tener como máximo 3000 caracteres. IF los supera THEN el sistema SHALL marcarlo como error.
4. El sistema SHALL detectar números y porcentajes del contenido generado que no aparezcan en los datos de origen y SHALL mostrarlos como **avisos de veracidad**.
5. Los datos del perfil SHALL enviarse al modelo como datos delimitados, con la instrucción de no seguir órdenes contenidas en ellos (mitigación de prompt injection).

### R4: Importador del export de LinkedIn
**User story:** Como usuario nuevo, quiero importar mi export de LinkedIn, para no escribir mi `profile.yaml` desde cero.

1. WHEN se ejecuta `proofstack import linkedin <export.zip> --out <profile.yaml>` THEN el sistema SHALL leer `Profile.csv`, `Positions.csv`, `Education.csv`, `Skills.csv` y `Projects.csv` (los que existan) y generar un `profile.yaml` válido.
2. El parser CSV SHALL soportar campos entre comillas con comas, comillas escapadas y saltos de línea.
3. Fechas: SHALL aceptar `Jan 2020`, `ene 2020` y `2020`. Un fin vacío se interpreta como "en curso".
4. Los datos que falten SHALL rellenarse con `TODO` y el sistema SHALL informar de cuántos hay.
5. Las skills SHALL asignarse a un área por palabras clave, con `backend` por defecto.
6. El ZIP SHALL leerse con límites (tamaño por entrada y número de entradas) y solo por nombre de entrada, sin extraer al disco.
7. IF el fichero de salida existe THEN el sistema SHALL negarse a sobrescribirlo salvo con `--force`.
8. El sistema SHALL NOT importar email ni teléfono: son datos personales que el usuario debe decidir publicar.

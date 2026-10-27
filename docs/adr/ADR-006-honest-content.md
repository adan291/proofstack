# ADR-006: Contenido honesto, sin fallbacks falsos y con avisos de veracidad

- Estado: aceptada
- Fecha: 2026-10

## Contexto

El prototipo inicial (LK-main, generado con Google AI Studio) devolvía resultados inventados cuando la IA fallaba, como "78 % de compatibilidad" o puntuaciones ATS fijas, sin avisar al usuario. Además, los LLMs tienden a inventar métricas ("reduje la latencia un 40 %"). En un portfolio cuyo lema es "pruebas, no promesas", las dos cosas son inaceptables.

## Decisión

1. **Sin fallbacks.** Si el proveedor no responde o lo que devuelve no cumple el esquema, el sistema lanza `ContentGenerationException` y la CLI sale con código 2 sin mostrar contenido.
2. **La respuesta del modelo se trata como entrada no confiable.** Se valida contra un JSON Schema (`title`, `body` y `hashtags`) y contra los límites de cada plataforma (un post de LinkedIn admite como máximo 3000 caracteres).
3. **Avisos de veracidad.** `GroundingCheck`, en el core, detecta números y porcentajes del texto generado que no aparecen en los datos de origen y los muestra como `WARNING`.
4. **Los datos del perfil van delimitados** en `<profile-data>`, con el cierre escapado y la instrucción explícita de no seguir órdenes que vengan dentro. Así se mitiga el prompt injection.
5. **Minimización de datos.** Al modelo no se le envían email ni ubicación. El importador de LinkedIn no importa email ni teléfono.

## Consecuencias

- El usuario siempre sabe si un texto es real, y los avisos le obligan a revisar las cifras antes de publicar.
- `GroundingCheck` es heurístico. No detecta afirmaciones inventadas sin números, así que la revisión humana sigue siendo necesaria y se documenta como tal.

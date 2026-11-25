# Requirements: un solo codec YAML

## Introducción

`profile.yaml` se escribe desde dos módulos: `proofstack-out-yaml` (`YamlProfileWriter`, que usan los importadores e `init`) y `proofstack-in-yaml` (`YamlProfileDocumentStore`, que usa Studio). Cada uno configura su propio `YAMLMapper`. Por eso el fallo de comillas de H1 hubo que corregirlo dos veces, y `TextQuoting` está copiado en los dos módulos, porque ArchUnit no deja que un adaptador use otro. La próxima regla de formato (por ejemplo, los años de la spec `proofstack-year-dates`) volvería a necesitar dos cambios iguales.

Fuera de alcance: cambiar el formato del fichero o la forma en que Studio conserva los comentarios.

## R1: Un solo sitio

1. La configuración de lectura y escritura de YAML (mapper, comillas, sangría, cabecera) SHALL existir en un único sitio del código.
2. `YamlProfileWriter` y `YamlProfileDocumentStore` SHALL producir el mismo YAML para el mismo documento.
3. La comprobación de ida y vuelta antes de escribir (H1) SHALL ser una sola, compartida por los dos.

## R2: Sin cambios para el usuario

1. Los ficheros escritos SHALL ser idénticos, byte a byte, a los de hoy para los perfiles de `profiles/` y de los tests.
2. Los comandos, sus opciones y su salida SHALL NOT cambiar.
3. El servicio RAG y el servidor MCP SHALL seguir funcionando con el mismo contenido de jar.

## R3: Arquitectura

1. ArchUnit SHALL seguir comprobando que ningún adaptador depende de otro, sin excepciones nuevas.
2. El core SHALL NOT cambiar.

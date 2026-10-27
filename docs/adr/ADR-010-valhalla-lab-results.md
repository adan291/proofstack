# ADR-010: Resultados del laboratorio de Valhalla y decisión sobre su adopción

- Estado: aceptada
- Fecha: 2026-10
- Relacionada con: ADR-003

## Contexto

JEP 401 (value classes) ya está integrado en JDK 28, en preview. Hemos medido `record` frente a `value record` en un módulo aislado (`labs/valhalla-lab`) con JDK 28-ea+18. Los resultados están en [`labs/valhalla-lab/RESULTS.md`](../../labs/valhalla-lab/RESULTS.md):
- **Lectura** de arrays: entre 2,1× y 4,4× más rápida.
- **Creación** en masa: sin mejora, y con un 40 % más de bytes asignados en nuestro caso. No he podido verificar la causa.

## Decisión

- **No adoptar** value classes en los módulos de producción mientras sean una feature en preview. Se mantiene ADR-003.
- **Candidatos** para cuando sean estables en un LTS, porque son inmutables, pequeños y se leen mucho: `Period`, `Skill`, `Link` y `KnowledgeChunk`.
- Antes de migrar cualquier tipo, repetir el benchmark en Linux, con 3 forks y con un tipo de un solo campo. Así se comprueba la hipótesis sobre la asignación en creación.

## Consecuencias

- El proyecto sigue en Java 25 LTS sin flags especiales.
- El laboratorio queda como documentación reproducible y como material para publicar, sin afectar al build principal.

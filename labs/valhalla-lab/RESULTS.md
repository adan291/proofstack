# Valhalla lab: `record` frente a `value record` (JEP 401)

## Entorno

| | |
|---|---|
| JDK | OpenJDK 28-ea+18-1391 (early access, `--enable-preview`) |
| CPU | AMD Ryzen 7 9800X3D (8C/16T), 32 GB RAM, Windows 11 |
| JMH | 1.37, 1 fork, 3×1 s de warmup y 5×1 s de medición, `-Xms1g -Xmx1g`, `-prof gc` |
| Tamaño | 1 000 000 elementos por array |

Datos en bruto: [`results-jdk28ea18.json`](results-jdk28ea18.json).

## Resultados

| Benchmark | Identity `record` | `value record` | Diferencia |
|---|---|---|---|
| `sum`: recorrer el array y sumar `x + y` | 1 099 ± 207 µs | **519 ± 79 µs** | **2,1× más rápido** |
| `spanMonths`: distancia entre meses consecutivos (caso tipo `Period`) | 3 606 ± 2 963 µs | **828 ± 237 µs** | **~4,4× más rápido** (con mucho error en identity) |
| `fill`: crear 1M objetos y guardarlos | **4 451 ± 831 µs** | 5 730 ± 2 421 µs | Sin ventaja (los intervalos se solapan) |
| `fill`, bytes asignados por op | **20,0 MB** (20 B/elemento) | 28,0 MB (28 B/elemento) | **+40 % con value** |

`LayoutCheck` confirma la semántica: `Objects.hasIdentity` vale `false` para el value record, `==` compara por valor (`true`) y `equals` coincide con `==`.

## Lectura

- **Leer es más rápido.** Recorrer arrays de value records fue entre 2 y 4 veces más rápido. Encaja con la promesa del JEP: sin identidad, el JVM puede aplanar los elementos y ahorrarse una indirección por elemento. Además, los arrays de identity se barajaron en el setup para que no se beneficiaran del orden de asignación.
- **Crear no mejoró, y asignó más memoria.** Con identity salen 20 B por elemento, que es lo esperable (objeto de 16 B más una referencia comprimida de 4 B). Con value salieron 28 B por elemento. **Todavía no sé por qué.** La hipótesis es que un array *nullable* de un value con 8 B de datos necesita además un marcador de null y no cabe en un acceso atómico de 64 bits. Si es así, el JVM tendría que usar otro layout o copias en el heap. `-XX:+PrintFlatArrayLayout` no imprimió nada en esta build, así que **no lo he verificado**.
- **Conclusión práctica para Proofstack:** compensa en estructuras inmutables que se leen mucho, como `Period`, `Skill` y los fragmentos del índice. No es una mejora automática para cualquier cosa que se crea en masa. Hay que medir.

## Salvedades

- Es una build **early access** y en **preview**. El rendimiento y el layout pueden cambiar antes de la GA (marzo de 2027).
- Es un microbenchmark sobre una sola máquina, con un solo fork, en Windows y con otras aplicaciones abiertas. Los errores de `spanMonthsIdentity` y `fillValue` son grandes.
- Ninguna feature en preview entra en los módulos de producción (ADR-003).

## Siguientes experimentos

1. Repetir con 3 forks y más iteraciones en Linux sin carga.
2. Probar un value record de un solo `int` (cabe junto con el marcador de null en 64 bits) para confirmar o descartar la hipótesis del layout.
3. Repetir cuando lleguen los tipos *null-restricted*, que van en un JEP posterior a 401.

## Reproducir

```bash
# JDK 28 EA: https://jdk.java.net/28/
JAVA_HOME=/path/to/jdk-28 mvn -f labs/valhalla-lab/pom.xml package
java --enable-preview -jar labs/valhalla-lab/target/benchmarks.jar -prof gc
java --enable-preview -cp labs/valhalla-lab/target/classes dev.proofstack.labs.valhalla.LayoutCheck
```

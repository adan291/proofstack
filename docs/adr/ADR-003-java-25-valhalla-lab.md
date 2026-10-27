# ADR-003 — Java 25 LTS, con Valhalla como laboratorio aislado

- Estado: aceptada
- Fecha: 2026-10

## Contexto

- Java 21 es el LTS anterior y Java 25 es el LTS actual.
- Project Valhalla (JEP 401, value classes) se integra como *preview* en JDK 28, con GA prevista para marzo de 2027. Esa versión no es LTS y exige `--enable-preview`.
- Spring Boot 4.x y Spring AI 2.0, que se usarán en fases posteriores, soportan Java 25.

## Decisión

- La base del proyecto compila con `maven.compiler.release=25`. El enforcer exige JDK 25 o superior.
- Valhalla se explorará en un módulo `proofstack-lab-valhalla` aislado en cuanto salga JDK 28. Los candidatos son records de dominio pequeños e inmutables como `Period` y `Skill`, medidos con benchmarks JMH.
- Ninguna feature en preview entra en los módulos de producción.

## Consecuencias

- La base es estable y compatible con el ecosistema Spring.
- El experimento con Valhalla da contenido técnico sin arriesgar la base.
- Cuando value classes sea estable en un LTS, se reevaluará esta decisión.

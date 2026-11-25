# Contribuir a Proofstack

Gracias por tu interés. Proofstack es pequeño a propósito y quiere seguir así.

## Antes de abrir un PR
1. Abre un issue si el cambio afecta al formato de `profile.yaml` o a la arquitectura. Esos cambios necesitan un ADR en `docs/adr/`.
2. `mvn verify` tiene que pasar con JDK 25. Ese comando ejecuta también las reglas de arquitectura (ArchUnit). Si una regla falla, la solución casi nunca es borrar la regla.
3. Todo cambio de comportamiento necesita tests. Los adaptadores HTTP se prueban contra servidores fake (`com.sun.net.httpserver`), nunca contra servicios reales.
4. Si tocas el editor de Studio (`adapters/proofstack-in-studio/src/main/resources/studio/`), pasa también los tests de navegador. Necesitan Chrome instalado; si no lo encuentran, se saltan con un aviso:
   ```bash
   mvn -Pe2e verify -pl proofstack-cli -am -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false
   ```
   Están en `proofstack-cli/src/e2e/java` (clases `*E2E`). Fallan también ante cualquier error de JavaScript, violación de CSP o respuesta HTTP de error que el test no espere.

## Reglas del proyecto
- **El core no depende de frameworks.** Solo `java.*`.
- **Los adaptadores no se conocen entre sí.** La raíz de composición (CLI o servicio) es la que los une.
- **Honestidad (ADR-006):** nada de fallbacks que inventen contenido, ni métricas que no salgan de los datos del usuario.
- **Privacidad:** no se importan, registran ni envían a un LLM datos que no hagan falta (email, teléfono, texto de las preguntas).
- **Dependencias con versión fijada.** Nada de rangos ni `LATEST`.

## Añadir un tema, un importador o un proveedor de IA
Implementa el puerto correspondiente (`PortfolioRenderer`, `ProfileImporter`/`ProjectSource` o `ContentGenerator`) en un módulo nuevo dentro de `adapters/`. Después conéctalo en `proofstack-cli` y añade el paquete a `HexagonalArchitectureTest`.

## Commits
Mensajes en imperativo y centrados en el porqué ("Bloquear el despliegue si hay placeholders"), no en el qué.

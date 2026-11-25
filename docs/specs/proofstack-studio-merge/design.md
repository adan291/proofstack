# Design: conflictos en Studio sin perder ediciones

## Decisiones

| Tema | Decisión | Alternativa descartada y motivo |
|---|---|---|
| Dónde se fusiona | En el servidor, adaptador `proofstack-in-studio`, sobre árboles JSON de Jackson | En `studio.js`: no hay tests de navegador todavía (ver la spec `proofstack-studio-browser-tests`), y la lógica de fusión es lo que más casos límite tiene. En el core: ADR-014 decide que el core no conoce JSON como tipo |
| Base de la fusión | El servidor guarda en memoria los documentos que sirvió en la sesión, por versión (como mucho los 20 últimos) | Que el navegador mande la base: un cliente podría inventar una base para colar cambios sin conflicto (R3.1) |
| Emparejado de listas | Por `id` en las listas cuyos elementos lo tienen en el esquema; listas sin `id`, como un valor | Diff por posición: reordenar un proyecto produciría conflictos en todos los demás |

No cambia ADR-014: el fichero solo se escribe con `replace` (validado, atómico, con `.bak` y versión). Se añade a ADR-014 una consecuencia: «un 409 se resuelve con una fusión revisada por el usuario».

## API

- `PUT /api/profile` sin cambios. Un 409 incluye ahora `baseKnown: true|false`.
- `POST /api/profile/merge`, cuerpo `{ "base": "<versión cargada>", "document": {…} }`. Responde:
  - `200 { "version": "<actual en disco>", "merged": {…}, "conflicts": [ { "path": "$.projects[id=billing].summary", "mine": …, "theirs": … } ] }`
  - `409` si la base no está entre las versiones servidas (R3.1).
- El cliente resuelve cada conflicto, sustituye el valor en `merged` y guarda con `PUT` e `If-Match` igual a `version`.

## Componentes

- `ThreeWayMerge` (paquete `adapter.in.studio`): función pura `merge(base, mine, theirs, idKeys)` → `Result(merged, conflicts)`. Las listas con `id` se sacan del esquema al arrancar (arrays de objetos con la propiedad `id` requerida).
- `ServedVersions`: mapa acotado de versión → JSON, que se rellena en cada `GET /api/profile` y en cada `Saved`.
- `studio.js`: diálogo de conflictos accesible (un `fieldset` por conflicto con dos radios), botón «Descargar mis cambios» y borrador en `sessionStorage`.

## Pruebas

- `ThreeWayMergeTest` con tabla de casos: sin cambios, un solo lado, los dos lados con el mismo valor, conflicto, elemento añadido o borrado en cada lado, reordenado, `id` cambiado y tipos distintos.
- `StudioServerTest`: base desconocida → 409; fusión y guardado posterior → 200; el fichero no cambia hasta el `PUT`.
- Navegador: cuando exista la spec de tests de navegador, el flujo completo del 409.

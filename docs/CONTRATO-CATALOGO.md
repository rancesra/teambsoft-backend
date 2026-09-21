# Contrato de Servicio — Catálogo (Equipo B)

**Versión:** 2.3 — Segunda entrega (Ciclo 2)
**Fecha:** 2026-09-21
**Equipo responsable:** Equipo B
**Consumido por:** Equipo A (Búsqueda), Equipo C (Carrito)

## 1. Propósito

Catálogo es la fuente de verdad de los productos de la tienda. Expone operaciones CRUD vía REST (a través de Kong) y publica eventos a RabbitMQ para que otros servicios se mantengan sincronizados sin tener que consultarlo en cada operación.

## 2. Endpoints REST

Se consumen a través del API Gateway (Kong). Base path propuesto: `/api/catalogo` (ajustable cuando se configure Kong).

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| GET | /productos | Lista productos activos, paginado, filtro opcional `?categoria=` | Pública |
| GET | /productos/{id} | Detalle de un producto | Pública |
| GET | /categorias | Lista las categorías válidas | Pública |
| POST | /productos | Crea un producto | Admin |
| PUT | /productos/{id} | Actualiza un producto | Admin |
| DELETE | /productos/{id} | Desactiva un producto (soft delete) | Admin |
| POST | /productos/{id}/activar | Reactiva un producto desactivado | Admin |
| POST | /productos/descontar-stock | Descuenta stock al confirmar una compra | Servicio |

> Identity Server (Keycloak) no está integrado en esta entrega. Los endpoints Admin no validan token real todavía — se documenta el contrato final para que Carro y Búsqueda ya sepan qué esperar cuando se conecte.

**GET /productos** → 200
```json
{
  "productos": [ { "...": "ver modelo Producto en sección 3" } ],
  "total": 42,
  "pagina": 1,
  "tamanoPagina": 20
}
```

Parámetros de consulta (todos opcionales, se pueden combinar):

| Parámetro | Por defecto | Reglas |
|---|---|---|
| `categoria` | todas | `id` de una categoría (ver `GET /categorias`) |
| `pagina` | 1 | entero ≥ 1 — la primera página es la 1 |
| `tamanoPagina` | 20 | entero entre 1 y 100 |
| `activo` | `true` | `true` (solo activos) · `false` (solo desactivados) · `todos` |

Ejemplo: `GET /productos?categoria=cat-ropa&pagina=2&tamanoPagina=20`

- `total` es la cantidad de productos que cumplen el filtro, no solo los de la página actual.
- **`activo` existe para la pantalla de administración**, que necesita poder encontrar lo que desactivó. Su valor por defecto es `true`, así que quien no lo envíe sigue recibiendo solo productos activos, exactamente como antes. El escaparate público no debe enviarlo nunca.
- Si `pagina` es mayor que la última página, responde 200 con `productos: []` y el mismo `total`.
- Si `categoria` no corresponde a ninguna categoría existente, responde 200 con `productos: []` y `total: 0` (un filtro sin resultados no es un error).
- Valores de `pagina` o `tamanoPagina` fuera de estas reglas → 400 (`VALIDACION_FALLIDA`).

**GET /productos/{id}** → 200 (Producto) · 404 (`PRODUCTO_NO_ENCONTRADO`)

Devuelve el producto aunque esté inactivo (viene con `activo: false`). El 404 es solo para un `id` que no existe.

**GET /categorias** → 200
```json
[
  { "id": "cat-ropa", "nombre": "ropa" },
  { "id": "cat-hogar", "nombre": "hogar" },
  { "id": "cat-electronica", "nombre": "electrónica" }
]
```

**POST /productos** → 201 (Producto creado, con `id` asignado) · 400 (`VALIDACION_FALLIDA`)

**PUT /productos/{id}** → 200 (Producto actualizado) · 404 · 400

Reemplaza el producto completo: el cuerpo lleva todos los campos, con las mismas reglas que `POST` (sección 3).
- No cambia `id` (se toma de la URL) ni `activo` (solo lo cambia `DELETE`); si vienen en el cuerpo, se ignoran.
- Sobre un producto inactivo responde 200 y el producto sigue inactivo.

**DELETE /productos/{id}** → 204 (marca `activo:false`, no borra el registro) · 404

Si el producto ya estaba inactivo, responde 204 sin cambiar nada ni publicar otro evento (la operación es idempotente).

**POST /productos/{id}/activar** → 200 (Producto reactivado) · 404 (`PRODUCTO_NO_ENCONTRADO`)

Vuelve a poner `activo: true`. Es la operación inversa de `DELETE` y no existía hasta v2.3: `PUT` ignora `activo` a propósito, así que sin este endpoint un producto desactivado no tenía camino de vuelta.

- Si el producto ya estaba activo, responde 200 sin cambiar nada ni publicar otro evento (idempotente, igual que `DELETE`).
- No toca ningún otro campo: para cambiar datos está `PUT`.

**POST /productos/descontar-stock** → 200 · 409 (`STOCK_INSUFICIENTE`) · 404 · 400

Descuenta stock de varios productos a la vez. Lo llama **Carro al confirmar el checkout**; no lo llama el navegador.

Cuerpo de la petición:
```json
{ "items": [ { "id": "64f1a2b3c9e77a001f3d8e21", "cantidad": 2 } ] }
```

Respuesta 200:
```json
{ "items": [ { "id": "64f1a2b3c9e77a001f3d8e21", "stockRestante": 118 } ] }
```

Reglas:
- Cada descuento se hace con una operación atómica de MongoDB condicionada al stock disponible, así que **dos checkouts simultáneos sobre el mismo producto no pueden pasar los dos**.
- Si algún ítem no alcanza, responde **409** (`STOCK_INSUFICIENTE`) con los ids afectados en el mensaje, y **revierte los descuentos que ya había hecho** en esa misma petición: o pasa todo, o no pasa nada.
- `cantidad` debe ser un entero ≥ 1, y el producto debe existir y estar activo; si no, 400 o 404.
- Publica `producto.actualizado` por cada producto cuyo stock cambió, para que el índice de Búsqueda no se desfase.

**Lo que este endpoint no es:** una reserva. No aparta stock mientras el usuario decide ni lo libera solo si abandona el carrito, y si el pedido falla después del descuento, nadie repone el stock automáticamente — ver sección 8.

## 3. Modelo de Producto

| Campo | Tipo | Descripción |
|---|---|---|
| id | string | Identificador único (generado por Mongo) |
| nombre | string | Requerido, máx. 120 caracteres |
| descripcion | string | Opcional |
| precio | number | Requerido, > 0 |
| categoria | string | Requerido, debe coincidir con un `id` de `GET /categorias` |
| stock | integer | Requerido, >= 0 |
| imagenes | array[string] | Opcional, URLs |
| activo | boolean | `true` al crearse; `false` tras un soft delete. Solo lo cambia `DELETE` |

Ejemplo:
```json
{
  "id": "64f1a2b3c9e77a001f3d8e21",
  "nombre": "Camiseta básica algodón",
  "descripcion": "Camiseta unisex 100% algodón, corte regular.",
  "precio": 49900,
  "categoria": "cat-ropa",
  "stock": 120,
  "imagenes": ["https://cdn.tienda.com/img/camiseta-1.jpg"],
  "activo": true
}
```

### Modelo de Categoría

| Campo | Tipo | Descripción |
|---|---|---|
| id | string | Identificador único, usado como valor de `Producto.categoria` |
| nombre | string | Nombre visible de la categoría |

En esta entrega las categorías vienen precargadas (seed) — ver sección 8.

## 4. Formato de error estándar

```json
{
  "codigo": "PRODUCTO_NO_ENCONTRADO",
  "mensaje": "descripción legible del error"
}
```

Códigos de esta entrega: `PRODUCTO_NO_ENCONTRADO` (404), `VALIDACION_FALLIDA` (400), `STOCK_INSUFICIENTE` (409).

`STOCK_INSUFICIENTE` es 409 y no 400 porque no es un error de formato de la petición, sino un conflicto con el estado actual del recurso: la misma petición puede fallar ahora y funcionar en un minuto. Se usa el mismo código y el mismo número que ya definió Carro en su contrato v1.1.

## 5. Eventos publicados (RabbitMQ)

Catálogo publica en un exchange de RabbitMQ de tipo **topic** llamado `catalogo.eventos`. Cada evento sale con su routing key; cada consumidor crea su propia cola y la enlaza al exchange con las routing keys que le interesan (por ejemplo, `producto.*` para recibirlos todos). El cuerpo del mensaje es un JSON con los campos del payload.

| Evento | Routing key | Se publica cuando | Payload mínimo |
|---|---|---|---|
| ProductoCreado | `producto.creado` | se crea un producto | id, nombre, **descripcion**, precio, categoria, **stock** |
| ProductoActualizado | `producto.actualizado` | se modifica cualquier campo, incluido el stock | id, nombre, **descripcion**, precio, categoria, **stock** |
| ProductoDesactivado | `producto.desactivado` | se hace soft delete (no se repite si el producto ya estaba inactivo) | id |
| **ProductoReactivado** | `producto.reactivado` | se reactiva un producto desactivado (no se repite si ya estaba activo) | id, nombre, descripcion, precio, categoria, stock |

**Cuántos consumidores puede haber:** los que hagan falta. Al ser un exchange *topic*, cada servicio crea su propia cola y recibe su propia copia de cada mensaje; que se sumen consumidores no afecta a los que ya estaban ni obliga a cambiar nada en Catálogo.

**Por qué `ProductoReactivado` es un evento aparte** y no un `ProductoActualizado` con `activo: true`: el payload de los eventos no incluye `activo`, así que un consumidor no podría distinguir una reactivación de una edición cualquiera. Con una routing key propia, quien necesite reaccionar se enlaza a ella y quien no, la ignora. Lleva el payload completo porque quien había sacado el producto de su índice necesita todos los datos para volver a meterlo.

## 6. Integración con Equipo A (Búsqueda)

Búsqueda **no debe** llamar a `GET /productos` para construir su índice. Debe suscribirse a los eventos de la sección 5 y mantener su propio índice de forma asíncrona.

**Resuelto en v2.3:** el contrato de Búsqueda v1.0 (sección 6) pide `descripcion` en el payload y lo marca como bloqueante para la búsqueda de texto completo. Desde v2.3, `ProductoCreado`, `ProductoActualizado` y `ProductoReactivado` la incluyen, junto con `stock`, que también pedían como dato informativo.

**Resuelto en v2.3:** también preguntaban si habría un evento de reactivación. Sí: `producto.reactivado` (sección 5).

## 7. Integración con Equipo C (Carrito)

Antes de agregar un producto al carrito, Carro debe llamar a `GET /productos/{id}` y validar:
- Que el producto existe (si no, 404 `PRODUCTO_NO_ENCONTRADO`)
- Que `activo == true`
- Usar el `precio` que devuelve Catálogo — nunca el que mande el frontend

**Resuelto en v2.3:** el contrato de Carrito v1.1 (secciones 2.1 y 8.A) responde qué pasa si un producto se desactiva estando en el carrito: lo marca como no disponible en vez de eliminarlo en silencio, para que sea el cliente quien decida quitarlo.

**Resuelto en v2.3 — suscripción a eventos:** Carro puede suscribirse a `catalogo.eventos` como un consumidor más (sección 5), y usar `producto.actualizado` y `producto.desactivado` para mantener su caché y reservar la llamada REST síncrona para el checkout.

**Resuelto en v2.3 — descuento de stock:** al confirmar el checkout, Carro llama a `POST /productos/descontar-stock` (sección 2) con todos los ítems en una sola petición. Ese endpoint es el que permite responder `STOCK_INSUFICIENTE` de forma confiable, que el contrato de Carrito v1.1 marcaba como bloqueante para su HU-09.

## 8. Fuera de alcance en esta entrega

- Autenticación real vía Keycloak
- Descuento y Órdenes (equipo aún sin asignar)
- Variantes de producto (talla/color con stock por variante)
- Filtros y ordenamiento avanzado (solo se filtra por categoría y por `activo` en esta entrega)
- **Reserva de stock con retención:** `POST /productos/descontar-stock` descuenta en el momento del checkout, pero no aparta unidades mientras el usuario decide ni las libera si abandona el carrito
- **Reposición automática de stock** si un pedido falla después del descuento: hoy no hay quién la dispare, porque el equipo de Órdenes no está asignado. Se asume el riesgo y se corrige a mano si ocurre
- **Atomicidad entre varios productos:** MongoDB garantiza la atomicidad por documento, y esta entrega corre un Mongo de un solo nodo, sin transacciones. Cuando un ítem falla, Catálogo revierte los descuentos ya hechos en esa petición, pero esa reversión es de mejor esfuerzo: si el servicio se cae justo en medio, puede quedar diferencia
- Gestión de categorías (`POST`/`PUT`/`DELETE /categorias`) — las categorías vienen precargadas por ahora, solo se listan

## 9. Frontend — módulo Catálogo

El módulo "Catálogo" del Cliente Web (dentro del Host App) consume estos endpoints directamente, a través de Kong:

| Vista | Endpoint que consume | Qué muestra |
|---|---|---|
| Listado de productos | `GET /productos` | Grid o lista: nombre, precio, imagen, categoría |
| Detalle de producto | `GET /productos/{id}` | Vista individual con toda la información |
| Crear producto (admin) | `POST /productos` | Formulario: nombre, descripción, precio, categoría, stock, imágenes |
| Selector de categoría | `GET /categorias` | Dropdown para el formulario de creación |
| Administración de productos | `POST`, `PUT`, `POST /{id}/activar`, `DELETE` | Listado interno, formulario de creación y edición, reactivar y desactivar |

**Framework:** Vue.js, acordado por los 3 equipos para todos los módulos del Host App, de modo que sean consistentes entre sí.

**El Host App lo construye el Equipo B.** Acordado por los 3 equipos el 2026-09-21. La superficie de integración —espacio de rutas de cada módulo, qué expone cada uno, qué entrega el cascarón y las variables de estilo compartidas— está en un documento aparte: [CONTRATO-HOSTAPP.md](https://github.com/rancesra/teambsoft-hostapp/blob/main/CONTRATO-HOSTAPP.md), en el repositorio [teambsoft-hostapp](https://github.com/rancesra/teambsoft-hostapp). Ese documento es el que tienen que leer los equipos A y C para que su módulo encaje.

Lo esencial para este contrato:

- El módulo de Catálogo vive bajo el prefijo `/catalogo/*` y no pinta nada fuera de él.
- **Cada módulo tiene que funcionar solo, sin el Host App.** Es lo que evita que un equipo quede bloqueado esperando a otro, y es el plan B si la integración falla el día de la sustentación.
- La integración se hace en dos etapas: primero composición por rutas, que es obligatoria; encima, Module Federation.

**Sigue pendiente de decidir:** dónde vive el botón “Agregar al carro” de la vista de detalle — si lo pinta Catálogo y emite un evento, o si el Host App monta ahí un componente del módulo de Carro. El módulo de Catálogo deja el espacio reservado mientras tanto.

No necesita ser un diseño elaborado: con que consuma los endpoints reales (nada de datos mockeados) y refleje los estados ya definidos (producto inactivo, error 404, etc.) alcanza para el nivel de esta entrega.

## 10. Historial de cambios

| Fecha | Cambio |
|---|---|
| 2026-08-20 | v1.0 — versión inicial para reunión con equipos A y C |
| 2026-08-20 | v1.1 — se agrega `GET /categorias` y modelo de Categoría |
| 2026-08-21 | v2.0 — se reasigna a Segunda entrega (Ciclo 2); se agrega sección de Frontend |
| 2026-09-10 | v2.1 — Aclaraciones compatibles con v2.0: parámetros de paginación de `GET /productos` (`pagina`, `tamanoPagina`); `?categoria=` inexistente devuelve lista vacía; `GET /productos/{id}` devuelve inactivos con `activo:false`; reglas de `PUT` (reemplazo completo, no cambia `id` ni `activo`) y de `DELETE` (idempotente); exchange `catalogo.eventos` y routing keys de los eventos; `stock` en el formulario de creación; variantes fuera de alcance |
| 2026-09-10 | v2.2 — §9: el frontend se construye con Vue.js, acordado por los 3 equipos; sigue pendiente el mecanismo de integración con el Host App |
| 2026-09-21 | v2.3 — Cambios compatibles con v2.2, a partir de la revisión cruzada con los contratos de Búsqueda v1.0 y Carrito v1.1. **Eventos:** se agregan `descripcion` y `stock` al payload (lo pedía Búsqueda como bloqueante) y se suma `producto.reactivado`. **Endpoints nuevos:** `POST /productos/{id}/activar` (no existía forma de reactivar) y `POST /productos/descontar-stock` (lo pedía Carrito como bloqueante para su checkout). **`GET /productos`:** nuevo parámetro `activo`, con `true` por defecto, para que la administración pueda encontrar lo que desactivó. **Errores:** se agrega `STOCK_INSUFICIENTE` (409), con el mismo nombre y código que ya usaba Carrito. **§9:** el Host App queda a cargo del Equipo B y su superficie de integración se documenta en `CONTRATO-HOSTAPP.md`. Se cierran los dos “pendiente de confirmar” de las secciones 6 y 7 |

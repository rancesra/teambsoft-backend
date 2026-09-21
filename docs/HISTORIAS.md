# Historias de Usuario — Catálogo (Equipo B)

**Entrega:** Primera entrega — Ciclo 1 (actualizado en Ciclo 2)
**Versión:** 1.2
**Fecha:** 2026-09-21
**Formato:** Como [rol], quiero [funcionalidad], para [beneficio] — con criterios Given/When/Then

## Historia 1 — Registrar producto
**Como** administrador, **quiero** registrar un producto con nombre, descripción, precio, categoría, stock e imágenes, **para** que esté disponible en el catálogo.

- Dado que soy administrador autenticado
- Cuando envío un producto con nombre, precio, categoría y stock válidos
- Entonces se guarda como activo y me devuelve el producto con su ID
- Y si falta un campo obligatorio o algún valor no cumple las reglas del contrato (precio > 0, stock ≥ 0, categoría existente, nombre de máx. 120 caracteres), devuelve error 400 (`VALIDACION_FALLIDA`)

## Historia 2 — Listar productos
**Como** cliente, **quiero** ver el listado de productos activos, **para** explorar qué puedo comprar.

- Dado que hay productos activos registrados
- Cuando consulto el listado
- Entonces recibo nombre, precio, imagen y categoría de cada uno
- Y los productos desactivados no aparecen
- Y puedo filtrar el listado por categoría
- Y el listado viene paginado (20 productos por página si no pido otro tamaño)

## Historia 3 — Ver detalle de un producto
**Como** cliente, **quiero** ver el detalle de un producto, **para** decidir si lo compro.

- Dado que el producto existe
- Cuando consulto su detalle por ID
- Entonces recibo toda su información (nombre, descripción, precio, categoría, stock, imágenes, activo)
- Y si el producto está desactivado, igual lo recibo, con `activo: false`
- Y si no existe, recibo error 404 (`PRODUCTO_NO_ENCONTRADO`)

## Historia 4 — Actualizar producto
**Como** administrador, **quiero** actualizar los datos de un producto (por ejemplo, su precio o stock), **para** mantener la información al día.

- Dado que soy administrador autenticado y el producto existe
- Cuando envío el producto completo con los valores nuevos
- Entonces el producto queda actualizado con esos valores
- Y si algún valor no cumple las reglas de validación, recibo error 400 (`VALIDACION_FALLIDA`)
- Y si el producto no existe, recibo error 404
- Y actualizarlo no cambia su estado: si estaba inactivo, sigue inactivo

## Historia 5 — Desactivar producto
**Como** administrador, **quiero** desactivar un producto que ya no se vende, **para** que no aparezca en el catálogo.

- Dado que soy administrador autenticado y el producto existe
- Cuando solicito desactivarlo
- Entonces queda marcado como inactivo y deja de aparecer en el listado
- Y el registro no se borra físicamente
- Y si ya estaba inactivo, la operación responde con éxito sin cambiar nada
- Y si el producto no existe, recibo error 404

## Historia 6 — Listar categorías
**Como** cliente o administrador, **quiero** ver las categorías disponibles, **para** filtrar productos o clasificar uno nuevo.

- Dado que hay categorías precargadas
- Cuando consulto el listado de categorías
- Entonces recibo su id y nombre

## Historia 7 — Reactivar producto
**Como** administrador, **quiero** volver a activar un producto que había desactivado, **para** ponerlo de nuevo a la venta sin tener que crearlo otra vez.

- Dado que soy administrador autenticado y el producto existe pero está inactivo
- Cuando solicito reactivarlo
- Entonces queda marcado como activo y vuelve a aparecer en el listado
- Y sus demás datos no cambian
- Y si ya estaba activo, la operación responde con éxito sin cambiar nada
- Y si el producto no existe, recibo error 404

## Historia 8 — Ver los productos desactivados
**Como** administrador, **quiero** ver también los productos que desactivé, **para** poder encontrarlos y reactivarlos.

- Dado que hay productos desactivados
- Cuando consulto el listado pidiendo los inactivos
- Entonces los recibo, paginados igual que los activos
- Y si no pido nada, el listado sigue devolviendo solo los activos, como antes

## Priorización sugerida para Ciclo 2

| Prioridad | Historias |
|---|---|
| Ciclo 2 (primer incremento funcional + frontend) | 1, 2, 3, 4, 5, 6, 7, 8 |
| Backlog (ciclo 3+) | — |

La Historia 3 entra en Ciclo 2 aunque no era la idea original, porque Carro depende de `GET /productos/{id}` para validar antes de agregar al carrito — sin ella, Equipo C no puede completar su parte. La Historia 6 es de bajo esfuerzo (las categorías vienen precargadas) y la necesita el formulario de creación del frontend.

Las Historias 4 y 5 estaban en el backlog (ciclo 3+) y se adelantaron al Ciclo 2 para entregar el CRUD completo que pide la segunda entrega.

Las Historias 7 y 8 nacen de la revisión cruzada con los otros dos equipos: al dibujar la pantalla de administración se vio que un producto desactivado no se podía ni encontrar ni recuperar, y el contrato de Búsqueda v1.0 preguntaba por un evento de reactivación que no existía. Son dos caras del mismo hueco.

**Descuento de stock al confirmar la compra** (`POST /productos/descontar-stock`, contrato §2) no se escribe como historia de Catálogo: no hay un usuario nuestro que la ejecute. Es un requisito técnico que sale de la HU-09 del contrato de Carrito v1.1, que sin él no puede responder `STOCK_INSUFICIENTE` de forma confiable.

## Historial de cambios

| Fecha | Cambio |
|---|---|
| 2026-08-21 | v1.0 — versión inicial (Primera entrega) |
| 2026-09-10 | v1.1 — Alineadas con el contrato v2.1: Historia 1 agrega `stock` y las reglas de validación; Historia 2 agrega filtro por categoría y paginación; Historia 3: un producto inactivo se devuelve con `activo: false` (antes decía 404); Historia 4: se envía el producto completo (`PUT`); Historia 5: agrega 404 y desactivación repetida sin error; Historias 4 y 5 pasan del backlog al Ciclo 2 |
| 2026-09-21 | v1.2 — Alineadas con el contrato v2.3: nuevas Historias 7 (reactivar producto) y 8 (ver los desactivados), las dos en Ciclo 2. Se anota por qué el descuento de stock no se escribe como historia |

# Evaluación del Transactional Outbox — Catálogo (Equipo B)

**Tarea:** S3-R2 · **Responsable:** Rances Ramírez · **Versión:** 1.0 · 27 de octubre de 2026
**Decide:** Hector Franco (Scrum Master del Sprint 3)

## 1. El problema y la evidencia

Cada escritura de Catálogo hace dos cosas: guarda el producto en MongoDB y después publica el evento en RabbitMQ (`ProductoService` → `PublicadorEventos`). No hay una transacción que cubra las dos. Si el guardado sale bien y la publicación falla, MongoDB y los consumidores quedan contando historias distintas.

### 1.1 Cómo se reprodujo

El 27 de octubre, sobre `main` (commit `63e6ee7`), con el servicio corriendo contra MongoDB y RabbitMQ:

1. Se creó una cola `busqueda.simulada`, enlazada a `catalogo.eventos` con `producto.*`, que hace el papel del Equipo A.
2. Con RabbitMQ encendido se crearon dos productos. La cola recibió **2 mensajes**.
3. Se apagó solo RabbitMQ y se hicieron tres operaciones.
4. Se encendió RabbitMQ y se volvió a revisar la cola.

### 1.2 Lo que se observó

| Operación con RabbitMQ apagado | Respuesta HTTP | Qué quedó en MongoDB | Evento |
|---|---|---|---|
| `POST /productos` | **500**, con el formato de error por defecto de Spring, no el del contrato | El producto **sí quedó guardado** y activo | Perdido |
| `DELETE /productos/{id}` | **500** | El producto **sí quedó desactivado** (`activo: false`) | Perdido |
| `POST /productos/descontar-stock` (2 unidades) | **500** | El stock **sí bajó**, de 5 a 3 | Perdido |

El log de catálogo, en las tres:

```
ERROR ... threw exception [Request processing failed:
org.springframework.amqp.AmqpConnectException: java.net.ConnectException: Connection refused]
```

Al encender RabbitMQ de nuevo, la cola seguía con **2 mensajes**: **los eventos perdidos no se recuperan solos**. Y al repetir el `DELETE`, el servicio respondió **204 sin publicar nada**, porque el producto ya estaba inactivo y la operación es idempotente. Ese evento ya no sale nunca.

### 1.3 Qué significa para el sistema

El problema no es solo que Búsqueda se desactualice. **El 500 es engañoso**, porque el cambio sí ocurrió:

- **Descontar stock:** Carro recibe un 500 y cree que el checkout falló, pero el stock ya se descontó. Si reintenta, **se descuenta dos veces**. Es el caso más grave, porque daña el inventario.
- **Crear:** el administrador ve un error y vuelve a crear el producto. Queda **duplicado**, y Búsqueda solo conoce uno de los dos, o ninguno.
- **Desactivar:** Búsqueda **sigue mostrando un producto retirado**, y reintentar no lo arregla.

## 2. Las opciones

Hay una restricción que pesa en todas: **MongoDB 7.0 en modo standalone no admite transacciones de varios documentos.** Necesitan un *replica set*, aunque sea de un solo nodo, y Spring Data MongoDB además exige declarar un `MongoTransactionManager`. Hoy, `docker-compose.yml` levanta Mongo en modo standalone.

| Opción | Cómo funciona | Qué cuesta en este código |
|---|---|---|
| **A. Dejar como está** | Se acepta el riesgo. Si algo se pierde, se reindexa a mano | Nada. El 500 engañoso y el doble descuento siguen |
| **A+. No convertir la falla del bus en un 500** | `PublicadorEventos` atrapa la falla de RabbitMQ, la registra en el log como `ERROR` con la routing key y el id, y el servicio responde lo que de verdad pasó (201, 204 o 200) | Un `try/catch` en un método privado de `PublicadorEventos`. No toca `ProductoService` ni la infraestructura. El evento se sigue perdiendo, pero queda en el log y se acaban el doble descuento y los duplicados |
| **B. Outbox en otra colección, con transacción** | `productos` y `eventos_pendientes` se escriben en una misma transacción; una tarea programada (`@Scheduled`) publica los pendientes y los marca como enviados | Pasar Mongo a replica set de un nodo (cambia `docker-compose.yml`, que es de Jhon), declarar `MongoTransactionManager`, marcar con `@Transactional` los cinco métodos que escriben, una colección nueva y la tarea de publicación |
| **C. Outbox dentro del mismo documento** | El producto lleva una lista `eventosPendientes`. Guardar el cambio y su evento es **una sola escritura sobre un documento**, que MongoDB ya garantiza atómica sin transacciones. Una tarea programada publica y saca los pendientes | Cambiar la entidad `Producto` (un campo y dos métodos con nombre), que los cinco métodos agreguen el evento en vez de publicarlo, la tarea de publicación y un método de limpieza en el repositorio. `descontar` ya es un `$inc` condicionado: se le suma un `$push` del evento en la misma operación |
| **D. CDC (Debezium o change streams)** | Un componente lee el registro de cambios de Mongo y publica | Debezium necesita Kafka Connect, que **no está en las tecnologías permitidas**. Los change streams también exigen replica set. Demasiado para el proyecto |

## 3. Criterios

Puntuación de 1 a 3, donde 3 es lo mejor en cada criterio:

| Criterio | A | A+ | B | C | D |
|---|---|---|---|---|---|
| Garantía de que el evento sale | 1 | 1 | 3 | 3 | 3 |
| Respuesta HTTP correcta para quien llama | 1 | 3 | 3 | 3 | 3 |
| Poco código cambiado | 3 | 3 | 1 | 2 | 1 |
| Poco riesgo al tocar `ProductoService` | 3 | 3 | 1 | 2 | 2 |
| Sin cambiar infraestructura | 3 | 3 | 1 | 3 | 1 |
| Tiempo (cabe en este sprint) | 3 | 3 | 1 | 1 | 1 |
| Fácil de probar con la prueba de S3-R1 | 3 | 3 | 2 | 2 | 1 |
| **Total** | **17** | **19** | **12** | **16** | **12** |

C es la mejor opción que de verdad garantiza el evento: logra lo mismo que B **sin replica set**, y sigue la decisión que el proyecto ya tomó para el stock: atomicidad por documento en vez de transacciones (arquitectura, decisiones de diseño).

## 4. Efectos para los otros equipos

Con cualquier Outbox (B, C o D), la entrega pasa a ser **al menos una vez**. Si el proceso publica un evento y se cae antes de marcarlo como enviado, al volver lo publica **otra vez**. Además, los eventos salen unos segundos después del cambio, no en el mismo instante.

Con C, el orden **dentro de un mismo producto** se conserva, porque los pendientes de cada producto están en una lista ordenada. El orden **entre productos distintos** no está garantizado, pero ningún consumidor lo necesita.

**Preguntas para los equipos A y C**, antes de implementar B, C o D:

1. ¿Su consumidor tolera recibir **el mismo evento dos veces**? Por ejemplo, reindexar o actualizar en caché el mismo producto con los mismos datos.
2. ¿Les afecta que el evento llegue unos segundos después del cambio, en vez de al instante?

Con A y A+ no cambia nada para ellos.

## 5. Recomendación

1. **En este sprint: A+.** Es un cambio de menos de una hora, solo en `PublicadorEventos`, y elimina lo más grave que mostró la evidencia: el doble descuento de stock y los productos duplicados por reintentos. El evento perdido queda en el log con su id, para reindexar a mano.
2. **En el siguiente sprint: C**, si los equipos A y C confirman que toleran duplicados. Estimado: **8 a 12 horas**, incluida la prueba nueva (sección 6). No entra en este sprint: la entrega es el jueves 29 y la guía exige al menos dos días de trabajo.
3. **No B**, salvo que el equipo decida pasar Mongo a replica set por otra razón. En ese caso, B y C cuestan parecido.
4. **No D**: necesita herramientas que no están permitidas.

A+ no reemplaza al Outbox: los eventos se siguen perdiendo, solo que ya no engañan a quien llama. Es una mitigación, no la solución.

## 6. Qué pasa con la prueba de S3-R1 en cada caso

`PublicacionEventosTest` crea una cola, hace la operación y espera el mensaje hasta 5 segundos.

| Opción | Efecto en la prueba |
|---|---|
| A | Ninguno |
| A+ | Ninguno. Se le puede sumar un caso: con la publicación fallando, el servicio responde bien y no lanza la excepción |
| B y C | Sigue pasando si la tarea programada corre con un intervalo menor de 5 segundos (por ejemplo, 1). Las pruebas de «no publica dos veces» siguen sirviendo tal cual. Hay que sumar la prueba que pide la guía: guardar con RabbitMQ apagado, encenderlo y comprobar que el evento sale |
| D | Igual que B y C, pero dependiendo de un componente externo, más difícil de levantar en la prueba |

## 7. Decisión

| Fecha | Quién | Decisión |
|---|---|---|
| | Hector Franco | *Pendiente* |

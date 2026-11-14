# Búsqueda en la Tienda Virtual: lo que falta para el 17 de noviembre

**De:** Equipo B (Catálogo y Host App) · **Para:** Equipo A (Búsqueda) · **Fecha:** 13 de noviembre de 2026\
**Plazo:** los cambios en `main` **el lunes 16 de noviembre**, para probar todo junto antes de la entrega del martes 17.

## En resumen

Hoy **a Búsqueda no le llega ningún evento de Catálogo**: escucha un exchange y unas routing keys que no son las del contrato. Por eso, en la tienda integrada, la búsqueda sale vacía. Además, su frontend:

- choca de puerto con Catálogo;
- llama a su backend sin pasar por Kong;
- muestra un segundo encabezado dentro del Host App.

**Todo está hecho y probado en un parche** ([`busqueda-integracion.patch`](busqueda-integracion.patch), adjunto), sobre su `main` actual (`61cbb68`, 5 de noviembre). Ustedes lo aplican, lo revisan y lo suben como un cambio suyo. Nosotros no tocamos su repositorio.

**Lo que tienen que hacer:**

1. **Aplicar el parche** sobre `main`, con los comandos de la sección 3.
2. **Correr sus pruebas:** deben dar **50 en verde**.
3. **Subir a `main` el lunes.** El `docker-compose` de integración construye Búsqueda desde `main`.

**El login no les cambia nada:** Búsqueda es pública y sus endpoints siguen sin pedir token.

## 1. Cómo se ve Búsqueda en la tienda

El Host App (`http://localhost:5050`) es el cascarón. Tiene un menú **Catálogo · Búsqueda · Carro · Órdenes** y carga cada módulo en un iframe. **Búsqueda** es su frontend, en el puerto `5174`, y su backend responde por Kong en `http://localhost:8000/api/busqueda`.

**Su parte en el recorrido de la presentación:**

1. En **Búsqueda**, el usuario escribe «camiseta» y aparece la camiseta con su precio.
2. La pulsa y el Host App abre su detalle en **Catálogo**, donde puede agregarla al carro.
3. Cuando alguien compra, el stock baja en Catálogo y Búsqueda se entera por el evento.
4. Si el administrador desactiva un producto, desaparece de la búsqueda; si lo reactiva, vuelve a aparecer.

## 2. Qué cambia el parche y por qué

| # | Cambio | Por qué |
|---|---|---|
| A1 | Exchange **`catalogo.eventos`** (hoy `catalogo.events`) y routing keys **`producto.creado`, `producto.actualizado`, `producto.desactivado` y `producto.reactivado`** (hoy `catalogo.producto.*`)<br><small>`src/infrastructure/rabbitmq/catalogoConsumer.js`</small> | Son los nombres del contrato de Catálogo desde la v2.1. Con los actuales, su cola recibe 0 mensajes |
| A2 | Aplica `producto.reactivado` y **toma `activo` del evento** en vez de poner siempre `activo: true`<br><small>`src/infrastructure/rabbitmq/catalogoConsumer.js`</small> | Si un administrador edita un producto desactivado, Catálogo publica `actualizado` con `activo: false`. Hoy ese producto volvería a salir en la búsqueda |
| A3 | Un producto **creado o reactivado borra las búsquedas guardadas en Redis**. Actualizar y desactivar siguen borrando solo las búsquedas donde aparecía el producto<br><small>`src/infrastructure/cache/cacheBusqueda.js` (función nueva `invalidarTodaLaBusqueda`)</small> | Lo encontramos al probar: si alguien buscó «teclado» cuando estaba desactivado, Redis guardó «0 resultados» y, al reactivarlo, siguió saliendo vacío hasta 5 minutos. Esa búsqueda no estaba asociada al producto, así que la invalidación por producto no la borraba |
| A4 | Frontend en el **puerto `5174`** con `strictPort: true`<br><small>`busqueda-frontend/vite.config.js`</small> | Hoy Vite toma el `5173`, que es de Catálogo |
| A5 | Frontend llama **por Kong**: `http://localhost:8000/api/busqueda`. Con `VITE_API_URL` se puede apuntar a otro lado, por ejemplo al `3000` sin Kong<br><small>`busqueda-frontend/src/composables/useBusqueda.js`</small> | Kong es el API Gateway de la tienda. En el `docker-compose` de integración el `3000` no está publicado |
| A6 | **Oculta su `<h1>`** «Búsqueda de productos» cuando corre dentro del Host App<br><small>`busqueda-frontend/src/App.vue`</small> | El encabezado ya lo pone el Host App. Si no se oculta, se ven dos |
| A7 | Al pulsar un resultado, le pide al Host App que abra **su detalle en Catálogo**, con `postMessage({ tipo: 'navegar', ruta: '/catalogo/' + id })`<br><small>`busqueda-frontend/src/App.vue`</small> | Une la búsqueda con la compra: buscar → ver el producto → agregar al carro |
| — | 8 pruebas nuevas: exchange, routing keys, `activo` del evento y cuándo se borra la caché. 1 prueba existente pasa a la routing key nueva<br><small>`tests/catalogoConsumer.test.js` (nuevo), `tests/cacheInvalidacion.test.js`</small> |  |

Sin el Host App alrededor, el frontend funciona igual que hoy. Lo único distinto es que llama por Kong.

## 3. Cómo aplicarlo

Los comandos son iguales en PowerShell:

```bash
git switch main
git pull
git switch -c integracion-host-app
git apply busqueda-integracion.patch
docker compose up -d elasticsearch redis
node scripts/migrarIndice.js
npm run seed
npm test
```

Después: abrir un PR hacia `main`, revisarlo y unirlo.

**Qué comprobamos nosotros con el parche aplicado:**

- `npm test`: **50 de 50 en verde**, contra Elasticsearch y Redis con el seed de 10 productos. Sin el parche, sus 42 pruebas también pasan; no se rompió ninguna.
- `npm run build` de `busqueda-frontend` compila.
- Con el `docker-compose` de integración: todo lo de la tabla de la sección 4, más el clic en un resultado dentro del Host App, que abrió `/catalogo/674000000000000000000001`.

## 4. Cómo comprobar que quedó

Con el `docker-compose` de integración arriba (sección 6):

| Prueba | Resultado esperado |
|---|---|
| Consola de RabbitMQ `http://localhost:15672` → *Queues* → `busqueda.catalogo.eventos` → *Bindings* | Enlazada a `catalogo.eventos` con las 4 routing keys `producto.*` |
| La misma cola, al arrancar | **6 mensajes** procesados: los 6 productos de prueba |
| `curl "http://localhost:8000/api/busqueda/productos?q=camiseta"` | `total: 1`, Camiseta básica algodón, `stock: 100` |
| `curl "http://localhost:8000/api/busqueda/productos?q=teclado"` | `total: 0`: el teclado (`…006`) está desactivado |
| Reactivar el teclado: `POST http://localhost:8000/api/catalogo/productos/674000000000000000000006/activar` con token de `admin` | Buscar «teclado» → `total: 1` **de inmediato** |
| Desactivarlo: `DELETE` al mismo producto, con token de `admin` | Buscar «teclado» → `total: 0` |
| Una compra en Carrito, o `POST /api/catalogo/productos/descontar-stock` | La camiseta sale en Búsqueda con el stock nuevo |
| `npm run dev` en `busqueda-frontend` | `Local: http://localhost:5174/` |
| `http://localhost:5050/busqueda` | Sin el título «Búsqueda de productos». Buscar «camiseta» y pulsarla abre su detalle en Catálogo |

## 5. Lo que manda Catálogo

**Exchange** `catalogo.eventos`: tipo `topic`, `durable: true`, `autoDelete: false`. **El cuerpo de cada mensaje es el JSON plano, sin envoltorio.**

| Routing key | Cuándo | Payload |
|---|---|---|
| `producto.creado` | Se crea un producto | `id, nombre, descripcion, precio, categoria, stock, activo, fecha` |
| `producto.actualizado` | Cambia cualquier campo, **incluido el stock** de una compra. **No implica que esté activo** | El mismo |
| `producto.reactivado` | Se reactiva un producto desactivado | El mismo |
| `producto.desactivado` | Se desactiva (soft delete) | `id, fecha` |

```json
{ "id": "674000000000000000000001", "nombre": "Camiseta básica algodón",
  "descripcion": "Camiseta unisex 100% algodón.", "precio": 49900, "categoria": "cat-ropa",
  "stock": 100, "activo": true, "fecha": "2026-11-13T15:30:00Z" }
```

Si se pierden eventos, el índice se reconstruye con `GET http://localhost:8000/api/catalogo/productos?activo=todos`. Eso es solo para recuperarse, no la forma normal de sincronizar (contrato de Catálogo, sección 5.1).

## 6. Cómo levantar la tienda completa

En [teambsoft-hostapp/integracion](https://github.com/rancesra/teambsoft-hostapp/tree/main/integracion), con el `.env` lleno (las claves se comparten por privado):

```bash
docker compose up -d --build
```

Ese `docker-compose` levanta, en orden:

1. **un solo RabbitMQ**;
2. su Elasticsearch y su Redis;
3. su backend;
4. Catálogo, que arranca después que Búsqueda, para que su cola ya esté enlazada cuando salgan los 6 eventos iniciales.

Después, cuatro terminales con `npm run dev`:

- `teambsoft-hostapp`;
- `teambsoft-frontend`, con `KONG_URL=http://localhost:8000`. En PowerShell: `$env:KONG_URL="http://localhost:8000"; npm run dev`;
- su `busqueda-frontend`;
- el `Frontend` de Carrito.

## 7. Datos fijos

| Qué | Valor |
|---|---|
| Puertos de los frontends | Host App `5050`, Catálogo `5173`, **Búsqueda `5174`**, Carrito `5175` |
| API por Kong | `http://localhost:8000/api/busqueda`. Kong no quita el prefijo, así que sus rutas no cambian |
| Cola | `busqueda.catalogo.eventos`, con su DLQ como la tienen hoy |
| Mensaje para navegar | `window.parent.postMessage({ tipo: 'navegar', ruta: '/catalogo/{id}' }, '*')` |
| Productos de prueba | `674000000000000000000001` a `…006`. El `…006` (teclado) está desactivado |
| Token de `admin` para probar | `POST http://localhost:8180/realms/tienda/protocol/openid-connect/token` con `grant_type=password`, `client_id=tienda-pruebas`, `username=admin`, `password=admin123` |

## 8. Qué cubre Búsqueda de lo que evalúa el profesor

| Criterio | Cómo lo cumple Búsqueda |
|---|---|
| Base de datos por servicio | Elasticsearch (índice) y Redis (caché), solo suyas. Nunca leen la base de Catálogo |
| Comunicación por eventos | Se enteran de cada cambio de Catálogo por RabbitMQ, sin llamarlo |
| Contrato entre equipos | Exchange, routing keys y payload del contrato de Catálogo v2.5, sección 5 |
| API Gateway | El frontend llama por Kong, en `/api/busqueda` |
| Micro-frontend | Su frontend, en su puerto, dentro del Host App, y conectado con Catálogo por `navegar` |
| Docker | Su backend, Elasticsearch y Redis en el `docker-compose` de integración |

## 9. Agregado el 14 de noviembre: registro en Eureka

El diagrama del profesor tiene a **todos los servicios registrados en Eureka**, el registro de servicios. Ya está en el `docker-compose` de integración (consola en `http://localhost:8761`) y Catálogo ya se registra. Para que también aparezca Búsqueda, hay un **segundo parche, pequeño e independiente**: [`busqueda-eureka.patch`](busqueda-eureka.patch).

| Qué hace | Detalle |
|---|---|
| Se registra como `BUSQUEDA` | Con su IP y su puerto dentro de Docker. Usa la API REST de Eureka, **sin librerías nuevas** |
| Mantiene el registro | Un latido cada 10 segundos. Si Eureka se reinicia, se vuelve a registrar solo |
| Publica su estado | `UP` o `DOWN` según un ping a su Elasticsearch: si se cae, aparece `DOWN` |
| Se despide al apagarse | Sale de la lista al instante |
| Sin `EUREKA_URL` no hace nada | Sus pruebas y el desarrollo sin Docker no cambian. El `docker-compose` de integración ya la pasa |

Archivos: `src/infrastructure/eureka/registroEureka.js` (nuevo), unas líneas en `src/index.js` y `tests/registroEureka.test.js` (6 pruebas nuevas, contra un Eureka falso).

**Cómo aplicarlo**, en la misma rama del primer parche:

```bash
git apply busqueda-eureka.patch
npm test
```

Se aplica limpio con o sin el primer parche.

**Qué comprobamos:** **56 de 56 en verde** (las 50 de antes y 6 nuevas). Con la tienda completa, los tres servicios aparecen en `UP` en `http://localhost:8761`. También probamos que pasa a `DOWN` si se cae su base de datos, que vuelve a `UP` cuando vuelve, y que sale de la lista al apagarse.


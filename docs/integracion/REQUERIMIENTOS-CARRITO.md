# Carrito en la Tienda Virtual: lo que falta para el 17 de noviembre

**De:** Equipo B (Catálogo y Host App) · **Para:** Equipo C (Carrito) · **Fecha:** 13 de noviembre de 2026\
**Plazo:** los cambios en `develop` **el lunes 16 de noviembre**, para probar todo junto antes de la entrega del martes 17.

## En resumen

Carrito ya funciona solo. Lo que falta es que funcione **dentro de la tienda**, es decir:

- dentro del Host App;
- con el usuario que inició sesión en Keycloak;
- con la entrada **«Órdenes»** del menú.

**Todo está hecho y probado en un parche** ([`carrito-integracion.patch`](carrito-integracion.patch), adjunto). Ustedes lo aplican, lo revisan y lo suben como un cambio suyo. Nosotros no tocamos su repositorio.

**Lo que tienen que hacer:**

1. **Unir su PR #51** (`feature/SCRUM-28-frontend-historial`) a `develop`. Ese PR trae la pantalla de pedidos, que es la que muestra «Órdenes».
2. **Aplicar el parche** sobre `develop`, con los comandos de la sección 3.
3. **Corregir `e.mensagem`** en `Frontend/src/stores/cartStore.js`, línea 40: debe decir `e.mensaje`. Con el error tipográfico, todo error del backend se muestra como «Ocurrió un error inesperado».
4. **Subir a `develop` el lunes.** El `docker-compose` de integración construye Carrito desde `develop`.

## 1. Cómo se ve Carrito en la tienda

El Host App (`http://localhost:5050`) es el cascarón. Tiene un menú **Catálogo · Búsqueda · Carro · Órdenes** y carga cada módulo en un iframe. **Carro** y **Órdenes** son el frontend de Carrito, en el puerto `5175`:

| Menú del Host App | Qué carga | Qué debe mostrar Carrito |
|---|---|---|
| **Carro** | `http://localhost:5175/carrito` | El carrito del usuario que inició sesión |
| **Órdenes** | `http://localhost:5175/carrito/pedidos` | «Mis pedidos» de ese usuario, abierto directamente |
| «Agregar al carro» en Catálogo | `http://localhost:5175/carrito?agregar={id}` | Agrega una unidad y muestra el carrito |

**El recorrido de la presentación** (lo probamos completo con el parche el 13 de noviembre):

1. El usuario abre la tienda y pulsa **Iniciar sesión**. Keycloak le pide usuario y clave, y lo devuelve con su nombre en el encabezado.
2. En **Catálogo** abre la camiseta y pulsa **Agregar al carro**.
3. Carrito agrega una unidad **a nombre de ese usuario** y muestra el carrito.
4. El usuario paga. Carrito descuenta el stock en Catálogo con su `checkoutId` como `referencia`, y el stock baja de 100 a 99.
5. En **Órdenes** aparece el pedido de ese mismo usuario.

## 2. Qué cambia el parche y por qué

| # | Cambio | Por qué |
|---|---|---|
| C1 | Frontend en el **puerto `5175`** con `strictPort: true`<br><small>`Frontend/vite.config.js`</small> | El `5173` es de Catálogo y el `5174` de Búsqueda. Los tres corren a la vez |
| C2 | Si la dirección trae **`?agregar={id}`**: agrega una unidad (`POST /api/carrito/items`), quita el parámetro y muestra el carrito<br><small>`Frontend/src/App.vue`, `Frontend/src/hostApp.js`</small> | Es el botón «Agregar al carro» de Catálogo |
| C3 | Si la dirección es **`/carrito/pedidos`**: abre «Mis pedidos» directamente<br><small>`Frontend/src/App.vue`, `Frontend/src/hostApp.js`</small> | Es la entrada **«Órdenes»** del menú |
| C4 | **Recibe la sesión del Host App** y usa ese token en todas las llamadas. Si no hay sesión, muestra un aviso con el botón **Iniciar sesión**, que lleva al login del Host App<br><small>`Frontend/src/hostApp.js` (nuevo), `Frontend/src/main.js`, `Frontend/src/App.vue`</small> | Quien compra y quien ve sus órdenes es el usuario real, no el token de prueba |
| C5 | El backend **acepta el token de Keycloak** (RS256) **y sigue aceptando** el de prueba (HS256 con `JWT_SECRET`)<br><small>`src/middlewares/authJwt.js`, `src/config/env.js`</small> | El token del login lo firma Keycloak. Sus pruebas actuales no cambian |
| C6 | **Oculta su encabezado** «Demo Carrito de Compras» cuando corre dentro del Host App<br><small>`Frontend/src/App.vue`</small> | El encabezado ya lo pone el Host App. Si no se oculta, se ven dos |
| — | 5 pruebas nuevas del token de Keycloak: válido, token de prueba, otro emisor, otra llave y vencido. Generan sus propias llaves, así que no necesitan Keycloak<br><small>`tests/authKeycloak.test.js` (nuevo)</small> |  |

Sin el Host App alrededor, el frontend funciona igual que hoy. Sin `KEYCLOAK_LLAVES`, el backend también.

## 3. Cómo aplicarlo

Después de unir el PR #51 (los comandos son iguales en PowerShell):

```bash
git switch develop
git pull
git switch -c integracion-host-app
git apply carrito-integracion.patch
npm test
```

Después: abrir un PR hacia `develop`, revisarlo y unirlo.

**Qué comprobamos nosotros con el parche aplicado:**

- `npm test`: **198 pruebas en verde** (172 de Jest, con las 5 nuevas, y 26 de `node --test`).
- `npm run build` del `Frontend` compila.
- El recorrido de la sección 1 completo, en el navegador, con los tres backends, Kong y Keycloak.

## 4. Cómo comprobar que quedó

Con el `docker-compose` de integración arriba (sección 6):

| Prueba | Resultado esperado |
|---|---|
| `npm run dev` en `Frontend` | `Local: http://localhost:5175/` |
| Abrir `http://localhost:5050/carrito` **sin** iniciar sesión | Sin el título «Demo Carrito de Compras», y un aviso con **Iniciar sesión** |
| Iniciar sesión como `cliente` / `cliente123` y abrir **Carro** | El carrito vacío de `cliente`, sin aviso |
| Catálogo → Camiseta → **Agregar al carro** | Se abre Carro con 1 camiseta. Al recargar la página no se agrega otra |
| Pagar | Pedido creado. `GET http://localhost:8000/api/catalogo/productos/674000000000000000000001` muestra `stock: 99` |
| **Órdenes** | «Mis pedidos» con ese pedido |
| `curl -H "Authorization: Bearer <token de Keycloak>" http://localhost:8000/api/carrito` | `200`, con el `sub` del usuario. Sin token: `401` |

## 5. Datos fijos

| Qué | Valor |
|---|---|
| Puertos de los frontends | Host App `5050`, Catálogo `5173`, Búsqueda `5174`, **Carrito `5175`** |
| API por Kong | `http://localhost:8000/api/carrito` (Kong no quita el prefijo) |
| Origen del Host App (del único que se acepta la sesión) | `http://localhost:5050` |
| Mensajes del Host App → Carrito | `{ tipo: 'sesion', token, usuario }`. `token: null` si no hay sesión. Se reenvía cada vez que se renueva (~4 min) |
| Mensajes de Carrito → Host App | `{ tipo: 'pedir-sesion' }` y `{ tipo: 'iniciar-sesion' }`, al origen `http://localhost:5050` |
| Emisor del token (`iss`) | `http://localhost:8180/realms/tienda` |
| Llaves públicas (dentro de Docker) | `http://keycloak:8080/realms/tienda/protocol/openid-connect/certs` |
| Variables del backend | `KEYCLOAK_LLAVES` y `KEYCLOAK_EMISOR`. El `docker-compose` de integración ya las pasa |
| Usuario en el token | Claim `sub` (UUID), igual que hoy |
| Usuarios de prueba | `cliente` / `cliente123` y `admin` / `admin123` |
| Token para Postman | `POST http://localhost:8180/realms/tienda/protocol/openid-connect/token` con `grant_type=password`, `client_id=tienda-pruebas`, `username`, `password` |
| Productos de prueba | `674000000000000000000001` a `…006`. La lámpara (`…003`) tiene stock 3: sirve para probar el `409` |

## 6. Cómo levantar la tienda completa

En [teambsoft-hostapp/integracion](https://github.com/rancesra/teambsoft-hostapp/tree/main/integracion), con el `.env` lleno (las claves se comparten por privado):

```bash
docker compose up -d --build
```

Después, cuatro terminales con `npm run dev`:

- `teambsoft-hostapp`;
- `teambsoft-frontend`, con `KONG_URL=http://localhost:8000`. En PowerShell: `$env:KONG_URL="http://localhost:8000"; npm run dev`;
- el frontend de Búsqueda;
- su `Frontend`.

## 7. Lo que ya está bien y no se toca

- **El checkout:**
  - `referencia` con el `checkoutId`;
  - `devolver-stock` si falla el pago;
  - lectura de `items` en el `409`.
- **El historial de pedidos en el backend** (HU-11, `GET /api/carrito/pedidos`), ya en `develop`.
- **Redis y PostgreSQL** como bases propias.
- **`X-Frame-Options: DENY` en su API.** Solo aplica a las respuestas de la API, no al frontend que va en el iframe, así que no hay que quitarlo.

**Su contrato v1.4** hay que actualizarlo, pero puede ser después del lunes. Las secciones 7.1 y 7.2 dicen tres cosas que ya no son ciertas:

- que Catálogo no valida tokens;
- que cada equipo levanta su Kong;
- que `referencia`, `devolver-stock` e `items` están pendientes.

Hoy los tres están en Catálogo v2.4, y desde la v2.5 hay un solo Kong y Keycloak.

## 8. Qué cubre Carrito de lo que evalúa el profesor

| Criterio | Cómo lo cumple Carrito |
|---|---|
| Base de datos por servicio | Redis (carrito) y PostgreSQL (pedidos), solo suyas |
| Contrato entre equipos | Llama a Catálogo según su contrato v2.5: `descontar-stock` y `devolver-stock` |
| API Gateway | Todo pasa por Kong, en `/api/carrito` |
| Seguridad | Token de Keycloak, validado con la llave pública del realm |
| Micro-frontend | Su frontend, en su puerto, dentro del Host App |
| Docker | Su backend y sus bases en el `docker-compose` de integración |

## 9. Agregado el 14 de noviembre: registro en Eureka

El diagrama del profesor tiene a **todos los servicios registrados en Eureka**, el registro de servicios. Ya está en el `docker-compose` de integración (consola en `http://localhost:8761`) y Catálogo ya se registra. Para que también aparezca Carrito, hay un **segundo parche, pequeño e independiente**: [`carrito-eureka.patch`](carrito-eureka.patch).

| Qué hace | Detalle |
|---|---|
| Se registra como `CARRITO` | Con su IP y su puerto dentro de Docker. Usa la API REST de Eureka, **sin librerías nuevas** |
| Mantiene el registro | Un latido cada 10 segundos. Si Eureka se reinicia, se vuelve a registrar solo |
| Publica su estado | `UP` o `DOWN` según su `/ready`: si se cae Redis, aparece `DOWN` |
| Se despide al apagarse | Sale de la lista al instante |
| Sin `EUREKA_URL` no hace nada | Sus pruebas y el desarrollo sin Docker no cambian. El `docker-compose` de integración ya la pasa |

Archivos: `src/clients/registroEureka.js` (nuevo), unas líneas en `src/server.js` y `tests/registroEureka.test.js` (6 pruebas nuevas, contra un Eureka falso).

**Cómo aplicarlo**, en la misma rama del primer parche:

```bash
git apply carrito-eureka.patch
npm test
```

Se aplica limpio con o sin el primer parche.

**Qué comprobamos:** **204 pruebas en verde** (las 198 de antes y 6 nuevas). Con la tienda completa, los tres servicios aparecen en `UP` en `http://localhost:8761`. También probamos que pasa a `DOWN` si se cae su base de datos, que vuelve a `UP` cuando vuelve, y que sale de la lista al apagarse.


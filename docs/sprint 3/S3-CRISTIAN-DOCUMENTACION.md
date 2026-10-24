# Guía de S3-K — Documentación del backend (tarea de peso bajo)

**Para:** Cristian Rivera · **Tareas:** S3-K1 (README del backend), S3-K2 (ejemplos de la API), S3-K3 (actualizar `ARQUITECTURA-CATALOGO.md` con lo de B4), S3-K4 (opcional: investigación de alternativas al Outbox) · **Repositorio:** [teambsoft-backend](https://github.com/rancesra/teambsoft-backend) · **Rama:** `s3-docs-backend` · **Entrega:** jueves 29 de octubre

> **Peso bajo, y no bloquea nada.** Ninguna otra tarea depende de esta. Si algo no queda terminado, el sprint se considera cumplido igual. Haz lo que alcances, en el orden de abajo. S3-K1 y S3-K2 son las que ya quedaron pendientes del Sprint 2: con esas dos basta para cerrar bien.

Al terminar, cualquier persona nueva del equipo podrá levantar el backend y probar la API leyendo el README y el archivo de ejemplos, sin preguntarle a nadie, y la documentación de arquitectura reflejará lo que de verdad hay construido.

**Dependes de:**

1. **Nadie.** Las cuatro tareas se pueden empezar ya.
2. Ojo con una cosa: **Jhon está cambiando cosas que tocan tu README** (credenciales en un archivo `.env`, un healthcheck nuevo, Swagger y actuator restringidos por Kong, posiblemente el puerto de Mongo). Antes de dar por terminado el README, **vuelve a hacer `git pull origin main`** y revisa qué se unió. Si algo de Jhon aún no está en `main` cuando entregues, déjalo anotado como «disponible tras S3-B4» en vez de describirlo como hecho.

Los comandos son para **PowerShell**, en la terminal de VS Code. Usa `curl.exe` y no `curl`.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
git switch -c s3-docs-backend
git push -u origin s3-docs-backend
```

Si Hector ya publicó la clave de Jira, antepónla al nombre de la rama (`CLAVE-N-s3-docs-backend`).

Ten a mano `docs/CONTRATO-CATALOGO.md` (secciones 2 a 6), `GUIA-INICIO.md`, el informe del Sprint 2 y el `docker-compose.yml`. **Los datos del README salen de ahí y del código, no de memoria ni de esta guía.** Esta guía te dice qué buscar y dónde; si algo no coincide con el contrato, el contrato manda y le avisas a Hector.

## 2. Qué archivos vas a tocar

```
teambsoft-backend/
├── README.md                          actualizar  (S3-K1)
└── docs/
    ├── ejemplos-api.http              NUEVO  (S3-K2)
    ├── ARQUITECTURA-CATALOGO.md       actualizar a v1.6  (S3-K3)
    └── investigacion/
        └── ALTERNATIVAS-OUTBOX.md     NUEVO, opcional  (S3-K4)
```

No tocas código Java. El archivo `docs/ejemplos-api.http` es distinto de tu `pruebas.http` personal: **este sí se sube**, y por eso no debe llevar claves ni datos tuyos.

## 3. S3-K1 — README del backend

Hoy el README explica el comando de arranque y tiene una lista de «Estado — Segunda entrega» con casillas, pero no refleja lo que B4 agregó. Que responda estas cuatro preguntas, cada una en su sección.

### 3.1 ¿Cómo lo levanto?

Los comandos mínimos, tal cual se usan en el equipo (compruébalos tú en una carpeta limpia, no los copies de otro README):

```powershell
docker compose up -d
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Revisa tres cosas del compose actual y documéntalas:

1. **Qué servicios levanta** (Mongo, RabbitMQ, catálogo, Kong…).
2. **El puerto de Mongo en el computador.** El informe del Sprint 2 dice que el compose publica el 27017 del contenedor en el **27018** del host. El README anterior y la guía de inicio hablan del 27017. Mira `docker-compose.yml` y `application.yml` y escribe lo que sea cierto *hoy*; si hay una contradicción, anótala y avísale a Hector.
3. **Si ya existe `.env.example`** (lo crea Jhon en S3-B4b): agrega el paso «copia `.env.example` a `.env`». Si aún no está en `main`, no lo agregues.

### 3.2 ¿Qué endpoints tiene?

Una tabla, con lo que hay en el contrato y en Swagger. Parte de esta y **verifica cada fila**:

| Método y ruta | Para qué sirve | Respuestas principales |
|---|---|---|
| `GET /categorias` | Lista de categorías | 200 |
| `GET /productos` | Listado paginado (`pagina`, `tamanoPagina`, `categoria`, `activo`) | 200; 400 si un parámetro es inválido |
| `GET /productos/{id}` | Detalle, incluso de productos retirados | 200 (con `activo: false` si está retirado); 404 si el id no existe |
| `POST /productos` | Crear | 201; 400 por validación |
| `PUT /productos/{id}` | Reemplazar el producto completo (no cambia `activo`) | 200; 400; 404 |
| `DELETE /productos/{id}` | Retirar (no borra el registro) | 204; 404 |
| `POST /productos/{id}/activar` | Reactivar | 200; 404 |
| `POST /productos/descontar-stock` | Descuento de stock del Carro («todo o nada») | 200; 409 `STOCK_INSUFICIENTE`; 400 |

Para comprobar, abre Swagger (`http://localhost:8080/swagger-ui/index.html`, directo al servicio) y compara fila por fila contra la tabla y el contrato (v2.3). Si el parámetro `activo` del listado o algún código de respuesta no coincide, el contrato manda.

### 3.3 ¿Qué errores devuelve?

Los códigos que Catálogo responde: `PRODUCTO_NO_ENCONTRADO` (404), `VALIDACION_FALLIDA` (400) y `STOCK_INSUFICIENTE` (409). Todos con la forma `{ "codigo": "...", "mensaje": "..." }`.

**Ojo:** `SIN_CONEXION` **no** es del backend; es un código que inventa el cliente del frontend cuando el servidor no responde. No lo pongas aquí.

### 3.4 ¿Qué eventos publica?

Una tabla de los eventos de RabbitMQ (el informe del Sprint 2, sección 5.1, tiene el detalle; verifica contra `PublicadorEventos`):

| Routing key | Cuerpo | Cuándo se publica |
|---|---|---|
| `producto.creado` | `ProductoEvento` | Al crear |
| `producto.actualizado` | `ProductoEvento` | Al actualizar y al descontar stock |
| `producto.desactivado` | Solo `{"id": "..."}` | Al desactivar (no se repite si ya estaba inactivo) |
| `producto.reactivado` | `ProductoEvento` | Al reactivar (no se repite si ya estaba activo) |

Exchange: `catalogo.eventos`, de tipo topic y durable. **Catálogo no crea colas**: cada equipo crea la suya y la enlaza con las routing keys que le interesen.

### 3.5 ¿Dónde veo cada cosa?

Una tabla de direcciones útiles:

| Qué | Dirección | Nota |
|---|---|---|
| Swagger | `http://localhost:8080/swagger-ui/index.html` | Solo directo al servicio, no por Kong (tras S3-B4c) |
| Estado de salud | `http://localhost:8080/actuator/health` | Sin detalles internos tras S3-B4c |
| RabbitMQ (interfaz) | `http://localhost:15672` | Usuario y clave del `.env` tras S3-B4b |
| Kong (proxy) | `http://localhost:8000/api/catalogo/...` | |
| Kong (administración) | `http://localhost:8001/status` | |
| Eureka | `http://localhost:8761` | Solo con un servidor local levantado; el compose lo deshabilita |

Las notas dependen de qué PR de Jhon estén ya en `main` (ver «Dependes de»).

### 3.6 Actualizar la lista de «Estado»

El README tiene casillas sin marcar para Historias 1 a 5, eventos, Eureka y Kong, que están desactualizadas. Márcalas según lo que de verdad hay en `main`, y agrega las historias nuevas (reactivar, listar por estado) si el contrato v2.3 las incluye. Cambia el título «Estado — Segunda entrega» por uno que refleje el sprint actual.

**Cómo saber que está listo:** una persona que no participó en el proyecto puede levantar el backend siguiendo solo el README. Pídele a alguien (Roger sirve) que lo intente en su computador y te cuente dónde se atoró.

## 4. S3-K2 — Ejemplos de la API

Un archivo `docs/ejemplos-api.http` para la extensión **REST Client** de VS Code, con **un caso que funciona y uno que falla por cada endpoint** (los ocho de la tabla).

Empieza con variables para la dirección base, de modo que sirva igual contra el backend directo (8080, sin prefijo) o contra Kong (8000, con `/api/catalogo`):

```http
### Dirección base. Cambia la que uses:
@base = http://localhost:8080
# @base = http://localhost:8000/api/catalogo

### 1. Categorías → 200
GET {{base}}/categorias

### 2. Listar la primera página → 200
GET {{base}}/productos?pagina=1&tamanoPagina=20

### 2b. Error esperado: la primera página es la 1, no la 0 → 400 VALIDACION_FALLIDA
GET {{base}}/productos?pagina=0

### 3. Crear un producto → 201 (el nombre "crear" guarda la respuesta para usar su id abajo)
# @name crear
POST {{base}}/productos
Content-Type: application/json

{
  "nombre": "Camiseta basica algodon",
  "descripcion": "Unisex 100% algodon.",
  "precio": 49900,
  "categoria": "cat-ropa",
  "stock": 120,
  "imagenes": []
}

### 3b. Error esperado: falta el nombre → 400 VALIDACION_FALLIDA
POST {{base}}/productos
Content-Type: application/json

{
  "descripcion": "Sin nombre",
  "precio": 49900,
  "categoria": "cat-ropa",
  "stock": 120,
  "imagenes": []
}

### Id del producto recién creado (sale de la respuesta de "crear")
@id = {{crear.response.body.$.id}}

### 4. Detalle → 200
GET {{base}}/productos/{{id}}

### 4b. Error esperado: id que no existe → 404 PRODUCTO_NO_ENCONTRADO
GET {{base}}/productos/000000000000000000000000
```

Sigue el mismo patrón para `PUT`, `DELETE`, `activar` y `descontar-stock`. Tres cosas a tener en cuenta:

1. **El cuerpo de `POST /productos/descontar-stock` no lo escribas de memoria**: ábrelo en `DescuentoStockRequest.java` y copia la forma exacta. Para mostrar el 409, el ejemplo debe pedir **más stock del que tiene** el producto de la prueba.
2. Para cada caso de éxito, anota en un comentario **qué código debe responder**: es lo que hace útil al archivo.
3. Los casos que dependen del anterior (por ejemplo, `DELETE` y luego `GET` del retirado) van en el orden en que se ejecutan, con un comentario que lo diga. Si tu versión de REST Client no entiende la sintaxis `{{crear.response.body.$.id}}`, deja un comentario que diga «copia aquí el id que devolvió el POST».

**Cómo saber que está listo:** ejecutas el archivo de arriba abajo contra tu backend y cada petición responde lo que dice su comentario.

## 5. S3-K3 — Actualizar `ARQUITECTURA-CATALOGO.md` (v1.6)

El informe del Sprint 2 dice que este documento está en **v1.5, «pendiente de actualizar con B4»**. Abre el archivo (no esta guía) y agrega lo que falta. **Verifica cada afirmación contra el código y el compose** antes de escribirla.

1. **§2, tabla de stack.** Agrega o corrige: RabbitMQ con Spring AMQP, Kong, springdoc-openapi (verifica la versión en el `pom.xml`), cliente de Eureka con Spring Cloud (verifica la versión), Docker Compose con los cuatro servicios.
2. **§3, diagrama de capas.** En el diagrama de Mermaid, `EventPublisher` debería ser `PublicadorEventos`, y puede agregarse la ruta Kong → servicio. Un ejemplo de cómo se vería el diagrama (revisa los nombres en el código):

   ```mermaid
   graph TD
       Kong[Kong API Gateway] -->|REST| PC[ProductoController]
       Kong -->|REST| CC[CategoriaController]
       PC --> PS[ProductoService]
       PS --> PR[ProductoRepository]
       PS --> PE[PublicadorEventos]
       PE -->|catalogo.eventos| RabbitMQ[RabbitMQ]
       PR --> Mongo[(MongoDB)]
   ```

3. **§5, decisiones de diseño.** Agrega las que el informe del Sprint 2 documenta, cada una en dos o tres frases con su «por qué»:
   - El evento se publica **después** de guardar, nunca antes.
   - Las operaciones idempotentes (desactivar algo ya desactivado) **no publican**.
   - El exchange es de tipo topic y Catálogo **no declara colas**: cada consumidor crea la suya.
   - `ProductoDesactivadoEvento` es un record aparte, con solo el `id`.
   - CORS solo en Kong, nunca en Spring.
   - Convertidor JSON de Spring AMQP 4 basado en Jackson 3.
4. **§7, historial.** Agrega la fila `v1.6` con la fecha de hoy y un resumen de una línea.
5. **Una advertencia honesta en §5:** guardar en Mongo y publicar en RabbitMQ **no es una sola operación** (si falla la publicación, el producto queda sin evento). Menciona que se evalúa el patrón Transactional Outbox y enlaza a `docs/EVALUACION-OUTBOX.md` **solo si Rances ya lo unió**.

**Cómo saber que está listo:** cada cosa que el documento afirma existe en el código y en el compose, y el diagrama de Mermaid se ve bien al abrir el archivo en GitHub.

## 6. S3-K4 — Investigación de alternativas al Outbox (opcional)

Una investigación **corta** (una o dos páginas), aislada: **Rances no la espera**. Si llega a tiempo, la lee; si no, su evaluación sigue sin ella.

**La pregunta.** Cuando un servicio guarda un dato en su base y además debe publicar un evento a un bus, las dos operaciones no son atómicas: si falla la segunda, queda un dato sin evento. ¿Qué formas conocidas hay de resolverlo, y cuáles aplican a nuestro caso (Spring Boot + MongoDB + RabbitMQ)?

**Qué investigar** (usa documentación oficial y artículos conocidos; cita siempre la fuente):

1. **Transactional Outbox**: qué es y cómo se implementa.
2. **Polling publisher** y **Change Data Capture** (por ejemplo, Debezium): cómo leen la tabla de eventos pendientes.
3. **Change Streams de MongoDB**: qué son y qué requiere MongoDB para usarlos.
4. **Transacciones en MongoDB**: ¿funcionan en `mongo:7.0` en modo normal (*standalone*)? ¿qué hay que cambiar?
5. **Consumidores idempotentes**: por qué, con cualquiera de estas soluciones, un mensaje puede llegar dos veces, y cómo se tolera.

**Entrega.** `docs/investigacion/ALTERNATIVAS-OUTBOX.md` con:

| Alternativa | Cómo funciona (2 líneas) | Qué exige de nuestra infraestructura | Ventaja | Desventaja | Fuente |
|---|---|---|---|---|---|

Y al final, **tres frases** de conclusión: qué alternativa te parece más razonable para un proyecto de este tamaño y por qué. No tienes que implementar nada.

## 7. Subir tu trabajo

```powershell
git status
git add README.md docs/ejemplos-api.http docs/ARQUITECTURA-CATALOGO.md docs/investigacion
git commit -m "Actualiza README, ejemplos de la API y arquitectura"
git pull origin main
git push
```

- En `git status` **no debe aparecer `pruebas.http`, `.env`, `target/` ni claves de ningún tipo**.
- Pull request con base `main`, título `S3: README, ejemplos de la API y arquitectura`, revisión de **Hector**.
- Puedes abrir un PR por tarea (más fácil de revisar) o uno solo; lo que prefieras.
- En **Jira**, pasa tus tarjetas a *En revisión*, y a *Listo* cuando se una el PR.

## 8. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| REST Client no muestra «Send Request» | Falta la extensión o el archivo no termina en `.http` | Instala *REST Client* y revisa el nombre |
| `GET /productos?pagina=0` responde 200 | Estás probando una versión sin la validación | `git pull` de `main` y reinicia el backend |
| `{{crear.response.body.$.id}}` sale vacío | La petición `crear` no se ejecutó antes | Ejecuta primero `crear` y luego las demás |
| `curl` en PowerShell pide parámetros raros | `curl` es un alias de PowerShell | Usa `curl.exe` |
| El README y el compose no coinciden en el puerto de Mongo | Cambio del Sprint 2 | Anota lo que diga el compose actual y avisa a Hector |
| No sabes qué cuerpo lleva un endpoint | El contrato y el código son la fuente | Mira `CONTRATO-CATALOGO.md` o el record de `dto/` |
| Swagger no abre | El servicio está apagado, o Swagger quedó restringido | Entra directo a `localhost:8080`, no por Kong |
| No sabes si algo de Jhon ya está en `main` | Su PR no se ha unido | `git log --oneline -20` en `main`; si no está, escribe «disponible tras S3-B4» |

---

_Última actualización: 2026-10-24_

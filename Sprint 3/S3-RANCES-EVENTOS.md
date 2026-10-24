# Guía de S3-R — Prueba automática de eventos y evaluación del Transactional Outbox

**Para:** Rances · **Tareas:** S3-R1 (prueba automática de publicación y recepción en RabbitMQ), S3-R2 (evaluación del patrón Transactional Outbox, obligatoria), S3-R3 (implementación del Outbox, **opcional**) · **Repositorio:** [teambsoft-backend](https://github.com/rancesra/teambsoft-backend) · **Ramas:** `s3-pruebas-eventos`, `s3-evaluacion-outbox` y, si llega, `s3-outbox` · **Entrega:** jueves 29 de octubre

Al terminar, el bus de eventos tendrá su primera prueba automática (hoy solo se verificó a mano: 5 y 3 mensajes), y el equipo tendrá por escrito qué pasa cuando se guarda un producto y el evento no sale, con una recomendación sobre si implementar el Outbox.

**Dependes de:**

1. **S3-R1** necesita RabbitMQ encendido (`docker compose up -d`) y, para no escribir en tu base de desarrollo, el perfil `test` de **Jhon** (S3-B4d). No hace falta esperarlo para empezar, pero antes de abrir el PR trae lo que haya en `main`. Si Jhon cambia las credenciales de RabbitMQ, tu prueba no tiene que cambiar: lee las propiedades de Spring.
2. **S3-R2** no depende de nadie.
3. **S3-R3** solo se hace si **R1 y R2 están unidos** (meta: martes 27), Hector aprueba la decisión y queda tiempo. Si no, no se hace y no pasa nada: el plan dice «implementado si el tiempo alcanza».

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
docker compose up -d
docker compose ps
git switch -c s3-pruebas-eventos
git push -u origin s3-pruebas-eventos
```

Si Hector ya publicó la clave de Jira, antepónla al nombre de la rama (`CLAVE-N-s3-pruebas-eventos`).

Antes de escribir código, **lee tres cosas** (tómate diez minutos):

1. `PublicadorEventos`: qué método publica cada evento y a qué routing key.
2. `ProductoService`: los nombres reales de los métodos de crear, actualizar, desactivar, activar y descontar stock, y **en qué línea se publica** (después de guardar).
3. Los records `ProductoEvento` y `ProductoDesactivadoEvento`.

En esta guía los nombres de métodos están como `crear(...)`, `actualizar(...)`, etc.: **cámbialos por los reales**.

## 2. Qué archivos vas a tocar

```
teambsoft-backend/
├── src/test/java/co/edu/uis/catalogo/eventos/
│   └── PublicacionEventosTest.java      NUEVO  (S3-R1)
└── docs/
    └── EVALUACION-OUTBOX.md             NUEVO  (S3-R2)
```

No cambias código de producción en R1 ni R2.

## 3. S3-R1 — Prueba automática de los eventos

**Qué se prueba.** Que cuando el servicio crea, actualiza, desactiva o reactiva un producto, **llega un mensaje** al exchange `catalogo.eventos` con la routing key y el cuerpo que promete el contrato, y que las operaciones repetidas **no publican otra vez**.

**Cómo funciona la prueba.** Es una prueba de integración: arranca la aplicación completa, crea una **cola temporal** enlazada al exchange con `producto.*` (como lo haría el Equipo A), llama al servicio y lee la cola. Igual que las otras pruebas del proyecto, necesita los contenedores encendidos (Mongo y RabbitMQ).

### 3.1 La clase de prueba

Crea `src/test/java/co/edu/uis/catalogo/eventos/PublicacionEventosTest.java`:

```java
package co.edu.uis.catalogo.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import co.edu.uis.catalogo.repository.ProductoRepository;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Comprueba que los eventos de productos llegan a RabbitMQ (contrato, sección 5): la routing key, el
 * cuerpo y que las operaciones idempotentes no vuelven a publicar. Necesita Mongo y RabbitMQ encendidos.
 */
@SpringBootTest
@ActiveProfiles("test")
class PublicacionEventosTest {

	private static final String EXCHANGE = "catalogo.eventos";
	private static final long ESPERA_MS = 5000;
	private static final long ESPERA_SIN_MENSAJE_MS = 1000;

	@Autowired
	private ProductoService productoService;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	private String cola;
	private String idProducto;

	/** Crea una cola propia, como haría Búsqueda, enlazada con todas las routing keys de producto. */
	@BeforeEach
	void crearColaDePrueba() {
		cola = "prueba.eventos." + UUID.randomUUID();
		// durable=false, exclusive=false, autoDelete=true
		Queue queue = new Queue(cola, false, false, true);
		amqpAdmin.declareQueue(queue);
		amqpAdmin.declareBinding(BindingBuilder.bind(queue)
				.to(new TopicExchange(EXCHANGE, true, false))
				.with("producto.*"));
	}

	@AfterEach
	void limpiar() {
		amqpAdmin.deleteQueue(cola);
		if (idProducto != null) {
			productoRepository.deleteById(idProducto);
		}
	}

	@Test
	void crearPublicaProductoCreado() {
		idProducto = crearProductoDePrueba();

		Message mensaje = siguienteMensaje();

		assertThat(clave(mensaje)).isEqualTo("producto.creado");
		String cuerpo = cuerpo(mensaje);
		assertThat(cuerpo).contains("\"id\":\"" + idProducto + "\"");
		assertThat(cuerpo).contains("\"nombre\":\"Producto de prueba de eventos\"");
		assertThat(cuerpo).contains("\"categoria\":\"cat-ropa\"");
		assertThat(cuerpo).contains("\"precio\"", "\"stock\"", "\"descripcion\"");
	}

	@Test
	void actualizarPublicaProductoActualizado() {
		idProducto = crearProductoDePrueba();
		siguienteMensaje(); // el de "creado", para dejar la cola vacía

		// Cambia por la llamada real a PUT del service.
		// productoService.actualizar(idProducto, ...datos nuevos...);

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.actualizado");
	}

	@Test
	void desactivarPublicaSoloElId() {
		idProducto = crearProductoDePrueba();
		siguienteMensaje();

		// productoService.desactivar(idProducto);

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.desactivado");
		assertThat(cuerpo(mensaje)).contains("\"id\":\"" + idProducto + "\"");
		// El contrato promete solo el id: ningún otro campo (ni en null).
		assertThat(cuerpo(mensaje)).doesNotContain("nombre", "precio");
	}

	@Test
	void desactivarDosVecesPublicaUnSoloEvento() {
		idProducto = crearProductoDePrueba();
		siguienteMensaje();

		// productoService.desactivar(idProducto);
		assertThat(clave(siguienteMensaje())).isEqualTo("producto.desactivado");

		// productoService.desactivar(idProducto); // idempotente: responde igual, sin evento
		assertThat(hayMensaje()).isFalse();
	}

	@Test
	void activarDosVecesPublicaUnSoloEvento() {
		idProducto = crearProductoDePrueba();
		// productoService.desactivar(idProducto);
		vaciarCola();

		// productoService.activar(idProducto);
		assertThat(clave(siguienteMensaje())).isEqualTo("producto.reactivado");

		// productoService.activar(idProducto); // ya activo: sin evento
		assertThat(hayMensaje()).isFalse();
	}

	// ---- ayudas ----

	/** Cambia por la llamada real; debe devolver el id del producto creado. */
	private String crearProductoDePrueba() {
		// return productoService.crear(new ProductoRequest(
		//         "Producto de prueba de eventos", "Descripcion", new BigDecimal("49900"),
		//         "cat-ropa", 10, List.of())).id();
		throw new UnsupportedOperationException("Completa con la llamada real del service");
	}

	/** Espera hasta 5 s por un mensaje; falla la prueba si no llega. */
	private Message siguienteMensaje() {
		Message mensaje = rabbitTemplate.receive(cola, ESPERA_MS);
		assertThat(mensaje).as("debía llegar un evento a la cola").isNotNull();
		return mensaje;
	}

	/** Espera 1 s; devuelve true solo si llegó algo (y lo consume). */
	private boolean hayMensaje() {
		return rabbitTemplate.receive(cola, ESPERA_SIN_MENSAJE_MS) != null;
	}

	private void vaciarCola() {
		while (rabbitTemplate.receive(cola, 500) != null) {
			// descarta lo que haya
		}
	}

	private String clave(Message mensaje) {
		return mensaje.getMessageProperties().getReceivedRoutingKey();
	}

	private String cuerpo(Message mensaje) {
		return new String(mensaje.getBody(), StandardCharsets.UTF_8);
	}

}
```

**Qué tienes que completar** (están comentadas a propósito, porque no conozco los nombres reales):

1. `crearProductoDePrueba()`: la llamada real al service y cómo sacar el `id` de lo que devuelve.
2. Las llamadas comentadas a `actualizar`, `desactivar` y `activar`: descoméntalas con la firma real. Si el service recibe el DTO `ProductoRequest`, constrúyelo con datos válidos (precio mayor que 0, categoría `cat-ropa`, stock 0 o más).
3. Quita los imports que no uses (`BigDecimal`, `List`).

**Dos notas.**

- Si `org.assertj` no se resuelve, revisa el `pom.xml`: debería llegar con los starters de prueba. Si no, usa `Assertions` de JUnit (`assertEquals`, `assertNotNull`, `assertTrue`).
- La prueba compara el cuerpo como texto. Jackson no deja espacios entre los dos puntos y el valor, por eso `"nombre":"..."` coincide tal cual. El precio no se compara con valor exacto porque `49900` y `49900.00` son el mismo número pero distinto texto (la misma razón por la que se usa `compareTo` y no `equals` con `BigDecimal`).

### 3.2 Casos extra (si te sobra tiempo)

| Caso | Qué debe pasar |
|---|---|
| `actualizar` sobre un id inexistente | Lanza `ProductoNoEncontradoException` **y no hay mensaje** en la cola |
| `descontarStock` con stock suficiente | Llega un `producto.actualizado` |
| `descontarStock` con stock insuficiente | Lanza la excepción de stock y **no hay mensaje** |

### 3.3 Cómo comprobar

1. Corre solo esta clase:

   ```powershell
   .\mvnw.cmd test "-Dtest=PublicacionEventosTest"
   ```

   Las cinco pruebas deben pasar.
2. **Comprueba que la prueba sirve** (la parte que más se olvida): una prueba que nunca falla no prueba nada. Comenta temporalmente en `ProductoService` la línea que publica el evento de desactivar, vuelve a correr la clase, y confirma que **falla** `desactivarPublicaSoloElId`. Después restaura la línea (`git restore` del archivo si la modificaste solo para esto).
3. Corre toda la suite (`.\mvnw.cmd test`): deben pasar las 51 de antes más las nuevas.
4. Apaga RabbitMQ (`docker compose stop rabbitmq`) y corre la clase: debe fallar con un error de conexión claro. Vuelve a encenderlo (`docker compose start rabbitmq`). Documenta en el PR que **esta prueba necesita RabbitMQ encendido**, igual que las otras necesitan Mongo.
5. Abre `http://localhost:15672` y comprueba que no quedaron colas `prueba.eventos.*` (se borran solas).

Si más adelante el equipo quiere pruebas que no dependan del compose, la alternativa es Testcontainers con un contenedor de RabbitMQ. **No es parte de este sprint**: añade una dependencia nueva y su versión depende de lo que gestione Spring Boot 4.1. Déjalo como pendiente en el PR.

## 4. S3-R2 — Evaluación del Transactional Outbox

**El problema.** Guardar el producto y publicar el evento son **dos operaciones distintas**: una contra Mongo, otra contra RabbitMQ. No hay una transacción que las cubra a las dos. Si el guardado sale bien y la publicación falla, Mongo queda con un producto que Búsqueda nunca llegará a indexar.

**El patrón Outbox**, en una frase: en vez de publicar directamente, el servicio **guarda el evento** junto con el cambio de negocio, en la misma operación atómica, y otro proceso lo publica después y lo marca como enviado.

### 4.1 Primero, demuestra el problema

Antes de recomendar nada, hay que ver el fallo en el sistema real.

1. Con todo encendido, apaga solo RabbitMQ:

   ```powershell
   docker compose stop rabbitmq
   ```

2. Crea un producto por el servicio (con REST Client o con `curl.exe`, directo al puerto 8080) y anota:
   - ¿Qué código responde? (¿201 o 500?)
   - ¿El producto quedó guardado en Mongo?
     `docker exec catalogo-mongo mongosh --quiet catalogo --eval "db.productos.find({nombre:'<tu nombre>'})"`
   - ¿Qué dice el log de catálogo? (`docker compose logs catalogo --tail 50`)
3. Enciende RabbitMQ otra vez: `docker compose start rabbitmq`. Confirma que el evento **no se recupera solo**: no aparece en la cola. Esa es la evidencia.
4. Borra el producto de prueba para no ensuciar los datos.

Anota exactamente lo que viste, con los códigos y los mensajes de error: esa evidencia es lo más valioso del documento.

### 4.2 Las opciones

Compara como mínimo estas cuatro. Hay una restricción técnica que cambia todo y que conviene que tengas presente:

> **MongoDB `mongo:7.0` en modo normal (standalone) no admite transacciones de varios documentos.** Las transacciones necesitan un *replica set*, aunque sea de un solo nodo. Y Spring Data MongoDB no las activa por defecto: hay que declarar un `MongoTransactionManager`.

| Opción | Cómo funciona | Qué cuesta |
|---|---|---|
| **A. Dejar como está** | Se acepta el riesgo; si algo falla, se reindexa a mano | Nada de código; el riesgo sigue |
| **B. Outbox en otra colección, con transacción** | `productos` y `eventos_pendientes` se escriben en una transacción; un proceso programado publica los pendientes | Pasar Mongo a replica set de un nodo, `MongoTransactionManager`, nueva colección, proceso de publicación |
| **C. Outbox dentro del mismo documento** | El producto lleva un campo `eventosPendientes`; guardar producto + evento es **una sola operación** sobre un documento (atómica sin transacciones); un proceso publica y limpia | Cambiar la entidad `Producto` (que hoy no tiene setters), nuevo método de limpieza, proceso de publicación |
| **D. CDC con Debezium (o change streams)** | Un componente externo lee el log de Mongo y publica | Infraestructura nueva (Kafka Connect, o change streams que también exigen replica set); demasiado para este proyecto |

### 4.3 Qué debe tener el documento

Crea `docs/EVALUACION-OUTBOX.md` con estas secciones, cortas:

1. **El problema y la evidencia** (lo de 4.1: qué hiciste y qué observaste).
2. **Las opciones**, con la tabla anterior adaptada a lo que viste en el código.
3. **Criterios** y una puntuación simple (por ejemplo 1 a 3) para cada opción: qué garantía da, cuánto código cambia, qué tan arriesgado es tocar `ProductoService`, cuánto tarda, qué tan fácil es de probar con la prueba de R1.
4. **Efectos para los otros equipos.** Con cualquier Outbox, la entrega es *al menos una vez*: un evento puede publicarse **dos veces**, y a veces fuera de orden. Búsqueda y Carro deben tolerarlo (tratar de nuevo el mismo producto no debe romper nada). Anota la pregunta para los equipos A y C.
5. **Recomendación**: qué opción, por qué, cuántas horas estimas, y si **entra en este sprint** o pasa al siguiente. Es válido recomendar la opción A «por ahora» si el costo no se justifica.
6. **Qué pasaría con la prueba de R1** en cada caso.

Cristian tiene una tarea opcional (S3-K4) de investigación sobre alternativas al Outbox. **No la esperes**: si ya llegó, léela; si no, sigue sin ella.

### 4.4 Cómo comprobar y entregar

1. El documento tiene evidencia real de 4.1, no solo teoría.
2. Hector lo lee y registra la decisión («implementar en este sprint», «próximo sprint» o «no»). Sin su visto bueno no se empieza S3-R3.

## 5. S3-R3 — Implementar el Outbox (opcional)

**Solo si** R1 y R2 ya están unidos a `main`, Hector aprobó implementar, y quedan al menos **dos días** de trabajo. Si no, **déjalo para el siguiente sprint**: no es un fallo.

Las condiciones innegociables, si decides hacerlo:

1. **Rama aparte** (`s3-outbox`) y un PR aparte; **nunca** mezclado con R1 o R2.
2. La prueba de R1 debe **seguir pasando** igual: el comportamiento visible (los mismos mensajes, con las mismas routing keys) no cambia.
3. Hay que agregar una prueba nueva: guardar con RabbitMQ apagado → el evento queda pendiente → al encender RabbitMQ, se publica.
4. El diseño exacto depende de la opción que recomiendes en R2. Antes de escribir código, acuerda con Hector y Jhon los cambios que toquen `docker-compose.yml` (si eliges replica set, es de Jhon) y avisa a los equipos A y C del posible duplicado de mensajes.
5. Si el miércoles 28 no está terminado y probado, **no entra en este sprint**.

## 6. Subir tu trabajo

```powershell
git status
git add src/test docs/EVALUACION-OUTBOX.md
git commit -m "Agrega prueba automatica de eventos de productos"
git pull origin main
.\mvnw.cmd test
git push
```

- Un PR por rama, con base `main`.
  - `S3: prueba automatica de eventos` → revisión de **Jhon**.
  - `S3: evaluacion del Transactional Outbox` → revisión de **Hector**.
- En la descripción del PR de R1 incluye lo que viste en la comprobación 2 (la prueba **fallaba** al quitar la publicación) y que necesita RabbitMQ encendido.
- En **Jira**, pasa S3-R1 y S3-R2 a *En revisión*, y a *Listo* cuando se unan.

## 7. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `PRECONDITION_FAILED - inequivalent arg 'durable'` | Declaras el exchange con argumentos distintos a los de la app | Debe ser `new TopicExchange("catalogo.eventos", true, false)`: durable, sin autoDelete |
| La prueba espera 5 s y `siguienteMensaje()` falla | La cola se creó **después** de publicar, o el exchange tiene otro nombre | Mantén la cola en `@BeforeEach` y verifica el nombre con el publicador |
| `Connection refused` a RabbitMQ | El contenedor está apagado, o las credenciales cambiaron | `docker compose up -d`; si Jhon cambió las credenciales, copia `.env.example` a `.env` |
| `ACCESS_REFUSED` | El perfil `test` no tiene las credenciales nuevas | Usa las propiedades de Spring (`spring.rabbitmq.*`), no valores escritos en la prueba |
| La prueba pasa aunque quites la publicación | La cola recibió un mensaje de otra prueba, o no vaciaste la cola | Cada prueba tiene su cola propia con nombre único; descarta los mensajes anteriores con `siguienteMensaje()` |
| El cuerpo no contiene `"nombre":"..."` | El mensaje no es JSON (convertidor sin configurar) | Revisa que el convertidor JSON esté en la configuración de eventos (incidente del Sprint 2) |
| `UnsupportedOperationException` al correr | No completaste `crearProductoDePrueba()` | Sección 3.1 |
| La prueba deja productos en Mongo | `limpiar()` no se ejecuta con el id correcto | Asigna `idProducto` justo después de crear |

---

_Última actualización: 2026-10-24_

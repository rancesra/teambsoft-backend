package co.edu.uis.catalogo.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;

import co.edu.uis.catalogo.config.ConfiguracionEventos;
import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.ProductoRequest;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.model.DescuentoStock;
import co.edu.uis.catalogo.repository.ProductoRepository;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Comprueba que los eventos de productos llegan a RabbitMQ (contrato, sección 5): la routing key, el
 * cuerpo, y que las operaciones idempotentes no vuelven a publicar.
 *
 * <p>Es una prueba de integración: necesita MongoDB y RabbitMQ encendidos. Usa el perfil {@code test}
 * para escribir en la base {@code catalogo-test} y no en la de desarrollo. Ese perfil apaga RabbitMQ
 * para las demás pruebas; aquí se vuelve a encender vaciando {@code spring.autoconfigure.exclude},
 * porque justamente es lo que se quiere probar. Eureka se apaga porque esta prueba no lo necesita.
 *
 * <p>Cada prueba crea su propia cola, enlazada al exchange con {@code producto.*}, igual que lo haría
 * Búsqueda. Así lo que llega a la cola es lo que recibiría cualquier consumidor.
 */
@SpringBootTest(properties = { "spring.autoconfigure.exclude=", "eureka.client.enabled=false" })
@ActiveProfiles("test")
class PublicacionEventosTest {

	private static final long ESPERA_MS = 5000;
	private static final long ESPERA_SIN_MENSAJE_MS = 1000;
	private static final String NOMBRE = "Producto de prueba de eventos";

	/** ISO-8601 en UTC, con o sin milisegundos: {@code 2026-11-12T15:30:00Z} o {@code ...:00.123Z}. */
	private static final String FECHA_ISO = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z";

	@Autowired
	private ProductoService productoService;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private MongoTemplate mongoTemplate;

	private String cola;
	private String idProducto;

	/** Una por prueba, para que una no vea el registro de descuento de otra. */
	private String referencia;

	@BeforeEach
	void crearColaDePrueba() {
		referencia = "prueba-" + UUID.randomUUID();
		cola = "prueba.eventos." + UUID.randomUUID();
		// RabbitMQ 4 ya no acepta colas no durables y no exclusivas, y una cola con autoDelete se
		// borraría después de la primera lectura. Por eso es durable, se borra en limpiar() y, si la
		// prueba se interrumpe antes, RabbitMQ la elimina sola al minuto sin uso (x-expires).
		Queue queue = QueueBuilder.durable(cola).expires(60_000).build();
		amqpAdmin.declareQueue(queue);
		// Mismos argumentos que el exchange de la aplicación; si no coinciden, RabbitMQ rechaza la declaración.
		amqpAdmin.declareBinding(BindingBuilder.bind(queue)
				.to(new TopicExchange(ConfiguracionEventos.EXCHANGE, true, false))
				.with("producto.*"));
	}

	@AfterEach
	void limpiar() {
		amqpAdmin.deleteQueue(cola);
		mongoTemplate.remove(Query.query(Criteria.where("_id").is(referencia)), DescuentoStock.class);
		if (idProducto != null) {
			productoRepository.deleteById(idProducto);
		}
	}

	@Test
	void crearPublicaProductoCreado() {
		idProducto = crearProductoDePrueba(10);

		Message mensaje = siguienteMensaje();

		assertThat(clave(mensaje)).isEqualTo("producto.creado");
		String cuerpo = cuerpo(mensaje);
		assertThat(cuerpo).contains("\"id\":\"" + idProducto + "\"");
		assertThat(cuerpo).contains("\"nombre\":\"" + NOMBRE + "\"");
		assertThat(cuerpo).contains("\"categoria\":\"cat-ropa\"");
		// v2.3: Búsqueda necesita la descripción y el stock.
		assertThat(cuerpo).contains("\"precio\"", "\"stock\":10", "\"descripcion\"");
		// v2.4: el estado y la fecha del evento.
		assertThat(cuerpo).contains("\"activo\":true");
		assertThat(cuerpo).containsPattern("\"fecha\":\"" + FECHA_ISO + "\"");
	}

	/** v2.4: un PUT sobre un producto desactivado publica actualizado, pero con activo en false. */
	@Test
	void actualizarUnoDesactivadoPublicaActivoFalse() {
		idProducto = crearProductoDePrueba(10);
		productoService.desactivar(idProducto);
		vaciarCola();

		productoService.actualizar(idProducto, datos(25));

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.actualizado");
		assertThat(cuerpo(mensaje)).contains("\"activo\":false", "\"stock\":25");
	}

	@Test
	void actualizarPublicaProductoActualizado() {
		idProducto = crearProductoDePrueba(10);
		siguienteMensaje();

		productoService.actualizar(idProducto, datos(25));

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.actualizado");
		assertThat(cuerpo(mensaje)).contains("\"stock\":25");
	}

	@Test
	void desactivarPublicaSoloElId() {
		idProducto = crearProductoDePrueba(10);
		siguienteMensaje();

		productoService.desactivar(idProducto);

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.desactivado");
		// El contrato promete el id y la fecha: ningún otro campo, ni siquiera en null.
		assertThat(cuerpo(mensaje)).matches("\\{\"id\":\"" + idProducto + "\",\"fecha\":\"" + FECHA_ISO + "\"\\}");
	}

	@Test
	void desactivarDosVecesPublicaUnSoloEvento() {
		idProducto = crearProductoDePrueba(10);
		siguienteMensaje();

		productoService.desactivar(idProducto);
		assertThat(clave(siguienteMensaje())).isEqualTo("producto.desactivado");

		productoService.desactivar(idProducto);
		assertThat(hayMensaje()).isFalse();
	}

	@Test
	void activarDosVecesPublicaUnSoloEvento() {
		idProducto = crearProductoDePrueba(10);
		productoService.desactivar(idProducto);
		vaciarCola();

		productoService.activar(idProducto);
		assertThat(clave(siguienteMensaje())).isEqualTo("producto.reactivado");

		productoService.activar(idProducto);
		assertThat(hayMensaje()).isFalse();
	}

	@Test
	void actualizarUnoInexistenteNoPublica() {
		assertThatThrownBy(() -> productoService.actualizar("000000000000000000000000", datos(5)))
				.isInstanceOf(ProductoNoEncontradoException.class);

		assertThat(hayMensaje()).isFalse();
	}

	@Test
	void descontarStockPublicaProductoActualizado() {
		idProducto = crearProductoDePrueba(10);
		siguienteMensaje();

		productoService.descontarStock(descuento(3));

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.actualizado");
		assertThat(cuerpo(mensaje)).contains("\"stock\":7");
	}

	@Test
	void descontarStockInsuficienteNoPublica() {
		idProducto = crearProductoDePrueba(2);
		siguienteMensaje();

		assertThatThrownBy(() -> productoService.descontarStock(descuento(5)))
				.isInstanceOf(StockInsuficienteException.class);

		assertThat(hayMensaje()).isFalse();
	}

	/** v2.4: el reintento con la misma referencia no descuenta ni avisa otra vez. */
	@Test
	void descontarConReferenciaRepetidaPublicaUnaSolaVez() {
		idProducto = crearProductoDePrueba(10);
		siguienteMensaje();

		productoService.descontarStock(descuentoConReferencia(3));
		assertThat(cuerpo(siguienteMensaje())).contains("\"stock\":7");

		productoService.descontarStock(descuentoConReferencia(3));
		assertThat(hayMensaje()).isFalse();
	}

	/** v2.4: devolver el stock es un cambio de stock, y se anuncia como tal. */
	@Test
	void devolverStockPublicaProductoActualizado() {
		idProducto = crearProductoDePrueba(10);
		productoService.descontarStock(descuentoConReferencia(3));
		vaciarCola();

		productoService.devolverStock(referencia);

		Message mensaje = siguienteMensaje();
		assertThat(clave(mensaje)).isEqualTo("producto.actualizado");
		assertThat(cuerpo(mensaje)).contains("\"stock\":10");
	}

	// ---- ayudas ----

	private DescuentoStockRequest descuentoConReferencia(int cantidad) {
		return new DescuentoStockRequest(referencia,
				List.of(new DescuentoStockRequest.Item(idProducto, cantidad)));
	}

	private String crearProductoDePrueba(int stock) {
		return productoService.crear(datos(stock)).id();
	}

	private ProductoRequest datos(int stock) {
		return new ProductoRequest(NOMBRE, "Descripción de prueba", new BigDecimal("49900"), "cat-ropa",
				stock, List.of());
	}

	private DescuentoStockRequest descuento(int cantidad) {
		return new DescuentoStockRequest(List.of(new DescuentoStockRequest.Item(idProducto, cantidad)));
	}

	/** Espera hasta 5 s por un mensaje; la prueba falla si no llega. */
	private Message siguienteMensaje() {
		Message mensaje = rabbitTemplate.receive(cola, ESPERA_MS);
		assertThat(mensaje).as("debía llegar un evento a la cola").isNotNull();
		return mensaje;
	}

	/** Espera 1 s y dice si llegó algo. Lo que llegue se consume. */
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

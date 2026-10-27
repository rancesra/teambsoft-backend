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
import org.springframework.test.context.ActiveProfiles;

import co.edu.uis.catalogo.config.ConfiguracionEventos;
import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.ProductoRequest;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
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

	@BeforeEach
	void crearColaDePrueba() {
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
		// El contrato promete solo el id: ningún otro campo, ni siquiera en null.
		assertThat(cuerpo(mensaje)).isEqualTo("{\"id\":\"" + idProducto + "\"}");
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

	// ---- ayudas ----

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

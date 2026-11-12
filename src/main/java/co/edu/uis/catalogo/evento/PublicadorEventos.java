package co.edu.uis.catalogo.evento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import co.edu.uis.catalogo.config.ConfiguracionEventos;
import co.edu.uis.catalogo.model.Producto;

/**
 * Publica en RabbitMQ los cambios del catálogo (contrato, sección 5).
 *
 * <p>Está aparte del service a propósito: así {@code ProductoService} sigue ocupándose solo de las
 * reglas de negocio y no sabe nada de RabbitMQ. Si mañana cambia el bus de eventos, se cambia aquí.
 */
@Component
public class PublicadorEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

	private final RabbitTemplate rabbitTemplate;

	public PublicadorEventos(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void productoCreado(Producto producto) {
		publicar("producto.creado", ProductoEvento.desde(producto, ahora()));
	}

	public void productoActualizado(Producto producto) {
		publicar("producto.actualizado", ProductoEvento.desde(producto, ahora()));
	}

	public void productoReactivado(Producto producto) {
		publicar("producto.reactivado", ProductoEvento.desde(producto, ahora()));
	}

	public void productoDesactivado(String id) {
		publicar("producto.desactivado", new ProductoDesactivadoEvento(id, ahora()));
	}

	private void publicar(String routingKey, Object cuerpo) {
		rabbitTemplate.convertAndSend(ConfiguracionEventos.EXCHANGE, routingKey, cuerpo);
		log.info("Evento publicado: {}", routingKey);
	}

	/**
	 * La fecha del evento (contrato v2.4, sección 5), en ISO-8601 UTC, por ejemplo
	 * {@code 2026-11-12T15:30:00.123Z}.
	 *
	 * <p>Va como texto y no como {@code Instant} para no depender de cómo lo serialice Jackson: así el
	 * formato que recibe Búsqueda es siempre el mismo. Se recorta a milisegundos porque dos eventos del
	 * mismo producto pueden salir en el mismo segundo, y la fecha es lo que usa el consumidor para
	 * ordenarlos.
	 */
	private static String ahora() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
	}

}

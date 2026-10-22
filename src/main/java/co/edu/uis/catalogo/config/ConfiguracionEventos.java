package co.edu.uis.catalogo.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del bus de eventos (contrato, sección 5).
 *
 * <p>Catálogo solo <b>publica</b>: no escucha nada. Quien quiera enterarse —Búsqueda y Carro— crea
 * su propia cola y la enlaza a este exchange con las routing keys que le interesen. Por eso aquí no
 * se declara ninguna cola: declararlas sería decidir por ellos.
 */
@Configuration
public class ConfiguracionEventos {

	/** Nombre del exchange acordado en el contrato. */
	public static final String EXCHANGE = "catalogo.eventos";

	/**
	 * Un exchange <b>topic</b> reparte cada mensaje a todas las colas cuyo patrón encaje con la routing
	 * key. Así, Búsqueda puede enlazarse con {@code producto.*} para recibirlos todos y Carro solo con
	 * {@code producto.actualizado} y {@code producto.desactivado}, sin que ninguno estorbe al otro.
	 *
	 * <p>{@code durable = true} hace que el exchange sobreviva a un reinicio de RabbitMQ.
	 */
	@Bean
	TopicExchange catalogoEventos() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/**
	 * Sin esto, Spring enviaría los objetos serializados en formato binario de Java, que solo otro
	 * programa Java podría leer. El contrato dice que el cuerpo es JSON.
	 */
	@Bean
	MessageConverter convertidorJson() {
		return new JacksonJsonMessageConverter();
	}

}

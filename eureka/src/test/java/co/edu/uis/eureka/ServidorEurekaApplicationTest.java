package co.edu.uis.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Arranca el servidor de verdad, en un puerto libre, y le pregunta como lo haría un cliente. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServidorEurekaApplicationTest {

	@LocalServerPort
	int puerto;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpResponse<String> pedir(String ruta) throws Exception {
		var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
				.header("Accept", "application/json")
				.build();
		return http.send(pedido, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void respondeElRegistroDeServicios() throws Exception {
		var respuesta = pedir("/eureka/apps");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).contains("\"applications\"");
	}

	@Test
	void respondeLaSaludParaElHealthcheck() throws Exception {
		var respuesta = pedir("/actuator/health");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).contains("\"UP\"");
	}
}

package co.edu.uis.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * El registro de servicios de la tienda.
 *
 * Cada microservicio, al arrancar, se anuncia aquí con su nombre, su IP y su puerto, y cada pocos
 * segundos confirma que sigue vivo. Quien necesite llamar a "catalogo" le pregunta a Eureka dónde
 * está, en vez de tener la dirección escrita a mano. Toda la lógica la trae Spring Cloud: esta
 * clase solo la enciende.
 */
@SpringBootApplication
@EnableEurekaServer
public class ServidorEurekaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServidorEurekaApplication.class, args);
	}
}

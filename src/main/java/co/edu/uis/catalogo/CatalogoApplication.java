package co.edu.uis.catalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de Catálogo, la fuente de verdad de los productos de la tienda.
 *
 * <p>{@code @SpringBootApplication} activa dos cosas: la autoconfiguración (Spring configura Tomcat,
 * MongoDB y Jackson según las librerías del pom) y el escaneo de componentes (Spring busca clases
 * anotadas en este paquete y sus subpaquetes). Por eso todo el código del servicio vive dentro de
 * {@code co.edu.uis.catalogo}: una clase fuera de ese paquete, Spring no la encuentra.
 */
@SpringBootApplication
public class CatalogoApplication {

	public static void main(String[] args) {
		SpringApplication.run(CatalogoApplication.class, args);
	}

}

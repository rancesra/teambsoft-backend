package co.edu.uis.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de humo: comprueba que la aplicación completa arranca, con todas sus piezas conectadas.
 * Necesita MongoDB encendido, porque al arrancar se precargan las categorías.
 */
@SpringBootTest
class CatalogoApplicationTests {

	@Test
	void contextLoads() {
	}

}

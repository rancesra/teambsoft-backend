package co.edu.uis.catalogo.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.edu.uis.catalogo.evento.PublicadorEventos;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.ProductoRepository;

/**
 * Carga los seis productos de prueba de la integración, con ids fijos (contrato v2.4, sección 10.5).
 *
 * <p>{@code @Profile("integracion")} hace que Spring solo cree este componente cuando el perfil
 * {@code integracion} está activo ({@code SPRING_PROFILES_ACTIVE=integracion}). Lo activa el
 * {@code docker-compose} de integración del Host App. En desarrollo y en las pruebas no existe, y la
 * base arranca vacía como siempre.
 *
 * <p>Las reglas:
 * <ul>
 * <li><b>Solo crea los que no existen.</b> Si un producto de prueba ya está, no lo toca, aunque le
 * hayan descontado stock: así un reinicio no deshace lo que se hizo en la demostración. Para volver a
 * empezar se borra el volumen de MongoDB.</li>
 * <li><b>Publica {@code producto.creado} por cada uno que crea,</b> como si lo hubiera creado un
 * administrador. Así Búsqueda los indexa por la vía normal de los eventos, sin llamar a la API. Si
 * los cargara directo en MongoDB, Búsqueda nunca se enteraría de que existen.</li>
 * </ul>
 */
@Component
@Profile("integracion")
public class CargaProductosPrueba implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CargaProductosPrueba.class);

	/**
	 * La tabla de la sección 10.5 del contrato. Los ids no cambian nunca; el resto puede cambiar.
	 *
	 * <p>Es un método y no una constante porque {@link Producto} cambia (por ejemplo, al guardarlo): cada
	 * llamada arma productos nuevos y nadie comparte el mismo objeto.
	 */
	static List<Producto> productos() {
		return List.of(
			producto("674000000000000000000001", "Camiseta básica algodón",
					"Camiseta unisex 100% algodón, ideal para el día a día.", "49900", "cat-ropa", 100),
			producto("674000000000000000000002", "Jean clásico azul",
					"Jean de corte recto en denim azul.", "129900", "cat-ropa", 50),
			producto("674000000000000000000003", "Lámpara de escritorio LED",
					"Lámpara LED con brazo ajustable y tres niveles de luz.", "89900", "cat-hogar", 3),
			producto("674000000000000000000004", "Juego de sábanas doble",
					"Juego de sábanas para cama doble, 200 hilos.", "159900", "cat-hogar", 0),
			producto("674000000000000000000005", "Audífonos inalámbricos",
					"Audífonos Bluetooth con estuche de carga.", "199900", "cat-electronica", 20),
			desactivado(producto("674000000000000000000006", "Teclado mecánico",
					"Teclado mecánico retroiluminado. Descontinuado.", "249900", "cat-electronica", 10)));
	}

	private final ProductoRepository productoRepository;
	private final PublicadorEventos publicadorEventos;

	public CargaProductosPrueba(ProductoRepository productoRepository, PublicadorEventos publicadorEventos) {
		this.productoRepository = productoRepository;
		this.publicadorEventos = publicadorEventos;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<Producto> productos = productos();
		int creados = 0;
		for (Producto producto : productos) {
			if (productoRepository.existsById(producto.getId())) {
				continue;
			}
			// insert y no save: si otro proceso lo creó justo ahora, falla en vez de pisarlo.
			Producto guardado = productoRepository.insert(producto);
			publicadorEventos.productoCreado(guardado);
			creados++;
		}
		log.info("Productos de prueba: {} creados, {} ya existían", creados, productos.size() - creados);
	}

	private static Producto producto(String id, String nombre, String descripcion, String precio, String categoria,
			int stock) {
		return new Producto(id, nombre, descripcion, new BigDecimal(precio), categoria, stock, List.of());
	}

	private static Producto desactivado(Producto producto) {
		producto.desactivar();
		return producto;
	}

}

package co.edu.uis.catalogo.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import co.edu.uis.catalogo.model.Categoria;
import co.edu.uis.catalogo.repository.CategoriaRepository;

/**
 * Precarga las categorías del contrato (sección 2, {@code GET /categorias}) cada vez que arranca el
 * servicio. En esta entrega las categorías no se crean por la API: vienen precargadas.
 *
 * <p>Es idempotente: las categorías tienen ids fijos, y guardar una que ya existe la reemplaza en lugar
 * de duplicarla, así que no importa cuántas veces arranque el servicio.
 *
 * <p>Consecuencia: el servicio, y las pruebas que arrancan la aplicación completa, necesitan MongoDB
 * encendido. Si no puede escribir las categorías, el arranque falla.
 */
@Component
public class CargaCategorias implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CargaCategorias.class);

	private final CategoriaRepository categoriaRepository;

	/**
	 * Spring crea este componente al arrancar y le entrega el repositorio por el constructor
	 * (inyección de dependencias). Es la forma que se usa en todo el proyecto: ni {@code new} ni
	 * {@code @Autowired} sobre campos.
	 */
	public CargaCategorias(CategoriaRepository categoriaRepository) {
		this.categoriaRepository = categoriaRepository;
	}

	/** Spring lo llama una sola vez, cuando la aplicación terminó de arrancar. */
	@Override
	public void run(ApplicationArguments args) {
		List<Categoria> categorias = List.of(
				new Categoria("cat-ropa", "ropa"),
				new Categoria("cat-hogar", "hogar"),
				new Categoria("cat-electronica", "electrónica"));
		categoriaRepository.saveAll(categorias);
		log.info("Categorías precargadas: {}", categorias.size());
	}

}

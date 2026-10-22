package co.edu.uis.catalogo.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.dto.ProductoListadoResponse;
import co.edu.uis.catalogo.dto.ProductoRequest;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.evento.PublicadorEventos;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.CategoriaRepository;
import co.edu.uis.catalogo.repository.ProductoRepository;
import co.edu.uis.catalogo.repository.ProductoStockRepository;

/**
 * Lógica de negocio de los productos: las reglas del contrato, el 404 cuando un producto no existe y
 * el soft delete. Los controllers nunca hablan con los repositorios; siempre pasan por aquí.
 */
@Service
public class ProductoService {

	/** Valor de {@code ?activo=} que trae los productos sin importar su estado. */
	private static final String TODOS = "todos";

	private final ProductoRepository productoRepository;

	/** Para validar que la categoría de un producto exista (B2). */
	private final CategoriaRepository categoriaRepository;

	/** Para el descuento atómico de stock, que MongoRepository no sabe hacer. */
	private final ProductoStockRepository productoStockRepository;

	/** Avisa a Búsqueda y a Carro de cada cambio del catálogo (contrato, sección 5). */
	private final PublicadorEventos publicadorEventos;

	public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
			ProductoStockRepository productoStockRepository, PublicadorEventos publicadorEventos) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.productoStockRepository = productoStockRepository;
		this.publicadorEventos = publicadorEventos;
	}

	/**
	 * GET /productos: una página de productos, con filtro opcional por categoría y por estado
	 * (Historias 2 y 8).
	 *
	 * @param pagina la primera página es la 1, como en el contrato.
	 * @param categoria id de categoría, o {@code null} o vacío para no filtrar.
	 * @param activo {@code true}, {@code false} o {@code "todos"}.
	 */
	public ProductoListadoResponse listar(int pagina, int tamanoPagina, String categoria, String activo) {
		validarFiltroActivo(activo);

		// PageRequest cuenta desde 0 y el contrato desde 1: aquí se hace la resta, una sola vez.
		Pageable paginado = PageRequest.of(pagina - 1, tamanoPagina);

		return ProductoListadoResponse.desde(buscarSegunFiltros(categoria, activo, paginado),
				pagina, tamanoPagina);
	}

	/** Elige el método del repositorio según los filtros que hayan llegado. */
	private Page<Producto> buscarSegunFiltros(String categoria, String activo, Pageable paginado) {
		boolean todos = TODOS.equals(activo);
		boolean soloActivos = Boolean.parseBoolean(activo);
		boolean filtrarCategoria = (categoria != null) && !categoria.isBlank();

		if (!filtrarCategoria) {
			return todos ? productoRepository.findAll(paginado)
					: (soloActivos ? productoRepository.findByActivoTrue(paginado)
							: productoRepository.findByActivoFalse(paginado));
		}

		return todos ? productoRepository.findByCategoria(categoria, paginado)
				: (soloActivos ? productoRepository.findByActivoTrueAndCategoria(categoria, paginado)
						: productoRepository.findByActivoFalseAndCategoria(categoria, paginado));
	}

	/**
	 * El parámetro solo acepta tres valores. Se valida aquí y no con una anotación porque el mensaje
	 * de error tiene que nombrar los tres, y eso es más claro escrito que en una expresión regular.
	 */
	private void validarFiltroActivo(String activo) {
		if (!TODOS.equals(activo) && !"true".equals(activo) && !"false".equals(activo)) {
			throw new ValidacionFallidaException("activo: debe ser true, false o todos");
		}
	}

	/** GET /productos/{id}: devuelve el producto aunque esté inactivo (Historia 3). */
	public ProductoResponse obtenerPorId(String id) {
		return ProductoResponse.desde(buscarExistente(id));
	}

	/**
	 * DELETE: soft delete (Historia 5). Es idempotente: si ya estaba inactivo no guarda nada, y así
	 * tampoco se publica otro evento cuando B4 los conecte.
	 *
	 * @return {@code true} si estaba activo y se desactivó; {@code false} si ya estaba inactivo.
	 */
	public boolean desactivar(String id) {
		Producto producto = buscarExistente(id);

		if (!producto.isActivo()) {
			return false;
		}

		producto.desactivar();
		productoRepository.save(producto);
		publicadorEventos.productoDesactivado(id);
		return true;
	}

	/** POST: crea un producto. Nace activo y MongoDB le asigna el id al guardarlo (Historia 1). */
	public ProductoResponse crear(ProductoRequest datos) {
		validarCategoriaExiste(datos.categoria());

		Producto producto = new Producto(datos.nombre(), datos.descripcion(), datos.precio(),
				datos.categoria(), datos.stock(), datos.imagenes());

		// El evento sale DESPUÉS de guardar: si el guardado falla, nadie debe enterarse de un producto
		// que no existe.
		Producto guardado = productoRepository.save(producto);
		publicadorEventos.productoCreado(guardado);

		return ProductoResponse.desde(guardado);
	}

	/**
	 * PUT: reemplaza el producto completo (Historia 4). No cambia {@code id} ni {@code activo}: sobre un
	 * producto inactivo responde 200 y el producto sigue inactivo.
	 */
	public ProductoResponse actualizar(String id, ProductoRequest datos) {
		Producto producto = buscarExistente(id);
		validarCategoriaExiste(datos.categoria());

		producto.actualizar(datos.nombre(), datos.descripcion(), datos.precio(), datos.categoria(),
				datos.stock(), datos.imagenes());

		Producto guardado = productoRepository.save(producto);
		publicadorEventos.productoActualizado(guardado);
		return ProductoResponse.desde(guardado);
	}

	/**
	 * Reactiva un producto desactivado (Historia 7). Es idempotente: si ya estaba activo, devuelve el
	 * producto sin guardar nada, igual que hace DELETE con uno que ya estaba inactivo.
	 */
	public ProductoResponse activar(String id) {
		Producto producto = buscarExistente(id);

		if (producto.isActivo()) {
			return ProductoResponse.desde(producto);
		}

		producto.activar();
		Producto guardado = productoRepository.save(producto);
		publicadorEventos.productoReactivado(guardado);
		return ProductoResponse.desde(guardado);
	}

	/**
	 * Descuenta stock de varios productos al confirmar un checkout (contrato v2.3, sección 2).
	 *
	 * <p>La regla es <b>o pasa todo, o no pasa nada</b>. Como esta entrega corre un MongoDB de un solo
	 * nodo, no hay transacciones entre documentos: si un ítem no alcanza, hay que devolver a mano lo que
	 * ya se descontó. Por eso se va guardando en {@code descontados} lo que va saliendo bien.
	 *
	 * <p>Los eventos de actualización solo se publican después de que todos los descuentos hayan
	 * terminado con éxito. Si algún descuento falla, se revierten los anteriores y no se publica
	 * ningún evento para esa operación fallida.
	 */
	public DescuentoStockResponse descontarStock(DescuentoStockRequest peticion) {
		validarSinRepetidos(peticion.items());

		List<DescuentoStockRequest.Item> descontados = new ArrayList<>();
		List<DescuentoStockResponse.Item> resultado = new ArrayList<>();

		for (DescuentoStockRequest.Item item : peticion.items()) {
			Producto producto = buscarExistente(item.id());

			if (!producto.isActivo()) {
				devolver(descontados);
				throw new ValidacionFallidaException(
						"El producto " + item.id() + " está desactivado y no se puede vender");
			}

			Integer restante = productoStockRepository.descontar(item.id(), item.cantidad());

			if (restante == null) {
				devolver(descontados);
				throw new StockInsuficienteException("No hay stock suficiente del producto " + item.id()
						+ ": se pidieron " + item.cantidad() + " unidades");
			}

			descontados.add(item);
			resultado.add(new DescuentoStockResponse.Item(item.id(), restante));
		}

		// Solo publicamos después de que todos los descuentos hayan tenido éxito.
		for (DescuentoStockRequest.Item item : descontados) {
			productoRepository.findById(item.id()).ifPresent(publicadorEventos::productoActualizado);
		}

		return new DescuentoStockResponse(resultado);
	}

	/**
	 * La categoría debe ser el id de una categoría existente (contrato, sección 3). Es 400 y no 404
	 * porque lo que falla es un dato del producto que se está enviando, no la dirección de la petición.
	 */
	private void validarCategoriaExiste(String categoria) {
		if (!categoriaRepository.existsById(categoria)) {
			throw new ValidacionFallidaException("La categoría '" + categoria + "' no existe");
		}
	}

	/** Deshace los descuentos de una petición que terminó fallando. */
	private void devolver(List<DescuentoStockRequest.Item> descontados) {
		for (DescuentoStockRequest.Item item : descontados) {
			productoStockRepository.devolver(item.id(), item.cantidad());
		}
	}

	/**
	 * Dos líneas con el mismo producto harían dos descuentos separados y la reversión quedaría a medias.
	 * Es más simple rechazarlo: que Carro sume las cantidades antes de enviar.
	 */
	private void validarSinRepetidos(List<DescuentoStockRequest.Item> items) {
		Set<String> vistos = new HashSet<>();
		for (DescuentoStockRequest.Item item : items) {
			if (!vistos.add(item.id())) {
				throw new ValidacionFallidaException("El producto " + item.id() + " viene repetido en la lista");
			}
		}
	}

	/**
	 * Busca un producto por id, esté activo o no. Si no existe lanza
	 * {@link ProductoNoEncontradoException}, que el manejador global convierte en 404. La usan GET por
	 * id, PUT y DELETE, para que la regla "404 solo si el id no existe" esté escrita una sola vez.
	 */
	private Producto buscarExistente(String id) {
		// findById devuelve un Optional: una "caja" que viene vacía si el producto no existe.
		return productoRepository.findById(id)
				.orElseThrow(() -> new ProductoNoEncontradoException(id));
	}

}
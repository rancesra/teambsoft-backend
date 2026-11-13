package co.edu.uis.catalogo.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import co.edu.uis.catalogo.error.ReferenciaEnUsoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.evento.PublicadorEventos;
import co.edu.uis.catalogo.model.DescuentoStock;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.CategoriaRepository;
import co.edu.uis.catalogo.repository.DescuentoStockRepository;
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

	/** Para la idempotencia por referencia y la devolución de stock (v2.4). */
	private final DescuentoStockRepository descuentoStockRepository;

	/** Avisa a Búsqueda y a Carro de cada cambio del catálogo (contrato, sección 5). */
	private final PublicadorEventos publicadorEventos;

	public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
			ProductoStockRepository productoStockRepository, DescuentoStockRepository descuentoStockRepository,
			PublicadorEventos publicadorEventos) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.productoStockRepository = productoStockRepository;
		this.descuentoStockRepository = descuentoStockRepository;
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
	 * Descuenta stock de varios productos al confirmar un checkout (contrato v2.4, sección 2).
	 *
	 * <p>La regla es <b>o pasa todo, o no pasa nada</b>. Como esta entrega corre un MongoDB de un solo
	 * nodo, no hay transacciones entre documentos: si un ítem no alcanza, hay que devolver a mano lo que
	 * ya se descontó.
	 *
	 * <p>El orden de los pasos importa:
	 * <ol>
	 * <li>Si viene {@code referencia} y ya se usó, se responde según el registro, sin tocar el stock
	 * (idempotencia).</li>
	 * <li>Se valida que todos los productos existan y estén activos <b>antes</b> de descontar nada, para
	 * que un 404 o un 400 nunca deje un descuento a medias.</li>
	 * <li>Se descuenta cada ítem. Si alguno no alcanza, se revierten los que sí, y el 409 lista todos
	 * los que no alcanzaron, no solo el primero.</li>
	 * <li>Si viene {@code referencia}, se registra. Un descuento que falla no llega aquí, así que no
	 * registra la referencia y se puede reintentar con la misma.</li>
	 * <li>Por último, los eventos. Si algo falló antes, no se publica ninguno.</li>
	 * </ol>
	 */
	public DescuentoStockResponse descontarStock(DescuentoStockRequest peticion) {
		validarSinRepetidos(peticion.items());
		String referencia = peticion.referencia();

		if (referencia != null) {
			Optional<DescuentoStockResponse> previa = respuestaPrevia(referencia, peticion.items());
			if (previa.isPresent()) {
				return previa.get();
			}
		}

		validarVendibles(peticion.items());
		List<DescuentoStockResponse.Item> resultado = descontarTodos(peticion.items());

		if (referencia != null && !registrar(referencia, peticion.items(), resultado)) {
			// Otra petición con la misma referencia se registró mientras esta descontaba. Esta deshace
			// su descuento y responde lo que diga el registro de la otra, como un reintento cualquiera.
			devolver(peticion.items());
			return respuestaPrevia(referencia, peticion.items())
					.orElseThrow(() -> new IllegalStateException("La referencia " + referencia
							+ " no se pudo registrar ni encontrar"));
		}

		for (DescuentoStockRequest.Item item : peticion.items()) {
			productoRepository.findById(item.id()).ifPresent(publicadorEventos::productoActualizado);
		}

		return new DescuentoStockResponse(referencia, resultado);
	}

	/**
	 * Devuelve el stock de un descuento hecho con {@code referencia} (contrato v2.4, sección 2). Lo
	 * llama Carro si falla guardar el pedido después de descontar.
	 *
	 * <p>Es idempotente: si la referencia no existe o ya se devolvió, no hace nada. Devuelve las unidades
	 * aunque el producto se haya desactivado entretanto, porque las unidades existen igual. Publica
	 * {@code producto.actualizado} por cada producto, como cualquier cambio de stock.
	 */
	public void devolverStock(String referencia) {
		descuentoStockRepository.marcarDevuelto(referencia).ifPresent(descuento -> {
			for (DescuentoStock.Linea linea : descuento.getItems()) {
				productoStockRepository.devolver(linea.producto(), linea.cantidad());
			}
			for (DescuentoStock.Linea linea : descuento.getItems()) {
				productoRepository.findById(linea.producto()).ifPresent(publicadorEventos::productoActualizado);
			}
		});
	}

	/**
	 * Qué responder si la referencia ya se usó: la misma respuesta de la primera vez, si los ítems son
	 * los mismos, o 409 {@code REFERENCIA_EN_USO} si son otros o si ya se devolvió.
	 *
	 * @return vacío si la referencia no se ha usado.
	 */
	private Optional<DescuentoStockResponse> respuestaPrevia(String referencia,
			List<DescuentoStockRequest.Item> items) {
		Optional<DescuentoStock> registro = descuentoStockRepository.buscar(referencia);
		if (registro.isEmpty()) {
			return Optional.empty();
		}

		DescuentoStock descuento = registro.get();
		if (descuento.isDevuelto()) {
			throw new ReferenciaEnUsoException("La referencia " + referencia
					+ " ya se devolvió. Para comprar de nuevo, usa una referencia nueva");
		}
		if (!descuento.tieneLosMismosItems(cantidadesPorProducto(items))) {
			throw new ReferenciaEnUsoException("La referencia " + referencia
					+ " ya se usó con otros productos o cantidades");
		}

		List<DescuentoStockResponse.Item> respuesta = descuento.getItems().stream()
				.map(linea -> new DescuentoStockResponse.Item(linea.producto(), linea.stockRestante()))
				.toList();
		return Optional.of(new DescuentoStockResponse(referencia, respuesta));
	}

	/** Todos los productos tienen que existir (si no, 404) y estar activos (si no, 400). */
	private void validarVendibles(List<DescuentoStockRequest.Item> items) {
		for (DescuentoStockRequest.Item item : items) {
			if (!buscarExistente(item.id()).isActivo()) {
				throw new ValidacionFallidaException(
						"El producto " + item.id() + " está desactivado y no se puede vender");
			}
		}
	}

	/**
	 * Descuenta cada ítem con la operación atómica de {@link ProductoStockRepository}. Si alguno no
	 * alcanza, sigue revisando los demás para poder decir en el 409 cuáles fallaron, y al final revierte
	 * los que sí se descontaron.
	 */
	private List<DescuentoStockResponse.Item> descontarTodos(List<DescuentoStockRequest.Item> items) {
		List<DescuentoStockRequest.Item> descontados = new ArrayList<>();
		List<DescuentoStockResponse.Item> resultado = new ArrayList<>();
		List<StockInsuficienteException.Faltante> faltantes = new ArrayList<>();

		for (DescuentoStockRequest.Item item : items) {
			Integer restante = productoStockRepository.descontar(item.id(), item.cantidad());

			if (restante == null) {
				int stockActual = productoRepository.findById(item.id()).map(Producto::getStock).orElse(0);
				faltantes.add(new StockInsuficienteException.Faltante(item.id(), item.cantidad(), stockActual));
			} else {
				descontados.add(item);
				resultado.add(new DescuentoStockResponse.Item(item.id(), restante));
			}
		}

		if (!faltantes.isEmpty()) {
			devolver(descontados);
			throw new StockInsuficienteException(faltantes);
		}
		return resultado;
	}

	/** @return {@code false} si otra petición ya registró esa referencia. */
	private boolean registrar(String referencia, List<DescuentoStockRequest.Item> items,
			List<DescuentoStockResponse.Item> resultado) {
		List<DescuentoStock.Linea> lineas = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			lineas.add(new DescuentoStock.Linea(items.get(i).id(), items.get(i).cantidad(),
					resultado.get(i).stockRestante()));
		}
		return descuentoStockRepository.registrar(new DescuentoStock(referencia, lineas));
	}

	private static Map<String, Integer> cantidadesPorProducto(List<DescuentoStockRequest.Item> items) {
		return items.stream()
				.collect(Collectors.toMap(DescuentoStockRequest.Item::id, DescuentoStockRequest.Item::cantidad));
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

	/**
	 * Deshace los descuentos de una petición que terminó fallando, o que llegó segunda con una
	 * referencia que otra petición registró primero.
	 */
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
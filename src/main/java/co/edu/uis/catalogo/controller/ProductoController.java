package co.edu.uis.catalogo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.dto.DevolucionStockRequest;
import co.edu.uis.catalogo.dto.ProductoListadoResponse;
import co.edu.uis.catalogo.dto.ProductoRequest;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.service.ProductoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Endpoints de productos (contrato, sección 2). El servicio expone {@code /productos}; el prefijo
 * {@code /api/catalogo} lo agrega Kong.
 *
 * <p>Aquí no se manejan errores: el service lanza excepciones y
 * {@link co.edu.uis.catalogo.error.ManejadorGlobalErrores} las convierte en el formato del contrato.
 */
@RestController
@RequestMapping("/productos")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	/**
	 * Los valores por defecto están aquí y no en el service porque son parte del contrato de la API:
	 * quien no envíe nada recibe la primera página, de 20, solo con productos activos. Así el listado
	 * se comporta igual que antes de que existiera {@code ?activo=}.
	 */
	@GetMapping
	public ProductoListadoResponse listar(
			@RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser 1 o más") int pagina,
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "debe ser 1 o más")
			@Max(value = 100, message = "no puede pasar de 100") int tamanoPagina,
			@RequestParam(required = false) String categoria,
			@RequestParam(defaultValue = "true") String activo) {

		return productoService.listar(pagina, tamanoPagina, categoria, activo);
	}

	/**
	 * GET /productos/{id}: devuelve el producto aunque esté inactivo, con {@code activo: false}. El
	 * 404 es solo para un id que no existe. Así el equipo C puede distinguir "lo retiraron" de "ese id
	 * nunca existió" cuando valida antes de agregar al carrito.
	 */
	@GetMapping("/{id}")
	public ProductoResponse obtenerPorId(@PathVariable String id) {
		return productoService.obtenerPorId(id);
	}

	/** DELETE: 204 sin cuerpo, porque el método devuelve void. El borrado es suave, no físico. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desactivar(@PathVariable String id) {
		productoService.desactivar(id);
	}

	/**
	 * {@code @Valid} es lo que dispara las validaciones del record. Sin esa anotación, las reglas
	 * {@code @NotBlank} y compañía quedan escritas pero nadie las ejecuta.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoResponse crear(@Valid @RequestBody ProductoRequest datos) {
		return productoService.crear(datos);
	}

	@PutMapping("/{id}")
	public ProductoResponse actualizar(@PathVariable String id, @Valid @RequestBody ProductoRequest datos) {
		return productoService.actualizar(id, datos);
	}

	/** POST y no PUT: no reemplaza un recurso, dispara una acción sobre él (contrato v2.3). */
	@PostMapping("/{id}/activar")
	public ProductoResponse activar(@PathVariable String id) {
		return productoService.activar(id);
	}

	/**
	 * Lo llama Carro al confirmar el checkout, no el navegador. Va antes que {@code /{id}/activar} en
	 * importancia, pero no choca con él: Spring prefiere siempre la ruta literal sobre la que tiene
	 * variable, así que {@code descontar-stock} nunca se confunde con un id.
	 */
	@PostMapping("/descontar-stock")
	public DescuentoStockResponse descontarStock(@Valid @RequestBody DescuentoStockRequest peticion) {
		return productoService.descontarStock(peticion);
	}

	/**
	 * Lo llama Carro si falla guardar el pedido después de descontar (contrato v2.4). Responde 204
	 * siempre que la petición sea válida, aunque la referencia no exista o ya se haya devuelto: es
	 * idempotente, y a Carro le basta con saber que ese stock ya no está descontado.
	 */
	@PostMapping("/devolver-stock")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void devolverStock(@Valid @RequestBody DevolucionStockRequest peticion) {
		productoService.devolverStock(peticion.referencia());
	}

}
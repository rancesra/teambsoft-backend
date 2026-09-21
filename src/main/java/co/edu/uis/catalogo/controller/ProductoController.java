package co.edu.uis.catalogo.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uis.catalogo.service.ProductoService;

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

	// B2 (POST, PUT) y B3 (GET, GET por id, DELETE) agregan sus endpoints aquí.

}
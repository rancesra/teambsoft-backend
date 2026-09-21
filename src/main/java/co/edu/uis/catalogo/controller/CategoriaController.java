package co.edu.uis.catalogo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.uis.catalogo.dto.CategoriaResponse;
import co.edu.uis.catalogo.service.CategoriaService;

/**
 * Endpoints de categorías (contrato, sección 2). El servicio expone {@code /categorias}; el prefijo
 * {@code /api/catalogo} lo agrega Kong.
 *
 * <p>Como todos los controllers, solo traduce HTTP: recibe la petición, llama al service y devuelve
 * DTOs, que Spring convierte en JSON. No contiene lógica de negocio.
 */
@RestController
@RequestMapping("/categorias")
public class CategoriaController {

	private final CategoriaService categoriaService;

	public CategoriaController(CategoriaService categoriaService) {
		this.categoriaService = categoriaService;
	}

	/** {@code GET /categorias} → 200 con todas las categorías (Historia 6). */
	@GetMapping
	public List<CategoriaResponse> listar() {
		return categoriaService.listar();
	}

}
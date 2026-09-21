package co.edu.uis.catalogo.dto;

import co.edu.uis.catalogo.model.Categoria;

/**
 * Categoría tal como la devuelve la API (contrato, sección 3, "Modelo de Categoría"). Separa lo que ve
 * el cliente de la entidad que se guarda en MongoDB.
 */
public record CategoriaResponse(String id, String nombre) {

	/** Convierte la entidad en el formato de la API. */
	public static CategoriaResponse desde(Categoria categoria) {
		return new CategoriaResponse(categoria.getId(), categoria.getNombre());
	}

}
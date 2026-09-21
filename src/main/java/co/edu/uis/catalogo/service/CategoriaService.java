package co.edu.uis.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import co.edu.uis.catalogo.dto.CategoriaResponse;
import co.edu.uis.catalogo.repository.CategoriaRepository;

/** Lógica de negocio de las categorías. En esta entrega solo las lista (Historia 6). */
@Service
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;

	public CategoriaService(CategoriaRepository categoriaRepository) {
		this.categoriaRepository = categoriaRepository;
	}

	/** Devuelve todas las categorías en el formato de la API. */
	public List<CategoriaResponse> listar() {
		return categoriaRepository.findAll().stream()
				.map(CategoriaResponse::desde)
				.toList();
	}

}
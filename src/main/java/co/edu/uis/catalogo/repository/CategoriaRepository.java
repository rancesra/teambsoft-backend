package co.edu.uis.catalogo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.uis.catalogo.model.Categoria;

/**
 * Acceso a la colección {@code categorias}. Es una interfaz sin implementación: Spring Data la genera
 * al arrancar, con los métodos heredados de {@link MongoRepository} ({@code findAll},
 * {@code existsById}, {@code saveAll}...).
 */
public interface CategoriaRepository extends MongoRepository<Categoria, String> {
}
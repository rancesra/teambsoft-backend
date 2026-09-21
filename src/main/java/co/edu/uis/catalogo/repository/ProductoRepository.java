package co.edu.uis.catalogo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.uis.catalogo.model.Producto;

/**
 * Acceso a la colección {@code productos}. Spring Data genera la implementación al arrancar.
 *
 * <p>Para una consulta nueva basta con declarar un método con el nombre adecuado: Spring Data genera
 * la consulta a partir de ese nombre. Por ejemplo, {@code findByActivoTrue(Pageable)} devolvería una
 * página de productos activos.
 */
public interface ProductoRepository extends MongoRepository<Producto, String> {
}
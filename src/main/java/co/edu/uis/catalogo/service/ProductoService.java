package co.edu.uis.catalogo.service;

import org.springframework.stereotype.Service;

import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.CategoriaRepository;
import co.edu.uis.catalogo.repository.ProductoRepository;

/**
 * Lógica de negocio de los productos: las reglas del contrato, el 404 cuando un producto no existe y
 * el soft delete. Los controllers nunca hablan con los repositorios; siempre pasan por aquí.
 */
@Service
public class ProductoService {

	private final ProductoRepository productoRepository;

	/** Para validar que la categoría de un producto exista (B2). */
	private final CategoriaRepository categoriaRepository;

	public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
	}

	// B2 (crear, actualizar) y B3 (listar, obtener, desactivar) agregan sus métodos aquí.

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

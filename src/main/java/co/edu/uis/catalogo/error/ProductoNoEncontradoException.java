package co.edu.uis.catalogo.error;

/**
 * Se lanza cuando se pide un producto cuyo id no existe. {@link ManejadorGlobalErrores} la convierte
 * en 404 {@code PRODUCTO_NO_ENCONTRADO}.
 *
 * <p>Un producto inactivo sí existe: pedirlo no lanza esta excepción (contrato, sección 2).
 */
public class ProductoNoEncontradoException extends RuntimeException {

	public ProductoNoEncontradoException(String id) {
		super("No existe un producto con id " + id);
	}

}
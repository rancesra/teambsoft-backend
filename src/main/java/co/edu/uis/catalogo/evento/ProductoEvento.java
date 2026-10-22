package co.edu.uis.catalogo.evento;

import java.math.BigDecimal;

import co.edu.uis.catalogo.model.Producto;

/**
 * Cuerpo de los eventos {@code producto.creado}, {@code producto.actualizado} y
 * {@code producto.reactivado} (contrato v2.3, sección 5).
 *
 * <p>Lleva {@code descripcion} y {@code stock} porque el Equipo A los necesita: sin la descripción no
 * puede indexar búsqueda de texto completo, y lo marcó como bloqueante en su contrato.
 *
 * <p>No incluye {@code activo}: quien quiera saber que un producto volvió al catálogo se entera por
 * la routing key {@code producto.reactivado}, no por un campo dentro del mensaje.
 */
public record ProductoEvento(String id, String nombre, String descripcion, BigDecimal precio,
		String categoria, int stock) {

	public static ProductoEvento desde(Producto producto) {
		return new ProductoEvento(producto.getId(), producto.getNombre(), producto.getDescripcion(),
				producto.getPrecio(), producto.getCategoria(), producto.getStock());
	}

}

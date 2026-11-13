package co.edu.uis.catalogo.evento;

import java.math.BigDecimal;

import co.edu.uis.catalogo.model.Producto;

/**
 * Cuerpo de los eventos {@code producto.creado}, {@code producto.actualizado} y
 * {@code producto.reactivado} (contrato v2.4, sección 5).
 *
 * <p>Lleva {@code descripcion} y {@code stock} porque el Equipo A los necesita: sin la descripción no
 * puede indexar búsqueda de texto completo, y lo marcó como bloqueante en su contrato (v2.3).
 *
 * <p>Desde la v2.4 lleva también:
 * <ul>
 * <li>{@code activo}: el estado del producto en el momento del evento. Hace falta porque
 * {@code producto.actualizado} no implica que el producto esté activo: un PUT sobre un producto
 * desactivado publica {@code actualizado} y el producto sigue inactivo. Sin este campo, el consumidor
 * tendría que suponerlo, y Búsqueda lo suponía mal.</li>
 * <li>{@code fecha}: el instante en que se publicó, en ISO-8601 UTC. Le sirve al consumidor para
 * descartar un evento más viejo que el último que ya aplicó a ese producto.</li>
 * </ul>
 */
public record ProductoEvento(String id, String nombre, String descripcion, BigDecimal precio,
		String categoria, int stock, boolean activo, String fecha) {

	public static ProductoEvento desde(Producto producto, String fecha) {
		return new ProductoEvento(producto.getId(), producto.getNombre(), producto.getDescripcion(),
				producto.getPrecio(), producto.getCategoria(), producto.getStock(), producto.isActivo(), fecha);
	}

}

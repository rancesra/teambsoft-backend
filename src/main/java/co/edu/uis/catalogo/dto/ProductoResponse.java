package co.edu.uis.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

import co.edu.uis.catalogo.model.Producto;

/**
 * Producto tal como lo devuelve la API: los campos del contrato (sección 3), en el mismo orden. Lo
 * usan POST, PUT y los dos GET. La API nunca devuelve la entidad {@link Producto} directamente, para
 * que un cambio en la base de datos no cambie el contrato sin querer.
 */
public record ProductoResponse(String id, String nombre, String descripcion, BigDecimal precio, String categoria,
		int stock, List<String> imagenes, boolean activo) {

	/** Convierte la entidad en el formato de la API. */
	public static ProductoResponse desde(Producto producto) {
		return new ProductoResponse(producto.getId(), producto.getNombre(), producto.getDescripcion(),
				producto.getPrecio(), producto.getCategoria(), producto.getStock(), producto.getImagenes(),
				producto.isActivo());
	}

}
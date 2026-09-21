package co.edu.uis.catalogo.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Producto del catálogo, guardado en la colección {@code productos} de MongoDB. Es el documento
 * principal del servicio (contrato, sección 3).
 *
 * <p><b>No tiene setters, a propósito.</b> Solo cambia de tres formas, una por cada operación de
 * escritura del contrato:
 * <ul>
 * <li>el constructor con datos → POST (el producto nace activo);</li>
 * <li>{@link #actualizar} → PUT (reemplaza los datos editables y no toca {@code id} ni {@code activo});</li>
 * <li>{@link #desactivar()} → DELETE (soft delete: el único método que cambia {@code activo}).</li>
 * </ul>
 * Así las reglas del contrato quedan protegidas en el código y no solo escritas en el documento.
 *
 * <p>El índice compuesto {@code activo_categoria} acelera el listado, que siempre filtra por
 * {@code activo} y a veces por {@code categoria}. Spring Data lo crea al arrancar
 * ({@code auto-index-creation} en {@code application.yml}).
 */
@Document(collection = "productos")
@CompoundIndex(name = "activo_categoria", def = "{'activo': 1, 'categoria': 1}")
public class Producto {

	/** Lo genera MongoDB (un ObjectId) al guardar el producto por primera vez; antes es {@code null}. */
	@Id
	private String id;
	private String nombre;
	private String descripcion;

	/**
	 * Precio exacto: {@code BigDecimal} en Java y {@code Decimal128} en MongoDB (ver
	 * {@code application.yml}). Se compara con {@code compareTo}, no con {@code equals}.
	 */
	private BigDecimal precio;

	/** Id de la categoría (por ejemplo {@code cat-ropa}), no su nombre. */
	private String categoria;

	private int stock;

	/** URLs de las imágenes. Nunca es {@code null}: como mínimo, una lista vacía. */
	private List<String> imagenes = new ArrayList<>();

	/** {@code true} al crearse; solo {@link #desactivar()} lo cambia. */
	private boolean activo = true;

	/**
	 * Lo usa Spring Data para reconstruir el producto al leer de MongoDB. El código del servicio usa el
	 * constructor con datos.
	 */
	public Producto() {
	}

	/** Crea un producto nuevo (POST). Nace activo y sin id: el id lo asigna MongoDB al guardarlo. */
	public Producto(String nombre, String descripcion, BigDecimal precio, String categoria, int stock,
			List<String> imagenes) {
		asignarDatos(nombre, descripcion, precio, categoria, stock, imagenes);
	}

	/**
	 * Reemplaza todos los datos editables, porque PUT reemplaza el producto completo. No cambia
	 * {@code id} ni {@code activo}: un producto inactivo sigue inactivo después de actualizarse
	 * (contrato, sección 2).
	 */
	public void actualizar(String nombre, String descripcion, BigDecimal precio, String categoria, int stock,
			List<String> imagenes) {
		asignarDatos(nombre, descripcion, precio, categoria, stock, imagenes);
	}

	/**
	 * Soft delete (DELETE): marca el producto como inactivo sin borrarlo de la base. Llamarlo sobre un
	 * producto que ya está inactivo no cambia nada.
	 */
	public void desactivar() {
		this.activo = false;
	}

	/** Compartido por el constructor y {@link #actualizar}, para no repetir las mismas asignaciones. */
	private void asignarDatos(String nombre, String descripcion, BigDecimal precio, String categoria, int stock,
			List<String> imagenes) {
		this.nombre = nombre;
		this.descripcion = descripcion;
		this.precio = precio;
		this.categoria = categoria;
		this.stock = stock;
		this.imagenes = (imagenes != null) ? imagenes : new ArrayList<>();
	}

	public String getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public String getCategoria() {
		return categoria;
	}

	public int getStock() {
		return stock;
	}

	public List<String> getImagenes() {
		return imagenes;
	}

	public boolean isActivo() {
		return activo;
	}

}
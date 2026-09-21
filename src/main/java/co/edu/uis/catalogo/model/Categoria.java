package co.edu.uis.catalogo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Categoría de productos, guardada en la colección {@code categorias} de MongoDB (contrato, sección 3).
 *
 * <p>En esta entrega es de solo lectura: se precarga al arrancar
 * ({@link co.edu.uis.catalogo.config.CargaCategorias}) y por eso no tiene setters.
 */
@Document(collection = "categorias")
public class Categoria {

	/**
	 * Id fijo del contrato, por ejemplo {@code cat-ropa}. Es el valor que guarda
	 * {@link Producto#getCategoria()}. En MongoDB se guarda como {@code _id}.
	 */
	@Id
	private String id;
	private String nombre;

	/** Lo usa Spring Data para crear el objeto al leer de MongoDB. */
	public Categoria() {
	}

	/** Crea una categoría; se usa en la carga inicial. */
	public Categoria(String id, String nombre) {
		this.id = id;
		this.nombre = nombre;
	}

	public String getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

}
package co.edu.uis.catalogo.repository;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import co.edu.uis.catalogo.model.Producto;

/**
 * Operaciones de stock que no se pueden hacer con {@link ProductoRepository}.
 *
 * <p>{@code ProductoRepository} hereda de {@code MongoRepository}, que solo sabe leer y guardar
 * documentos completos. Para descontar stock eso no sirve: entre leer el producto, restar en Java y
 * guardarlo cabe otra petición haciendo lo mismo, y las dos pasarían. Eso es sobreventa.
 *
 * <p>Aquí se usa {@code MongoTemplate}, que permite mandarle a MongoDB una operación condicionada.
 * MongoDB garantiza que esa operación es atómica <b>sobre un documento</b>: nadie más lo toca
 * mientras se ejecuta.
 */
@Repository
public class ProductoStockRepository {

	private final MongoTemplate mongoTemplate;

	public ProductoStockRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/**
	 * Descuenta {@code cantidad} unidades solo si el producto está activo y le quedan al menos esas
	 * unidades. Devuelve el stock que quedó, o {@code null} si no alcanzó.
	 *
	 * <p>La condición viaja dentro de la misma operación que la resta, y ahí está todo el truco: si dos
	 * checkouts piden a la vez las últimas 3 unidades, MongoDB ejecuta uno y para cuando llega el otro
	 * el documento ya no cumple {@code stock >= cantidad}, así que no modifica nada y devuelve null.
	 */
	public Integer descontar(String id, int cantidad) {
		Query consulta = Query.query(Criteria.where("_id").is(id)
				.and("activo").is(true)
				.and("stock").gte(cantidad));
		Update resta = new Update().inc("stock", -cantidad);

		// returnNew(true) devuelve el documento ya actualizado, así no hay que volver a leerlo.
		Producto actualizado = mongoTemplate.findAndModify(consulta, resta,
				FindAndModifyOptions.options().returnNew(true), Producto.class);

		return (actualizado == null) ? null : actualizado.getStock();
	}

	/**
	 * Devuelve unidades al stock. Se usa para deshacer los descuentos de una petición que falló y para
	 * {@code devolver-stock} (v2.4). No pide que el producto esté activo: si lo desactivaron entretanto,
	 * las unidades existen igual.
	 */
	public void devolver(String id, int cantidad) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)),
				new Update().inc("stock", cantidad), Producto.class);
	}

}
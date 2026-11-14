package co.edu.uis.catalogo.repository;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import co.edu.uis.catalogo.model.DescuentoStock;

/**
 * Acceso a los registros de descuento de stock (contrato v2.4).
 *
 * <p>Usa {@code MongoTemplate}, como {@link ProductoStockRepository}, porque las dos operaciones que
 * importan tienen que ser atómicas y {@code MongoRepository} no las ofrece: {@code save} reemplaza un
 * documento existente sin avisar, y aquí se necesita saber si ya existía.
 */
@Repository
public class DescuentoStockRepository {

	private final MongoTemplate mongoTemplate;

	public DescuentoStockRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public Optional<DescuentoStock> buscar(String referencia) {
		return Optional.ofNullable(mongoTemplate.findById(referencia, DescuentoStock.class));
	}

	/**
	 * Guarda el registro solo si la referencia no existe.
	 *
	 * <p>{@code insert} y no {@code save}: {@code insert} falla si ya hay un documento con ese
	 * {@code _id}, y {@code save} lo reemplazaría. Esa falla es la que avisa que otra petición con la
	 * misma referencia se registró primero.
	 *
	 * @return {@code true} si se guardó; {@code false} si la referencia ya estaba registrada.
	 */
	public boolean registrar(DescuentoStock descuento) {
		try {
			mongoTemplate.insert(descuento);
			return true;
		} catch (DuplicateKeyException e) {
			return false;
		}
	}

	/**
	 * Marca la referencia como devuelta, solo si todavía no lo estaba, y devuelve el registro.
	 *
	 * <p>La condición {@code devuelto = false} viaja dentro de la misma operación: si llegan dos
	 * devoluciones a la vez, MongoDB aplica una, y para cuando llega la otra el documento ya no cumple la
	 * condición. Así el stock se devuelve una sola vez.
	 *
	 * @return el registro, si se acaba de marcar; vacío si no existe o ya estaba devuelto.
	 */
	public Optional<DescuentoStock> marcarDevuelto(String referencia) {
		Query consulta = Query.query(Criteria.where("_id").is(referencia).and("devuelto").is(false));
		return Optional.ofNullable(
				mongoTemplate.findAndModify(consulta, new Update().set("devuelto", true), DescuentoStock.class));
	}

}

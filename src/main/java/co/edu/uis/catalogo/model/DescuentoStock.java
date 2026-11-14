package co.edu.uis.catalogo.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de un descuento de stock hecho con {@code referencia} (contrato v2.4, sección 2). Se guarda
 * en la colección {@code descuentos_stock}, uno por referencia.
 *
 * <p>Existe para dos cosas:
 * <ul>
 * <li><b>Idempotencia:</b> si Carro reintenta {@code descontar-stock} con la misma referencia, se
 * responde lo mismo que la primera vez sin descontar otra vez.</li>
 * <li><b>Devolución:</b> {@code devolver-stock} solo recibe la referencia; de aquí salen los productos
 * y las cantidades que hay que devolver.</li>
 * </ul>
 *
 * <p>La referencia es el {@code _id} del documento. Así MongoDB garantiza, con el índice único que ya
 * trae todo {@code _id}, que no puede haber dos registros con la misma referencia, aunque lleguen dos
 * peticiones a la vez.
 *
 * <p>Como {@link Producto}, no tiene setters. Solo se crea; el paso a devuelto lo hace
 * {@code DescuentoStockRepository} con una operación atómica en MongoDB, para que dos devoluciones
 * simultáneas no devuelvan el stock dos veces.
 */
@Document(collection = "descuentos_stock")
public class DescuentoStock {

	@Id
	private String referencia;

	private List<Linea> items;

	/** {@code true} después de {@code devolver-stock}. Una referencia devuelta queda cerrada. */
	private boolean devuelto;

	private Instant fecha;

	/** Lo usa Spring Data para reconstruir el registro al leer de MongoDB. */
	public DescuentoStock() {
	}

	public DescuentoStock(String referencia, List<Linea> items) {
		this.referencia = referencia;
		this.items = items;
		this.devuelto = false;
		this.fecha = Instant.now();
	}

	/**
	 * Dice si una petición trae exactamente los mismos productos y cantidades que este descuento, sin
	 * importar el orden. Si no, la referencia está en uso por otra compra (409 {@code REFERENCIA_EN_USO}).
	 *
	 * @param cantidades producto → cantidad pedida.
	 */
	public boolean tieneLosMismosItems(Map<String, Integer> cantidades) {
		Map<String, Integer> propias = items.stream()
				.collect(Collectors.toMap(Linea::producto, Linea::cantidad));
		return propias.equals(cantidades);
	}

	public String getReferencia() {
		return referencia;
	}

	public List<Linea> getItems() {
		return items;
	}

	public boolean isDevuelto() {
		return devuelto;
	}

	public Instant getFecha() {
		return fecha;
	}

	/**
	 * Un producto del descuento. Se llama {@code producto} y no {@code id} porque Spring Data trata
	 * cualquier campo {@code id} como identificador y lo guardaría como {@code _id}.
	 *
	 * @param stockRestante el que quedó después de descontar; se guarda para repetir la misma respuesta
	 *                      si Carro reintenta.
	 */
	public record Linea(String producto, int cantidad, int stockRestante) {

	}

}

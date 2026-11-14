package co.edu.uis.catalogo.error;

import java.util.List;

/**
 * No hay unidades suficientes de uno o más productos para completar el descuento. El manejador global
 * la convierte en 409 con el código {@code STOCK_INSUFICIENTE} (contrato, sección 4).
 *
 * <p>Es 409 y no 400 porque no es un error de formato de la petición: la misma petición puede fallar
 * ahora y funcionar en un minuto, cuando el stock cambie. Es un conflicto con el estado del recurso.
 *
 * <p>Desde la v2.4 lleva la lista de productos que no alcanzaron, con lo que se pidió y lo que había,
 * para que Carro le diga al cliente cuáles son sin tener que leer el mensaje.
 */
public class StockInsuficienteException extends RuntimeException {

	private final List<Faltante> items;

	public StockInsuficienteException(List<Faltante> items) {
		super("No hay stock suficiente para " + items.size() + (items.size() == 1 ? " producto" : " productos"));
		this.items = List.copyOf(items);
	}

	public List<Faltante> getItems() {
		return items;
	}

	/**
	 * Un producto que no alcanzó.
	 *
	 * @param cantidad la que se pidió.
	 * @param stock    la que había en ese momento.
	 */
	public record Faltante(String id, int cantidad, int stock) {

	}

}

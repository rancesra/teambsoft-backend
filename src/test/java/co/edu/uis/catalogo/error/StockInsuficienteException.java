package co.edu.uis.catalogo.error;

/**
 * No hay unidades suficientes de un producto para completar el descuento. El manejador global la
 * convierte en 409 con el código {@code STOCK_INSUFICIENTE} (contrato, sección 4).
 *
 * <p>Es 409 y no 400 porque no es un error de formato de la petición: la misma petición puede fallar
 * ahora y funcionar en un minuto, cuando el stock cambie. Es un conflicto con el estado del recurso.
 */
public class StockInsuficienteException extends RuntimeException {

	public StockInsuficienteException(String mensaje) {
		super(mensaje);
	}

}
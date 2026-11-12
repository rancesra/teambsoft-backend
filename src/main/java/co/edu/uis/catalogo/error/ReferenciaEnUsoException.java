package co.edu.uis.catalogo.error;

/**
 * La {@code referencia} de un descuento de stock ya se usó con otros productos o cantidades, o ya se
 * devolvió (contrato v2.4, sección 4). El manejador global la convierte en 409
 * {@code REFERENCIA_EN_USO}.
 *
 * <p>Una referencia identifica una sola compra. Si Carro necesita comprar de nuevo, usa una nueva.
 */
public class ReferenciaEnUsoException extends RuntimeException {

	public ReferenciaEnUsoException(String mensaje) {
		super(mensaje);
	}

}

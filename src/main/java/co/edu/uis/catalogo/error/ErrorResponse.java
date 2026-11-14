package co.edu.uis.catalogo.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato de error del contrato (sección 4). Jackson lo convierte en
 * {@code {"codigo": "...", "mensaje": "..."}}. Solo lo arma {@link ManejadorGlobalErrores}.
 *
 * <p>{@code items} solo aparece en {@code STOCK_INSUFICIENTE} (v2.4). Con {@code NON_NULL}, los demás
 * errores salen exactamente igual que antes, sin un {@code "items": null} que nadie pidió. El contrato
 * permite campos adicionales, pero nunca en lugar de {@code codigo} y {@code mensaje}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String codigo, String mensaje, List<StockInsuficienteException.Faltante> items) {

	/** Error sin campos adicionales: todos menos {@code STOCK_INSUFICIENTE}. */
	public ErrorResponse(String codigo, String mensaje) {
		this(codigo, mensaje, null);
	}

}

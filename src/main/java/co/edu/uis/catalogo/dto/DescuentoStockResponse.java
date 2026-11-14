package co.edu.uis.catalogo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lo que responde el descuento de stock cuando todo salió bien: cuánto quedó de cada producto.
 * Carro lo usa para mostrar el resultado sin tener que volver a consultar el catálogo.
 *
 * <p>{@code referencia} solo sale en el JSON si vino en la petición (contrato v2.4, sección 2):
 * {@code NON_NULL} omite el campo cuando vale {@code null}, en vez de escribir
 * {@code "referencia": null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DescuentoStockResponse(String referencia, List<Item> items) {

	/** Respuesta sin referencia, como las de la v2.3. */
	public DescuentoStockResponse(List<Item> items) {
		this(null, items);
	}

	public record Item(String id, int stockRestante) {

	}

}

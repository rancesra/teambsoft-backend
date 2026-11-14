package co.edu.uis.catalogo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía Carro al confirmar el checkout: todos los ítems en una sola petición (contrato,
 * sección 2). Viene todo junto y no ítem por ítem porque la regla es "o pasa todo, o no pasa nada",
 * y eso solo se puede garantizar si el servicio ve la lista completa.
 *
 * <p>{@code referencia} es opcional (v2.4). Identifica la compra (Carro envía su {@code checkoutId}) y
 * es lo que hace idempotente el descuento: si Carro no recibe la respuesta y reintenta con la misma
 * referencia, el stock no se descuenta dos veces. {@code @Size} no rechaza un {@code null}, así que
 * la petición sin referencia sigue siendo válida, como en la v2.3.
 */
public record DescuentoStockRequest(

		@Size(min = 1, max = 100, message = "debe tener entre 1 y 100 caracteres")
		String referencia,

		@NotEmpty(message = "no puede venir vacía")
		@Valid
		List<Item> items) {

	/** Petición sin referencia, como las de la v2.3. */
	public DescuentoStockRequest(List<Item> items) {
		this(null, items);
	}

	/**
	 * {@code @Valid} en la lista de arriba es lo que hace que Jakarta entre a validar cada Item. Sin
	 * esa anotación, solo se validaría que la lista no esté vacía y las cantidades pasarían sin revisar.
	 */
	public record Item(

			@NotBlank(message = "es obligatorio")
			String id,

			@NotNull(message = "es obligatoria")
			@Min(value = 1, message = "debe ser 1 o más")
			Integer cantidad) {

	}

}

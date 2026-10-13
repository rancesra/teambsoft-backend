package co.edu.uis.catalogo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Lo que envía Carro al confirmar el checkout: todos los ítems en una sola petición (contrato,
 * sección 2). Viene todo junto y no ítem por ítem porque la regla es "o pasa todo, o no pasa nada",
 * y eso solo se puede garantizar si el servicio ve la lista completa.
 */
public record DescuentoStockRequest(

		@NotEmpty(message = "no puede venir vacía")
		@Valid
		List<Item> items) {

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
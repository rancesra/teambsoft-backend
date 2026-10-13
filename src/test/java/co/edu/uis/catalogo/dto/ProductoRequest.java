package co.edu.uis.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en el cuerpo de POST y PUT. Es el mismo record para los dos porque PUT reemplaza
 * el producto completo (contrato, sección 2).
 *
 * <p>No tiene {@code id} ni {@code activo}: el contrato dice que se ignoran si el cliente los envía,
 * y la forma más simple de ignorarlos es no recibirlos. El {@code id} sale de la URL y {@code activo}
 * solo lo cambian DELETE y activar.
 *
 * <p>Los mensajes van fijos en español: si se dejan los de Jakarta, el mismo error llega traducido
 * según el idioma que pida el cliente, y el contrato exige un texto estable.
 */
public record ProductoRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 120, message = "no puede pasar de 120 caracteres")
		String nombre,

		String descripcion,

		@NotNull(message = "es obligatorio")
		@Positive(message = "debe ser mayor que 0")
		BigDecimal precio,

		@NotBlank(message = "es obligatoria")
		String categoria,

		// Integer y no int: con int, un cuerpo sin stock llegaría como 0 y @NotNull nunca saltaría.
		@NotNull(message = "es obligatorio")
		@PositiveOrZero(message = "no puede ser negativo")
		Integer stock,

		List<String> imagenes) {

}
package co.edu.uis.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que envía Carro a {@code POST /productos/devolver-stock} (contrato v2.4, sección 2) cuando falla
 * guardar el pedido después de haber descontado el stock.
 *
 * <p>Solo lleva la referencia, no los productos ni las cantidades: Catálogo ya sabe qué descontó con
 * ella, y así Carro no puede devolver más de lo que se llevó.
 */
public record DevolucionStockRequest(

		@NotBlank(message = "es obligatoria")
		@Size(max = 100, message = "no puede pasar de 100 caracteres")
		String referencia) {

}

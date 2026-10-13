package co.edu.uis.catalogo.dto;

import java.util.List;

/**
 * Lo que responde el descuento de stock cuando todo salió bien: cuánto quedó de cada producto.
 * Carro lo usa para mostrar el resultado sin tener que volver a consultar el catálogo.
 */
public record DescuentoStockResponse(List<Item> items) {

	public record Item(String id, int stockRestante) {

	}

}
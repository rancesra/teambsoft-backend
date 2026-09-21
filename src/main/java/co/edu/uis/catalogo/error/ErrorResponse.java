package co.edu.uis.catalogo.error;

/**
 * Formato de error del contrato (sección 4). Jackson lo convierte en
 * {@code {"codigo": "...", "mensaje": "..."}}. Solo lo arma {@link ManejadorGlobalErrores}.
 */
public record ErrorResponse(String codigo, String mensaje) {
}
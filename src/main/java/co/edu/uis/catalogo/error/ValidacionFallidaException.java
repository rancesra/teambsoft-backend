package co.edu.uis.catalogo.error;

/**
 * Se lanza cuando falla una regla de negocio que las anotaciones de validación no pueden comprobar,
 * por ejemplo que la categoría de un producto no existe. {@link ManejadorGlobalErrores} la convierte
 * en 400 {@code VALIDACION_FALLIDA}, con el mensaje que se pase aquí.
 */
public class ValidacionFallidaException extends RuntimeException {

	public ValidacionFallidaException(String mensaje) {
		super(mensaje);
	}

}
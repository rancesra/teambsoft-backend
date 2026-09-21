package co.edu.uis.catalogo.error;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Convierte las excepciones en la respuesta de error del contrato (sección 4): un JSON
 * {@code {codigo, mensaje}} con el código HTTP que corresponde.
 *
 * <p>{@code @RestControllerAdvice} lo aplica a todos los controllers. Cuando un controller, o el
 * service al que llama, lanza una excepción, Spring busca aquí el método {@code @ExceptionHandler} de
 * ese tipo y responde con lo que ese método devuelve. Así, controllers y services solo lanzan
 * excepciones, y el formato de error se define en un único lugar.
 *
 * <p>Se usa el formato del contrato y no ProblemDetail (el estándar de Spring) porque ya estaba
 * acordado con los equipos A y C. Los errores que no se atrapan aquí (fallas inesperadas, rutas que no
 * existen) salen con el formato por defecto de Spring.
 *
 * <p>Las pruebas de esta clase están en {@code ManejadorGlobalErroresTest}.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

	/** Códigos de error del contrato (sección 4). */
	private static final String PRODUCTO_NO_ENCONTRADO = "PRODUCTO_NO_ENCONTRADO";
	private static final String VALIDACION_FALLIDA = "VALIDACION_FALLIDA";

	/** 404: se pidió un producto que no existe. */
	@ExceptionHandler(ProductoNoEncontradoException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse productoNoEncontrado(ProductoNoEncontradoException e) {
		return new ErrorResponse(PRODUCTO_NO_ENCONTRADO, e.getMessage());
	}

	/** 400: falló una regla de negocio, por ejemplo que la categoría no existe. */
	@ExceptionHandler(ValidacionFallidaException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse validacionFallida(ValidacionFallidaException e) {
		return new ErrorResponse(VALIDACION_FALLIDA, e.getMessage());
	}

	/**
	 * 400: el cuerpo JSON no cumple las anotaciones del DTO ({@code @Valid}). El mensaje junta cada
	 * campo que falló, por ejemplo {@code "nombre: es obligatorio; precio: debe ser mayor que 0"}.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse cuerpoInvalido(MethodArgumentNotValidException e) {
		String mensaje = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return new ErrorResponse(VALIDACION_FALLIDA, mensaje);
	}

	/**
	 * 400: un parámetro de la URL no cumple sus anotaciones ({@code @Min}, {@code @Max}), por ejemplo
	 * {@code ?pagina=0}. Cada parámetro puede tener varios errores; {@code flatMap} los junta todos.
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse parametrosInvalidos(HandlerMethodValidationException e) {
		String mensaje = e.getParameterValidationResults().stream()
				.flatMap(resultado -> resultado.getResolvableErrors().stream()
						.map(error -> resultado.getMethodParameter().getParameterName() + ": "
								+ error.getDefaultMessage()))
				.collect(Collectors.joining("; "));
		return new ErrorResponse(VALIDACION_FALLIDA, mensaje);
	}

	/** 400: un parámetro de la URL no se puede convertir a su tipo, por ejemplo {@code ?pagina=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse tipoDeParametroInvalido(MethodArgumentTypeMismatchException e) {
		return new ErrorResponse(VALIDACION_FALLIDA, "El parámetro '" + e.getName() + "' tiene un valor inválido");
	}

	/** 400: el cuerpo no es JSON válido, o un campo tiene el tipo equivocado ({@code "precio": "abc"}). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse cuerpoIlegible(HttpMessageNotReadableException e) {
		return new ErrorResponse(VALIDACION_FALLIDA, "El cuerpo de la petición no es un JSON válido o tiene tipos incorrectos");
	}

}
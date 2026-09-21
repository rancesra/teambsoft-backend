package co.edu.uis.catalogo.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Comprueba que {@link ManejadorGlobalErrores} responde con el formato de error del contrato
 * (sección 4) para cada tipo de error.
 *
 * <p>Todavía no hay endpoints de productos que fallen, así que se usa {@link ControladorDePrueba}, un
 * controller que solo existe en esta prueba y provoca cada error a propósito.
 *
 * <p>{@code @WebMvcTest} carga solo la capa web, sin MongoDB, y por eso es rápida. El controller de
 * prueba se registra con {@code @Import} porque Spring Boot ignora las clases definidas dentro de una
 * prueba cuando busca componentes.
 */
@WebMvcTest(controllers = ManejadorGlobalErroresTest.ControladorDePrueba.class)
@Import(ManejadorGlobalErroresTest.ControladorDePrueba.class)
class ManejadorGlobalErroresTest {

	// En las pruebas se inyecta en el campo: la clase de prueba la crea JUnit, no Spring.
	@Autowired
	private MockMvc mockMvc;

	@Test
	void productoNoEncontradoResponde404() throws Exception {
		mockMvc.perform(get("/prueba/no-encontrado"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").value("No existe un producto con id 123"));
	}

	@Test
	void reglaDeNegocioResponde400() throws Exception {
		mockMvc.perform(get("/prueba/regla"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("La categoría 'cat-inexistente' no existe"));
	}

	@Test
	void cuerpoInvalidoResponde400() throws Exception {
		mockMvc.perform(post("/prueba/cuerpo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("nombre: es obligatorio"));
	}

	@Test
	void jsonMalFormadoResponde400() throws Exception {
		mockMvc.perform(post("/prueba/cuerpo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{esto no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
	}

	@Test
	void parametroFueraDeRangoResponde400() throws Exception {
		mockMvc.perform(get("/prueba/parametro").param("pagina", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("pagina: debe ser 1 o más"));
	}

	@Test
	void parametroDeTipoIncorrectoResponde400() throws Exception {
		mockMvc.perform(get("/prueba/parametro").param("pagina", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("El parámetro 'pagina' tiene un valor inválido"));
	}

	/** Controller que solo existe en esta prueba: cada endpoint provoca un tipo de error distinto. */
	@RestController
	@RequestMapping("/prueba")
	static class ControladorDePrueba {

		@GetMapping("/no-encontrado")
		void noEncontrado() {
			throw new ProductoNoEncontradoException("123");
		}

		@GetMapping("/regla")
		void regla() {
			throw new ValidacionFallidaException("La categoría 'cat-inexistente' no existe");
		}

		@PostMapping("/cuerpo")
		void cuerpo(@Valid @RequestBody DatosDePrueba datos) {
		}

		@GetMapping("/parametro")
		void parametro(@RequestParam @Min(value = 1, message = "debe ser 1 o más") int pagina) {
		}

	}

	/**
	 * Cuerpo de ejemplo con una validación. El mensaje va fijo en español para que la prueba no dependa
	 * del idioma de quien la ejecuta.
	 */
	record DatosDePrueba(@NotBlank(message = "es obligatorio") String nombre) {
	}

}
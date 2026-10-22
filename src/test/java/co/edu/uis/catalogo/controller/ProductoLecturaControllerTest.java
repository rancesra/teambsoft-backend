package co.edu.uis.catalogo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import co.edu.uis.catalogo.dto.ProductoListadoResponse;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Endpoints de lectura y borrado (B3): el listado con sus filtros y su paginación, el detalle y el
 * DELETE. No necesita MongoDB: {@code @WebMvcTest} carga solo la capa web y {@code @MockitoBean}
 * reemplaza el service por un doble.
 *
 * <p>Las pruebas de escritura (POST, PUT, activar, descontar stock) viven en
 * {@code ProductoControllerTest}, que es de B2.
 */
@WebMvcTest(ProductoController.class)
class ProductoLecturaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	@MockitoBean
	private RabbitTemplate rabbitTemplate;

	private static final ProductoResponse PRODUCTO_ACTIVO = new ProductoResponse("prod-1", "Camiseta",
			"Algodón", new BigDecimal("49900"), "cat-ropa", 120, List.of(), true);

	private static final ProductoResponse PRODUCTO_INACTIVO = new ProductoResponse("prod-2", "Pantalón",
			"Algodón", new BigDecimal("39900"), "cat-hogar", 5, List.of(), false);

	// ── GET /productos ──────────────────────────────────────────────

	@Test
	void listarSinParametrosUsaLosValoresPorDefecto() throws Exception {
		given(productoService.listar(anyInt(), anyInt(), any(), any()))
				.willReturn(new ProductoListadoResponse(List.of(), 0L, 1, 20));

		mockMvc.perform(get("/productos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productos").isEmpty())
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.tamanoPagina").value(20));

		// Página 1, tamaño 20, sin categoría y solo activos: el comportamiento previo al B3.
		verify(productoService).listar(1, 20, null, "true");
	}

	@Test
	void listarConPaginaYTamanoPersonalizados() throws Exception {
		given(productoService.listar(3, 10, null, "true"))
				.willReturn(new ProductoListadoResponse(List.of(), 42L, 3, 10));

		mockMvc.perform(get("/productos").param("pagina", "3").param("tamanoPagina", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(42))
				.andExpect(jsonPath("$.pagina").value(3))
				.andExpect(jsonPath("$.tamanoPagina").value(10));
	}

	@Test
	void listarDevuelveLaFormaDelContrato() throws Exception {
		given(productoService.listar(1, 20, null, "true"))
				.willReturn(new ProductoListadoResponse(List.of(PRODUCTO_ACTIVO), 42L, 1, 20));

		mockMvc.perform(get("/productos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productos[0].id").value("prod-1"))
				.andExpect(jsonPath("$.productos[0].nombre").value("Camiseta"))
				.andExpect(jsonPath("$.productos[0].categoria").value("cat-ropa"));
	}

	@Test
	void listarPaginaCeroResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("pagina", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("pagina: debe ser 1 o más"));
	}

	@Test
	void listarTamanoPaginaCeroResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("tamanoPagina", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("tamanoPagina: debe ser 1 o más"));
	}

	@Test
	void listarTamanoPaginaMayorQueCienResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("tamanoPagina", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("tamanoPagina: no puede pasar de 100"));
	}

	@Test
	void listarPaginaNoNumericaResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("pagina", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("El parámetro 'pagina' tiene un valor inválido"));
	}

	@Test
	void filtroActivoInvalidoResponde400() throws Exception {
		willThrow(new ValidacionFallidaException("activo: debe ser true, false o todos"))
				.given(productoService).listar(anyInt(), anyInt(), any(), eq("quizas"));

		mockMvc.perform(get("/productos").param("activo", "quizas"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("activo: debe ser true, false o todos"));
	}

	@Test
	void filtraPorCategoria() throws Exception {
		given(productoService.listar(1, 20, "cat-ropa", "true"))
				.willReturn(new ProductoListadoResponse(List.of(PRODUCTO_ACTIVO), 1L, 1, 20));

		mockMvc.perform(get("/productos").param("categoria", "cat-ropa"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.productos[0].categoria").value("cat-ropa"));
	}

	@Test
	void unaCategoriaSinProductosDevuelveListaVaciaYNoUnError() throws Exception {
		given(productoService.listar(1, 20, "cat-electronica", "true"))
				.willReturn(new ProductoListadoResponse(List.of(), 0L, 1, 20));

		mockMvc.perform(get("/productos").param("categoria", "cat-electronica"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productos").isEmpty())
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void conActivoFalseSalenSoloLosDesactivados() throws Exception {
		given(productoService.listar(1, 20, null, "false"))
				.willReturn(new ProductoListadoResponse(List.of(PRODUCTO_INACTIVO), 5L, 1, 20));

		mockMvc.perform(get("/productos").param("activo", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.productos[0].activo").value(false));
	}

	@Test
	void conActivoTodosSalenActivosEInactivos() throws Exception {
		given(productoService.listar(1, 20, null, "todos"))
				.willReturn(new ProductoListadoResponse(List.of(PRODUCTO_ACTIVO, PRODUCTO_INACTIVO), 2L, 1, 20));

		mockMvc.perform(get("/productos").param("activo", "todos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.productos.length()").value(2));
	}

	// ── GET /productos/{id} ──────────────────────────────────────────

	@Test
	void detalleDeProductoActivoResponde200() throws Exception {
		given(productoService.obtenerPorId("prod-1")).willReturn(PRODUCTO_ACTIVO);

		mockMvc.perform(get("/productos/prod-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("prod-1"))
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void detalleDeProductoDesactivadoResponde200ConActivoFalse() throws Exception {
		given(productoService.obtenerPorId("prod-2")).willReturn(PRODUCTO_INACTIVO);

		mockMvc.perform(get("/productos/prod-2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("prod-2"))
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	void detalleDeIdInexistenteResponde404() throws Exception {
		willThrow(new ProductoNoEncontradoException("inexistente")).given(productoService)
				.obtenerPorId("inexistente");

		mockMvc.perform(get("/productos/inexistente"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
	}

	// ── DELETE /productos/{id} ───────────────────────────────────────

	@Test
	void borrarProductoActivoResponde204SinCuerpo() throws Exception {
		given(productoService.desactivar("prod-1")).willReturn(true);

		mockMvc.perform(delete("/productos/prod-1")).andExpect(status().isNoContent());

		verify(productoService, times(1)).desactivar("prod-1");
	}

	@Test
	void borrarProductoYaInactivoResponde204Igual() throws Exception {
		given(productoService.desactivar("prod-2")).willReturn(false);

		mockMvc.perform(delete("/productos/prod-2")).andExpect(status().isNoContent());

		verify(productoService, times(1)).desactivar("prod-2");
	}

	@Test
	void borrarUnIdInexistenteResponde404() throws Exception {
		willThrow(new ProductoNoEncontradoException("inexistente")).given(productoService)
				.desactivar("inexistente");

		mockMvc.perform(delete("/productos/inexistente"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
	}

}
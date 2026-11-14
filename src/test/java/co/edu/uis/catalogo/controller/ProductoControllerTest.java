package co.edu.uis.catalogo.controller;

import static co.edu.uis.catalogo.config.TokensDePrueba.admin;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import co.edu.uis.catalogo.config.SeguridadConfig;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.ReferenciaEnUsoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Comprueba los endpoints de B2: los códigos de estado, las validaciones del contrato y el formato de
 * los errores.
 */
@WebMvcTest(ProductoController.class)
@Import(SeguridadConfig.class)
class ProductoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	@MockitoBean
	private RabbitTemplate rabbitTemplate;

	private static final String CUERPO_VALIDO = """
			{"nombre":"Camiseta","descripcion":"Algodón","precio":49900,
			 "categoria":"cat-ropa","stock":120,"imagenes":[]}""";

	private static ProductoResponse ejemplo(boolean activo) {
		return new ProductoResponse("abc123", "Camiseta", "Algodón", new BigDecimal("49900"),
				"cat-ropa", 120, List.of(), activo);
	}

	@Test
	void postValidoResponde201() throws Exception {
		given(productoService.crear(any())).willReturn(ejemplo(true));

		mockMvc.perform(post("/productos").with(admin()).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("abc123"))
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void postSinNombreResponde400() throws Exception {
		mockMvc.perform(post("/productos").with(admin()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"","precio":49900,"categoria":"cat-ropa","stock":10}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("nombre: es obligatorio"));
	}

	@Test
	void postConPrecioCeroResponde400() throws Exception {
		mockMvc.perform(post("/productos").with(admin()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Camiseta","precio":0,"categoria":"cat-ropa","stock":10}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("precio: debe ser mayor que 0"));
	}

	@Test
	void postConStockNegativoResponde400() throws Exception {
		mockMvc.perform(post("/productos").with(admin()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Camiseta","precio":100,"categoria":"cat-ropa","stock":-1}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("stock: no puede ser negativo"));
	}

	@Test
	void putValidoResponde200() throws Exception {
		given(productoService.actualizar(eq("abc123"), any())).willReturn(ejemplo(true));

		mockMvc.perform(put("/productos/abc123").with(admin()).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Camiseta"));
	}

	@Test
	void putAIdInexistenteResponde404() throws Exception {
		willThrow(new ProductoNoEncontradoException("nada")).given(productoService).actualizar(eq("nada"), any());

		mockMvc.perform(put("/productos/nada").with(admin()).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
	}

	@Test
	void activarResponde200ConElProductoActivo() throws Exception {
		given(productoService.activar("abc123")).willReturn(ejemplo(true));

		mockMvc.perform(post("/productos/abc123/activar").with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void descontarStockResponde200ConElRestante() throws Exception {
		given(productoService.descontarStock(any()))
				.willReturn(new DescuentoStockResponse(List.of(new DescuentoStockResponse.Item("abc123", 118))));

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":2}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].stockRestante").value(118));
	}

	@Test
	void descontarStockSinSuficienteResponde409ConLosItems() throws Exception {
		willThrow(new StockInsuficienteException(List.of(new StockInsuficienteException.Faltante("abc123", 999, 4))))
				.given(productoService).descontarStock(any());

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":999}]}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"))
				.andExpect(jsonPath("$.mensaje").value("No hay stock suficiente para 1 producto"))
				.andExpect(jsonPath("$.items[0].id").value("abc123"))
				.andExpect(jsonPath("$.items[0].cantidad").value(999))
				.andExpect(jsonPath("$.items[0].stock").value(4));
	}

	@Test
	void descontarStockSinReferenciaNoLaDevuelve() throws Exception {
		given(productoService.descontarStock(any()))
				.willReturn(new DescuentoStockResponse(List.of(new DescuentoStockResponse.Item("abc123", 118))));

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":2}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.referencia").doesNotExist());
	}

	@Test
	void descontarStockConReferenciaLaPasaAlServicioYLaDevuelve() throws Exception {
		given(productoService.descontarStock(argThat(peticion -> "chk_1".equals(peticion.referencia()))))
				.willReturn(new DescuentoStockResponse("chk_1",
						List.of(new DescuentoStockResponse.Item("abc123", 118))));

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"referencia":"chk_1","items":[{"id":"abc123","cantidad":2}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.referencia").value("chk_1"))
				.andExpect(jsonPath("$.items[0].stockRestante").value(118));
	}

	@Test
	void descontarStockConReferenciaVaciaResponde400() throws Exception {
		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"referencia":"","items":[{"id":"abc123","cantidad":2}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("referencia: debe tener entre 1 y 100 caracteres"));
	}

	@Test
	void descontarStockConReferenciaEnUsoResponde409() throws Exception {
		willThrow(new ReferenciaEnUsoException("La referencia chk_1 ya se usó con otros productos o cantidades"))
				.given(productoService).descontarStock(any());

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"referencia":"chk_1","items":[{"id":"abc123","cantidad":5}]}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REFERENCIA_EN_USO"))
				.andExpect(jsonPath("$.items").doesNotExist());
	}

	@Test
	void devolverStockResponde204() throws Exception {
		mockMvc.perform(post("/productos/devolver-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"referencia":"chk_1"}"""))
				.andExpect(status().isNoContent());

		verify(productoService).devolverStock("chk_1");
	}

	@Test
	void devolverStockSinReferenciaResponde400() throws Exception {
		mockMvc.perform(post("/productos/devolver-stock").contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("referencia: es obligatoria"));

		verifyNoInteractions(productoService);
	}

	@Test
	void descontarStockConCantidadCeroResponde400() throws Exception {
		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":0}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
	}

}

package co.edu.uis.catalogo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.edu.uis.catalogo.controller.CategoriaController;
import co.edu.uis.catalogo.controller.ProductoController;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.dto.ProductoListadoResponse;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.service.CategoriaService;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Las reglas de acceso de {@link SeguridadConfig} (contrato v2.5, sección 2).
 *
 * <p>Las peticiones llevan un token de mentira ({@code Bearer token-admin}) y un {@link JwtDecoder}
 * simulado lo traduce a los datos que traería un token real de Keycloak. Así la prueba recorre el
 * camino completo: la cabecera, el decodificador, la lectura de roles y las reglas.
 */
@WebMvcTest(controllers = { ProductoController.class, CategoriaController.class })
@Import(SeguridadConfig.class)
class SeguridadTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	@MockitoBean
	private CategoriaService categoriaService;

	@MockitoBean
	private RabbitTemplate rabbitTemplate;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	private static final String CUERPO_VALIDO = """
			{"nombre":"Camiseta","descripcion":"Algodón","precio":49900,
			 "categoria":"cat-ropa","stock":120,"imagenes":[]}""";

	@BeforeEach
	void tokensDeMentira() {
		given(jwtDecoder.decode("token-admin")).willReturn(token("admin", "cliente"));
		given(jwtDecoder.decode("token-cliente")).willReturn(token("cliente"));
		given(jwtDecoder.decode("token-vencido")).willThrow(new BadJwtException("Jwt expired"));

		ProductoResponse producto = new ProductoResponse("abc123", "Camiseta", "Algodón",
				new BigDecimal("49900"), "cat-ropa", 120, List.of(), true);
		given(productoService.listar(anyInt(), anyInt(), any(), anyString()))
				.willReturn(new ProductoListadoResponse(List.of(), 0, 1, 20));
		given(productoService.crear(any())).willReturn(producto);
		given(productoService.actualizar(anyString(), any())).willReturn(producto);
		given(productoService.activar(anyString())).willReturn(producto);
		given(productoService.descontarStock(any())).willReturn(new DescuentoStockResponse(List.of()));
	}

	private static Jwt token(String... roles) {
		return Jwt.withTokenValue("x")
				.header("alg", "RS256")
				.subject("usuario-1")
				.claim("realm_access", Map.of("roles", List.of(roles)))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.build();
	}

	private static MockHttpServletRequestBuilder con(String token, MockHttpServletRequestBuilder peticion) {
		return peticion.header("Authorization", "Bearer " + token);
	}

	// --- Lo público --------------------------------------------------------------------------------

	@Test
	void elListadoElDetalleYLasCategoriasNoPidenToken() throws Exception {
		mockMvc.perform(get("/productos")).andExpect(status().isOk());
		mockMvc.perform(get("/productos").param("activo", "true")).andExpect(status().isOk());
		mockMvc.perform(get("/productos/abc123")).andExpect(status().isOk());
		mockMvc.perform(get("/categorias")).andExpect(status().isOk());
	}

	@Test
	void descontarStockSigueAbiertoParaCarrito() throws Exception {
		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"referencia":"chk-1","items":[{"id":"abc123","cantidad":1}]}"""))
				.andExpect(status().isOk());
	}

	// --- Sin token: 401 ----------------------------------------------------------------------------

	@Test
	void crearSinTokenResponde401ConElFormatoDelContrato() throws Exception {
		mockMvc.perform(post("/productos").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void editarDesactivarYReactivarSinTokenResponden401() throws Exception {
		mockMvc.perform(put("/productos/abc123").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/productos/abc123")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/productos/abc123/activar")).andExpect(status().isUnauthorized());
	}

	@Test
	void verLosDesactivadosSinTokenResponde401() throws Exception {
		mockMvc.perform(get("/productos").param("activo", "false"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	@Test
	void unTokenVencidoOFalsoResponde401() throws Exception {
		mockMvc.perform(con("token-vencido", post("/productos"))
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	// --- Con token de cliente: 403 -----------------------------------------------------------------

	@Test
	void unClienteNoPuedeCrearNiVerLosDesactivados() throws Exception {
		mockMvc.perform(con("token-cliente", post("/productos"))
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("SIN_PERMISO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());

		mockMvc.perform(con("token-cliente", get("/productos")).param("activo", "todos"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
	}

	// --- Con token de admin ------------------------------------------------------------------------

	@Test
	void unAdminPuedeCrearEditarDesactivarYReactivar() throws Exception {
		mockMvc.perform(con("token-admin", post("/productos"))
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isCreated());
		mockMvc.perform(con("token-admin", put("/productos/abc123"))
				.contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isOk());
		mockMvc.perform(con("token-admin", delete("/productos/abc123"))).andExpect(status().isNoContent());
		mockMvc.perform(con("token-admin", post("/productos/abc123/activar"))).andExpect(status().isOk());
	}

	@Test
	void unAdminPuedeVerLosDesactivados() throws Exception {
		mockMvc.perform(con("token-admin", get("/productos")).param("activo", "false"))
				.andExpect(status().isOk());
	}

	// --- Lectura de roles --------------------------------------------------------------------------

	@Test
	void unTokenSinRolesNoTieneAutoridades() {
		Jwt sinRoles = Jwt.withTokenValue("x").header("alg", "RS256").subject("u").build();

		assertThat(SeguridadConfig.rolesDelRealm(sinRoles)).isEmpty();
	}

	@Test
	void losRolesDelRealmSeConviertenEnRoleGuionBajo() {
		assertThat(SeguridadConfig.rolesDelRealm(token("admin", "cliente")))
				.extracting(Object::toString)
				.containsExactly("ROLE_admin", "ROLE_cliente");
	}

}

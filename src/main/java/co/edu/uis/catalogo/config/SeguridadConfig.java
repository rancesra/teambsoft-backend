package co.edu.uis.catalogo.config;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import co.edu.uis.catalogo.error.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Quién puede llamar a cada endpoint (contrato v2.5, sección 2).
 *
 * <p>Catálogo es un <em>resource server</em>: no inicia sesiones ni guarda contraseñas. El usuario
 * inicia sesión en Keycloak, que le entrega un token firmado (un JWT). El navegador manda ese token en
 * la cabecera {@code Authorization: Bearer ...} y Spring Security comprueba aquí tres cosas: que la
 * firma sea de nuestro Keycloak, que no esté vencido y que lo haya emitido el realm {@code tienda}.
 * Después, estas reglas deciden si el rol que trae alcanza.
 *
 * <p>Las pruebas de esta clase están en {@code SeguridadTest}.
 */
@Configuration
public class SeguridadConfig {

	static final String ROL_ADMIN = "admin";

	@Bean
	SecurityFilterChain filtroDeSeguridad(HttpSecurity http, JsonMapper json) throws Exception {
		http
				// Sin sesión ni cookies: cada petición trae su token. Por eso tampoco hace falta la
				// protección CSRF, que existe para los formularios que se apoyan en una cookie de sesión.
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(reglas -> reglas
						// Ver los desactivados es de la administración (GET /productos?activo=false|todos).
						// Va antes que la regla pública del listado: gana la primera regla que coincide.
						.requestMatchers(listadoConDesactivados()).hasRole(ROL_ADMIN)

						// Lo público: el escaparate y Búsqueda leen sin iniciar sesión.
						.requestMatchers(HttpMethod.GET, "/productos", "/productos/*", "/categorias").permitAll()

						// "Servicio": los llama Carrito al confirmar una compra. Siguen abiertos en esta
						// entrega (contrato v2.5, sección 8): protegerlos exige que Carrito pida su propio token.
						.requestMatchers(HttpMethod.POST, "/productos/descontar-stock", "/productos/devolver-stock")
						.permitAll()

						// Lo que cambia el catálogo: solo el rol admin.
						.requestMatchers(HttpMethod.POST, "/productos", "/productos/*/activar").hasRole(ROL_ADMIN)
						.requestMatchers(HttpMethod.PUT, "/productos/*").hasRole(ROL_ADMIN)
						.requestMatchers(HttpMethod.DELETE, "/productos/*").hasRole(ROL_ADMIN)

						// Salud (Docker y Eureka), documentación de la API y la página de error de Spring.
						.requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**",
								"/swagger-ui.html", "/error")
						.permitAll()

						// Cualquier otra cosa, con token. Mejor cerrar por defecto que olvidar una ruta.
						.anyRequest().authenticated())
				.oauth2ResourceServer(rs -> rs
						.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeKeycloak()))
						.authenticationEntryPoint(sinToken(json))
						.accessDeniedHandler(sinPermiso(json)))
				// Los mismos manejadores para las rutas sin token que no pasan por el resource server.
				.exceptionHandling(e -> e
						.authenticationEntryPoint(sinToken(json))
						.accessDeniedHandler(sinPermiso(json)));

		return http.build();
	}

	/** GET /productos con {@code activo} distinto de {@code true}: la lista de la administración. */
	private static RequestMatcher listadoConDesactivados() {
		return peticion -> HttpMethod.GET.matches(peticion.getMethod())
				&& "/productos".equals(peticion.getRequestURI())
				&& peticion.getParameter("activo") != null
				&& !"true".equals(peticion.getParameter("activo"));
	}

	/**
	 * Keycloak guarda los roles del realm en {@code realm_access.roles}. Spring Security no sabe leer
	 * ese campo, así que aquí se convierten en autoridades {@code ROLE_admin}, {@code ROLE_cliente}, que
	 * es lo que revisa {@code hasRole("admin")}.
	 */
	static JwtAuthenticationConverter convertidorDeKeycloak() {
		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(SeguridadConfig::rolesDelRealm);
		return convertidor;
	}

	static Collection<GrantedAuthority> rolesDelRealm(Jwt jwt) {
		Object realm = jwt.getClaims().get("realm_access");
		if (!(realm instanceof Map<?, ?> acceso) || !(acceso.get("roles") instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
				.map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
				.toList();
	}

	/**
	 * 401: no trae token, o el que trae no sirve (vencido, mal firmado, de otro emisor). Se delega en el
	 * manejador de Spring para conservar la cabecera estándar {@code WWW-Authenticate}, y se agrega el
	 * cuerpo con el formato de error del contrato (sección 4).
	 */
	private static AuthenticationEntryPoint sinToken(JsonMapper json) {
		BearerTokenAuthenticationEntryPoint estandar = new BearerTokenAuthenticationEntryPoint();
		return (peticion, respuesta, error) -> {
			estandar.commence(peticion, respuesta, error);
			escribir(respuesta, json, new ErrorResponse("NO_AUTENTICADO",
					"Esta operación requiere iniciar sesión: falta el token o no es válido"));
		};
	}

	/** 403: el token es válido, pero el usuario no tiene el rol que pide la operación. */
	private static AccessDeniedHandler sinPermiso(JsonMapper json) {
		BearerTokenAccessDeniedHandler estandar = new BearerTokenAccessDeniedHandler();
		return (peticion, respuesta, error) -> {
			estandar.handle(peticion, respuesta, error);
			escribir(respuesta, json, new ErrorResponse("SIN_PERMISO",
					"Esta operación es solo para administradores (rol admin)"));
		};
	}

	private static void escribir(HttpServletResponse respuesta, JsonMapper json, ErrorResponse error)
			throws IOException {
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		respuesta.setCharacterEncoding("UTF-8");
		json.writeValue(respuesta.getOutputStream(), error);
	}

}

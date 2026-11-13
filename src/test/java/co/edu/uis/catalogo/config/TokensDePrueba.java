package co.edu.uis.catalogo.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Tokens simulados para las pruebas con MockMvc: la petición llega como si trajera un token de
 * Keycloak con esos roles, sin levantar Keycloak. Los roles se leen con el mismo convertidor de
 * {@link SeguridadConfig}, así que la prueba pasa por la misma lógica que una petición real.
 *
 * <p>Uso: {@code mockMvc.perform(post("/productos").with(TokensDePrueba.admin())...)}.
 */
public final class TokensDePrueba {

	private TokensDePrueba() {
	}

	/** Un usuario con rol {@code admin}, como el usuario {@code admin} del realm de prueba. */
	public static JwtRequestPostProcessor admin() {
		return conRoles("admin", "cliente");
	}

	/** Un usuario que inició sesión pero no es administrador. */
	public static JwtRequestPostProcessor cliente() {
		return conRoles("cliente");
	}

	private static JwtRequestPostProcessor conRoles(String... roles) {
		return jwt()
				.jwt(token -> token.claim("realm_access", Map.of("roles", List.of(roles))))
				.authorities(SeguridadConfig::rolesDelRealm);
	}

}

package nl.tufa.poc.bme680.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import nl.tufa.poc.bme680.identity.domain.port.HttpUserRepository;

@Configuration
@EnableWebSecurity
public class WebSecurity {
	@Bean
	@DependsOn("httpUserBootstrap")
	public SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users,
			PasswordEncoder encoder) throws Exception {
		var provider = new DaoAuthenticationProvider(users);
		provider.setPasswordEncoder(encoder);
		http.authenticationProvider(provider)
				// JSON is not a CORS-safelisted content type. These endpoints also
				// require JSON bodies; simple browser submissions remain CSRF-protected.
				.csrf(csrf -> csrf.ignoringRequestMatchers(WebSecurity::isJsonMutation))
				.authorizeHttpRequests(auth -> auth
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.POST, "/bme680").hasRole("SENSOR")
						.requestMatchers(HttpMethod.GET, "/admin/users").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/admin/users").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/admin/users/*/roles",
								"/admin/users/*/password", "/admin/users/*/enabled").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/admin/mqtt-identities").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/admin/mqtt-identities/devices",
								"/admin/mqtt-identities/services",
								"/admin/mqtt-identities/*/credentials").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/admin/mqtt-identities/*/enabled").hasRole("ADMIN")
						.anyRequest().denyAll())
				.httpBasic(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	@Bean
	public UserDetailsService userDetailsService(HttpUserRepository users) {
		return username -> {
			var user = users.findByUsername(username)
					.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
			return User.withUsername(user.username()).password(user.passwordHash().encoded())
					.disabled(!user.enabled())
					.authorities(user.roles().stream().map(role -> "ROLE_" + role.name()).toArray(String[]::new))
					.build();
		};
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private static boolean isJsonMutation(HttpServletRequest request) {
		String path = request.getServletPath();
		if (path.isEmpty()) {
			path = request.getRequestURI().substring(request.getContextPath().length());
		}
		boolean endpoint = ("POST".equals(request.getMethod())
				&& ("/bme680".equals(path) || "/admin/users".equals(path)
						|| "/admin/mqtt-identities/devices".equals(path)
						|| "/admin/mqtt-identities/services".equals(path)
						|| path.matches("/admin/mqtt-identities/[^/]+/credentials")))
				|| ("PUT".equals(request.getMethod())
						&& (path.startsWith("/admin/users/") || path.startsWith("/admin/mqtt-identities/")));
		if (!endpoint || request.getContentType() == null) {
			return false;
		}
		try {
			var type = MediaType.parseMediaType(request.getContentType());
			return "application".equalsIgnoreCase(type.getType()) && "json".equalsIgnoreCase(type.getSubtype());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}

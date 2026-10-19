package nl.tufa.poc.bme680.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // Activeert Spring Security 7 functionaliteit
public class WebSecurity {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Schakel CSRF uit voor stateless REST API's (niet nodig bij puur Basic Auth via API)
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Configureer endpoint autorisatie
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/bme680/**").hasRole("SENSOR")             // Alleen voor ADMIN rol
                .anyRequest().authenticated()                                  // Rest vereist authenticatie
            )
            
            // 3. Activeer HTTP Basic Authenticatie met de standaardinstellingen
            .httpBasic(Customizer.withDefaults())
            
            // 4. Maak de applicatie stateless (geen HTTP sessies aanmaken voor authenticatie)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Maak testgebruikers aan in het geheugen (gebruik in productie een database)
        UserDetails user = User.builder()
                .username("user")
                .password(passwordEncoder.encode("password123"))
                .roles("USER")
                .build();

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin"))
                .roles("ADMIN")
                .build();

        UserDetails sensor = User.builder()
                .username("sensor")
                .password(passwordEncoder.encode("PQwKtahI0efC9lB97hTX"))
                .roles("SENSOR")
                .build();
        
        return new InMemoryUserDetailsManager(user, admin, sensor);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Verplicht in Spring Security: Wachtwoorden moeten veilig gehasht worden
        return new BCryptPasswordEncoder();
    }
}

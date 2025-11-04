package config.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Define CORS configuration inline to avoid bean ambiguity with MVC auto-config
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(java.util.List.of("Authorization"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.registerCorsConfiguration("/**", configuration);

        http
            .cors(cors -> cors.configurationSource(corsSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/auth/login", "/auth/refresh","/auth/signup","/auth/validate",
                        "/products","/products/**",
                        "/categories","/categories/**",
                        "/stores","/stores/**",
                        "/location","/location/**",
                        "/storesaddresses","/storesaddresses/**",
                        "/drones","/drones/**").permitAll()
                .requestMatchers("/users/userCreated").permitAll()

                // Allow static resources (HTML, CSS, JS, images)
                .requestMatchers("/static/**", "/images/**", "/uploads/**",
                        "/*.html", "/*.css", "/*.js", "/*.png", "/*.jpg",
                        "/test-*.html", "/debug-*.html", "/drone-*.html", "/index.html").permitAll()

                        // Admin only endpoints
                        .requestMatchers("/users/getAllUser").hasRole("ADMIN")
                        .requestMatchers("/users/deleteUser/**").hasRole("ADMIN")

                        // Authenticated user endpoints
                        .requestMatchers("/users/**").authenticated()
                        .requestMatchers("/auth/logout").authenticated()

                        // All other requests need authentication
                       // .anyRequest().authenticated()
                            .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
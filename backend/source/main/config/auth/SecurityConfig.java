package config.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
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
                // Always allow preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public endpoints
                .requestMatchers("/auth/login", "/auth/refresh","/auth/signup","/auth/validate",
                    // Public GETs for catalog browsing; restrict mutating methods below
                    "/products", "/products/**",
                    "/categories", "/categories/**",
                    "/stores","/stores/**",
                    "/api/stores","/api/stores/**",
                        // Payment return/IPN must be public for VNPAY callbacks and browser returns
                        "/api/v1/payments/vnpay-return", "/api/v1/payments/vnpay-ipn",
                        "/location","/location/**",
                        "/storesaddresses","/storesaddresses/**",
                        
                    // Allow GET for drones public (listing and fetch by code)
                    "/drones", "/api/v1/drones",
                    "/drones/*", "/api/v1/drones/*").permitAll()
                .requestMatchers("/users/userCreated").permitAll()

                // Allow static resources (HTML, CSS, JS, images)
                .requestMatchers("/static/**", "/images/**", "/uploads/**",
                        "/*.html", "/*.css", "/*.js", "/*.png", "/*.jpg",
                        "/test-*.html", "/debug-*.html", "/drone-*.html", "/index.html").permitAll()

                    // Cart requires authentication
                    .requestMatchers("/api/cart/**").authenticated()

                        // Admin only endpoints
                        .requestMatchers("/users/getAllUser").hasRole("ADMIN")
                        .requestMatchers("/users/deleteUser/**").hasRole("ADMIN")

                        // Product & Category write protections (POST/PUT/PATCH/DELETE)
                        .requestMatchers(HttpMethod.POST, "/products/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/products/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/products/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/products/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/categories/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/categories/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/categories/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/categories/**").authenticated()

                        // Drones write protections
                        .requestMatchers(HttpMethod.PUT, "/drones/**", "/api/v1/drones/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/drones/**", "/api/v1/drones/**").authenticated()
                        // Keep registration open if needed
                        .requestMatchers(HttpMethod.POST, "/drones/register", "/api/v1/drones/register").permitAll()

                        // Authenticated user endpoints
                        .requestMatchers("/users/**").authenticated()
                        .requestMatchers("/auth/logout").authenticated()

                        // All other requests need authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
package config.auth;

import service.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private static final Set<String> blacklistedTokens = new HashSet<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {
            // Check if token is blacklisted
            if (blacklistedTokens.contains(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Validate token
            if (jwtService.isTokenValid(jwt) && !jwtService.isTokenExpired(jwt)) {
                String username = jwtService.extractUsername(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Extract roles from token
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) jwtService.extractClaims(jwt).get("roles");

                    Set<SimpleGrantedAuthority> authorities = roles != null ? roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toSet()) : new HashSet<>();

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is invalid, continue without authentication
            logger.error("JWT Token validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // Static method to add token to blacklist (to be called from AuthenticationService)
    public static void blacklistToken(String token) {
        blacklistedTokens.add(token);
    }
}

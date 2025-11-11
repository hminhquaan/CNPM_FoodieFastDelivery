package config.auth;

import mapper.UserMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import repository.user.RoleRepository;
import repository.user.UserRepository;
import service.auth.AuthenticationService;
import service.auth.AuthenticationServiceImpl;
import service.auth.JwtService;

@Configuration
public class AuthBeansConfig {

    @Bean
    public AuthenticationService authenticationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserMapper userMapper,
            JwtService jwtService,
            PasswordEncoder passwordEncoder
    ) {
        return new AuthenticationServiceImpl(
                userRepository,
                roleRepository,
                userMapper,
                jwtService,
                passwordEncoder
        );
    }
}

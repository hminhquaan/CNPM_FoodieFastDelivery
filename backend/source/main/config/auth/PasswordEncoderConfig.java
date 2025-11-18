package config.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    // Dev toggle: when security.password.noop=true, use plaintext passwords (DEV ONLY)
    @Bean
    @ConditionalOnProperty(name = "security.password.noop", havingValue = "true")
    public PasswordEncoder noOpPasswordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    // Default: BCrypt encoder
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

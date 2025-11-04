package service.auth;

import config.auth.JwtAuthenticationFilter;
import dto.request.Auth.LoginRequest;
import dto.request.Auth.SignUpRequest;
import dto.response.Auth.AuthenticationResponse;
import dto.response.User.UserResponse;
import entity.Roles;
import entity.User;
import enums.Gender;
import enums.UserStatus;
import mapper.UserMapper;
import repository.user.RoleRepository;
import repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;


    @Override
    public AuthenticationResponse authenticate(LoginRequest request) {
        // Find user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        // Check if user is active
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }

        // Extract roles
        Set<String> roles = user.getRoles().stream()
                .map(Roles::getName)
                .collect(Collectors.toSet());

        // Generate tokens
        String token = jwtService.generateToken(user.getUsername(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());

        return AuthenticationResponse.builder()
        .id(user.getId())
                .token(token)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .authenticated(true)
                .build();
    }

    @Override
    public void logout(String token) {
        if (token != null && jwtService.isTokenValid(token)) {
            JwtAuthenticationFilter.blacklistToken(token);
            log.info("Token has been blacklisted successfully");
        }
    }

    @Override
    public boolean validateToken(String token) {
        return jwtService.isTokenValid(token) && !jwtService.isTokenExpired(token);
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        String username = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<String> roles = user.getRoles().stream()
                .map(Roles::getName)
                .collect(Collectors.toSet());

        return jwtService.generateToken(username, roles);
    }

    @Override
    public UserResponse signUp(SignUpRequest request) {
        // Check if username already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        // Check if phone already exists (if provided)
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            if (userRepository.findByPhone(request.getPhone()).isPresent()) {
                throw new RuntimeException("Phone number already exists");
            }
        }

    // Get USER role by name; if missing, create it on the fly to avoid signup failures
        Roles userRole = roleRepository.findByName("USER").orElse(null);
        if (userRole == null) {
            userRole = roleRepository.save(Roles.builder().name("USER").build());
        }

        // Create new user
        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .status(UserStatus.ACTIVE)
                .build();

        if(request.getPhone() == null ||request.getUsername()==null||request.getFullName()==null || request.getEmail()==null) {
            log.info("Missing required fields during sign up");
            throw new RuntimeException("Missing required fields: username, email, fullName, phone");
        }

        // Set date of birth if provided
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty()) {
            try {
                user.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
            } catch (Exception e) {
                throw new RuntimeException("Invalid date format. Please use yyyy-MM-dd format");
            }
        }

        // Set gender if provided
        if (request.getGender() != null && !request.getGender().trim().isEmpty()) {
            try {
                user.setGender(Gender.valueOf(request.getGender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid gender. Allowed values: MALE, FEMALE, OTHER");
            }
        }

    // Assign USER role
        Set<Roles> roles = new HashSet<>();
    roles.add(userRole);
        user.setRoles(roles);

        // Save user
        User savedUser = userRepository.save(user);

        log.info("User registered successfully with username: {}", savedUser.getUsername());

        return userMapper.toResponse(savedUser);
    }
}

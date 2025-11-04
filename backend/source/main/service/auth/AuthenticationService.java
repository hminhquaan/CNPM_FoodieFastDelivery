package service.auth;

import dto.request.Auth.LoginRequest;
import dto.request.Auth.SignUpRequest;
import dto.response.Auth.AuthenticationResponse;
import dto.response.User.UserResponse;

public interface AuthenticationService {
    AuthenticationResponse authenticate(LoginRequest request);
    void logout(String token);
    boolean validateToken(String token);
    String refreshToken(String refreshToken);
    UserResponse signUp(SignUpRequest request);
}

package controller.auth;

import dto.request.Auth.LoginRequest;
import dto.request.Auth.LogoutRequest;
import dto.request.Auth.SignUpRequest;
import dto.request.Auth.ValidateTokenRequest;
import dto.response.API.APIResponse;
import dto.response.Auth.AuthenticationResponse;
import dto.response.User.UserResponse;
import service.auth.AuthenticationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {

    AuthenticationService authenticationService;

    @PostMapping("/signup")
    public APIResponse<UserResponse> signUp(@RequestBody SignUpRequest request) {
        APIResponse<UserResponse> response = new APIResponse<>();
        try {
            UserResponse userResponse = authenticationService.signUp(request);
            response.setResult(userResponse);
            response.setMessage("User registered successfully");
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setCode(400);
        }
        return response;
    }

    @PostMapping("/login")
    public APIResponse<AuthenticationResponse> login(@RequestBody LoginRequest request) {
        APIResponse<AuthenticationResponse> response = new APIResponse<>();
        try {
            AuthenticationResponse authResponse = authenticationService.authenticate(request);
            response.setResult(authResponse);
            response.setMessage("Login successful");
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setCode(401);
        }
        return response;
    }

    @PostMapping("/logout")
    public APIResponse<String> logout(@RequestBody LogoutRequest request) {
        APIResponse<String> response = new APIResponse<>();
        try {
            authenticationService.logout(request.getToken());
            response.setResult("Logout successful");
            response.setMessage("User logged out successfully");
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setCode(400);
        }
        return response;
    }

    @PostMapping("/validate")
    public APIResponse<Boolean> validateToken(@RequestBody ValidateTokenRequest request) {
        APIResponse<Boolean> response = new APIResponse<>();
        try {
            boolean isValid = authenticationService.validateToken(request.getToken());
            response.setResult(isValid);
            response.setMessage(isValid ? "Token is valid" : "Token is invalid");
        } catch (Exception e) {
            response.setResult(false);
            response.setMessage(e.getMessage());
            response.setCode(400);
        }
        return response;
    }

    @PostMapping("/refresh")
    public APIResponse<String> refreshToken(@RequestParam String refreshToken) {
        APIResponse<String> response = new APIResponse<>();
        try {
            String newToken = authenticationService.refreshToken(refreshToken);
            response.setResult(newToken);
            response.setMessage("Token refreshed successfully");
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setCode(400);
        }
        return response;
    }
}

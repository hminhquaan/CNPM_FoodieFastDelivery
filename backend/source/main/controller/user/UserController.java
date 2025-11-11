package controller.user;

import dto.request.User.UserAddressCreationRequest;
import dto.request.User.UserCreationRequest;
import dto.response.API.APIResponse;
import dto.response.User.UserAddressResponse;
import dto.response.User.UserResponse;
import service.user.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/users", "/users"})
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {
    UserService userService;

    // Create user (admin or self-signup depending on security rules)
        @PostMapping({"", "/userCreated"})
    public ResponseEntity<APIResponse<UserResponse>> createUser(@RequestBody UserCreationRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<UserResponse>builder()
                .code(201)
                .message("User created")
                .result(created)
                .build());
    }

    // Update user
        @PutMapping({"/{userId}", "/UpdateUser/{userId}"})
    public ResponseEntity<APIResponse<UserResponse>> updateUser(@RequestBody UserCreationRequest request, @PathVariable String userId) {
        UserResponse updated = userService.updateUser(request, userId);
        return ResponseEntity.ok(APIResponse.<UserResponse>builder()
                .code(200)
                .message("User updated")
                .result(updated)
                .build());
    }

    // Get user by id
        @GetMapping({"/{userId}", "/GetUserById/{userId}"})
    public ResponseEntity<APIResponse<UserResponse>> getUserById(@PathVariable String userId) {
        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(APIResponse.<UserResponse>builder()
                .code(200)
                .message("User retrieved")
                .result(user)
                .build());
    }

    // Delete user
        @DeleteMapping({"/{userId}", "/deleteUser/{userId}"})
    public ResponseEntity<APIResponse<String>> deleteUser(@PathVariable String userId) {
        String result = userService.deleteUser(userId);
        return ResponseEntity.ok(APIResponse.<String>builder()
                .code(200)
                .message("User deleted")
                .result(result)
                .build());
    }

    // List users (simple pagination could be added later)
        @GetMapping({"", "/getAllUser"})
    public ResponseEntity<APIResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(APIResponse.<List<UserResponse>>builder()
                .code(200)
                .message("Users retrieved")
                .result(users)
                .build());
    }

    // User Address endpoints
    @PostMapping("/{userId}/addresses")
    public ResponseEntity<APIResponse<UserAddressResponse>> addAddress(@PathVariable Long userId,
                                                       @RequestBody UserAddressCreationRequest request) {
        UserAddressResponse addr = userService.addAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.<UserAddressResponse>builder()
                .code(201)
                .message("Address created")
                .result(addr)
                .build());
    }

    @GetMapping("/{userId}/addresses")
    public ResponseEntity<APIResponse<List<UserAddressResponse>>> getUserAddresses(@PathVariable Long userId) {
        List<UserAddressResponse> list = userService.getUserAddresses(userId);
        return ResponseEntity.ok(APIResponse.<List<UserAddressResponse>>builder()
                .code(200)
                .message("Addresses retrieved")
                .result(list)
                .build());
    }

    @PutMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<APIResponse<UserAddressResponse>> updateAddress(@PathVariable Long userId,
                                                          @PathVariable Long addressId,
                                                          @RequestBody UserAddressCreationRequest request) {
        UserAddressResponse addr = userService.updateAddress(userId, addressId, request);
        return ResponseEntity.ok(APIResponse.<UserAddressResponse>builder()
                .code(200)
                .message("Address updated")
                .result(addr)
                .build());
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    public ResponseEntity<APIResponse<String>> deleteAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        userService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(APIResponse.<String>builder()
                .code(200)
                .message("Address deleted")
                .result("Address deleted successfully")
                .build());
    }

    @PutMapping("/{userId}/addresses/{addressId}/set-default")
    public ResponseEntity<APIResponse<UserAddressResponse>> setDefaultAddress(@PathVariable Long userId,
                                                             @PathVariable Long addressId) {
        UserAddressResponse def = userService.setDefaultAddress(userId, addressId);
        return ResponseEntity.ok(APIResponse.<UserAddressResponse>builder()
                .code(200)
                .message("Default address set")
                .result(def)
                .build());
    }

    @GetMapping("/{userId}/addresses/default")
    public ResponseEntity<APIResponse<UserAddressResponse>> getDefaultAddress(@PathVariable Long userId) {
        UserAddressResponse def = userService.getDefaultAddress(userId);
        return ResponseEntity.ok(APIResponse.<UserAddressResponse>builder()
                .code(200)
                .message("Default address retrieved")
                .result(def)
                .build());
    }
}

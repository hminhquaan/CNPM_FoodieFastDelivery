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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {
    UserService userService;

    @PostMapping({"/userCreated"})
    public APIResponse<UserResponse> createUser(@RequestBody UserCreationRequest request) {
        APIResponse<UserResponse> response = new APIResponse<>();
        response.setResult(userService.createUser(request));
        return response;
    }

    @PutMapping({"/UpdateUser/{userId}"})
    public APIResponse<UserResponse> updateUser(@RequestBody UserCreationRequest request, @PathVariable String userId) {
        APIResponse<UserResponse> response = new APIResponse<>();
        response.setResult(userService.updateUser(request, userId));
        return response;
    }

    @GetMapping({"/GetUserById/{userId}"})
    public APIResponse<UserResponse> getUserById(@PathVariable String userId) {
        APIResponse<UserResponse> response = new APIResponse<>();
        response.setResult(userService.getUserById(userId));
        return response;
    }

    @DeleteMapping({"/deleteUser/{userId}"})
    public APIResponse<String> deleteUser(@PathVariable String userId) {
        APIResponse<String> response = new APIResponse<>();
        response.setResult(userService.deleteUser(userId));
        return response;
    }
    @GetMapping({"/getAllUser"})
    public APIResponse getAllUser() {
        APIResponse response = new APIResponse<>();
        response.setResult(userService.getAllUsers());
        return response;
    }

    // User Address endpoints
    @PostMapping("/{userId}/addresses")
    public APIResponse<UserAddressResponse> addAddress(@PathVariable Long userId,
                                                       @RequestBody UserAddressCreationRequest request) {
        APIResponse<UserAddressResponse> response = new APIResponse<>();
        response.setResult(userService.addAddress(userId, request));
        return response;
    }

    @GetMapping("/{userId}/addresses")
    public APIResponse<List<UserAddressResponse>> getUserAddresses(@PathVariable Long userId) {
        APIResponse<List<UserAddressResponse>> response = new APIResponse<>();
        response.setResult(userService.getUserAddresses(userId));
        return response;
    }

    @PutMapping("/{userId}/addresses/{addressId}")
    public APIResponse<UserAddressResponse> updateAddress(@PathVariable Long userId,
                                                          @PathVariable Long addressId,
                                                          @RequestBody UserAddressCreationRequest request) {
        APIResponse<UserAddressResponse> response = new APIResponse<>();
        response.setResult(userService.updateAddress(userId, addressId, request));
        return response;
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    public APIResponse<String> deleteAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        APIResponse<String> response = new APIResponse<>();
        userService.deleteAddress(userId, addressId);
        response.setResult("Address deleted successfully");
        return response;
    }

    @PutMapping("/{userId}/addresses/{addressId}/set-default")
    public APIResponse<UserAddressResponse> setDefaultAddress(@PathVariable Long userId,
                                                             @PathVariable Long addressId) {
        APIResponse<UserAddressResponse> response = new APIResponse<>();
        response.setResult(userService.setDefaultAddress(userId, addressId));
        return response;
    }

    @GetMapping("/{userId}/addresses/default")
    public APIResponse<UserAddressResponse> getDefaultAddress(@PathVariable Long userId) {
        APIResponse<UserAddressResponse> response = new APIResponse<>();
        response.setResult(userService.getDefaultAddress(userId));
        return response;
    }
}

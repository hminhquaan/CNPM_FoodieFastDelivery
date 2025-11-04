package service.user;

import dto.request.User.UserAddressCreationRequest;
import dto.request.User.UserCreationRequest;
import dto.response.User.UserAddressResponse;
import dto.response.User.UserResponse;

import java.util.List;

public interface UserService {

    // User methods
    UserResponse createUser(UserCreationRequest request);
    UserResponse updateUser(UserCreationRequest request, String userId);
    UserResponse getUserById(String userId);
    String deleteUser(String userId);
    List<UserResponse> getAllUsers();

    // User Address methods
    UserAddressResponse addAddress(Long userId, UserAddressCreationRequest request);
    List<UserAddressResponse> getUserAddresses(Long userId);
    UserAddressResponse updateAddress(Long userId, Long addressId, UserAddressCreationRequest request);
    void deleteAddress(Long userId, Long addressId);
    UserAddressResponse setDefaultAddress(Long userId, Long addressId);
    UserAddressResponse getDefaultAddress(Long userId);
}

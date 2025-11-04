package mapper;

import dto.request.User.UserCreationRequest;
import dto.request.User.UserAddressCreationRequest;
import dto.response.User.UserResponse;
import dto.response.User.UserAddressResponse;
import entity.User;
import entity.UserAddress;
import enums.Gender;
import org.mapstruct.*;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Mapper(componentModel = "spring", imports = {LocalDate.class, Gender.class})
public interface UserMapper {

    // Request -> Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", source = "password") // chuyển password -> passwordHash
    @Mapping(target = "status", ignore = true) // dùng default Active
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "roles", ignore = true) // roles sẽ được set riêng trong service
    @Mapping(target = "dateOfBirth",
            expression = "java(request.getDateOfBirth() != null && !request.getDateOfBirth().isEmpty() ? LocalDate.parse(request.getDateOfBirth()) : null)")
    @Mapping(target = "gender",
            expression = "java(request.getGender() != null ? Gender.valueOf(request.getGender()) : null)")
    User toUser(UserCreationRequest request);

    // Entity -> Response
    UserResponse toResponse(User user);

    // Update entity từ request (patch)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "passwordHash", source = "password")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "roles", ignore = true) // roles không được update qua method này
    @Mapping(target = "dateOfBirth",
            expression = "java(request.getDateOfBirth() != null && !request.getDateOfBirth().isEmpty() ? LocalDate.parse(request.getDateOfBirth()) : null)")
    @Mapping(target = "gender",
            expression = "java(request.getGender() != null ? Gender.valueOf(request.getGender()) : null)")
    void updateUser(@MappingTarget User user, UserCreationRequest request);

    // UserAddress mapping methods
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true) // will be set in service
    UserAddress toUserAddress(UserAddressCreationRequest request);

    UserAddressResponse toUserAddressResponse(UserAddress userAddress);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateUserAddress(@MappingTarget UserAddress userAddress, UserAddressCreationRequest request);
    
}
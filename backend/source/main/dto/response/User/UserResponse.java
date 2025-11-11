package dto.response.User;

import enums.Gender;
import enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {

    Long id;
    String username;
    String fullName;
    String email;
    String phone;

    UserStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDate dateOfBirth;
    Gender gender;

    // Role names for display in admin UI
    List<String> roles;
}

package dto.request.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {

    @NotBlank
    @Size(max = 50)
    String username;

    @NotBlank
    @Size(min = 6, max = 255, message = "Mật khẩu phải từ 6-255 ký tự")
    String password;

    @Email
    @NotBlank
    @Size(max = 100)
    String email;

    @NotBlank
    @Size(max = 150)
    String fullName;

    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không hợp lệ")
    @Size(max = 20)
    String phone;

    String dateOfBirth; // ISO format (yyyy-MM-dd), sẽ convert sang LocalDate trong mapper/service

    String gender; // "MALE", "FEMALE", "OTHER"

    // Danh sách role IDs để gán cho user (Optional - có thể null hoặc empty)
    List<Long> roleIds;
}
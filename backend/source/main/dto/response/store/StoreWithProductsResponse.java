package dto.response.store;

import dto.response.product.ProductResponse;
import enums.StoreStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreWithProductsResponse {
    Long id;
    Long ownerUserId;
    String name;
    String description;
    String bankAccountName;
    String bankAccountNumber;
    String bankName;
    String bankBranch;
    String payoutEmail;
    StoreStatus storeStatus;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    // Danh sách sản phẩm của cửa hàng
    List<ProductResponse> products;

    // Thống kê
    Integer totalProducts;
    Integer availableProducts;
}

package dto.request.product;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    @NotNull(message = "Category ID is required")
    Long categoryId;

    @NotNull(message = "Store ID is required")
    Long storeId;

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 150, message = "Product name must not exceed 150 characters")
    String name;

    String description;

    @NotNull(message = "Base price is required")
    @Positive(message = "Base price must be positive")
    BigDecimal basePrice;

    String currency;
    String mediaPrimaryUrl;

    @Min(value = 0, message = "Safety stock must be non-negative")
    Integer safetyStock;

    @Min(value = 0, message = "Quantity available must be non-negative")
    Integer quantityAvailable;

    @Min(value = 0, message = "Reserved quantity must be non-negative")
    Integer reservedQuantity;

    String extraJson;

    @Min(value = 0, message = "Weight must be non-negative")
    Integer weightGram;

    @Positive(message = "Length must be positive")
    BigDecimal lengthCm;

    @Positive(message = "Width must be positive")
    BigDecimal widthCm;

    @Positive(message = "Height must be positive")
    BigDecimal heightCm;
}

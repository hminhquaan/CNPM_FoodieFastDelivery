package dto.response.product;

import enums.ProductStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private Long storeId;
    private Long categoryId;
    private String sku;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String currency;
    private String mediaPrimaryUrl;
    private Integer weightGram;
    private Integer quantityAvailable;
    private Integer reservedQuantity;
    private Integer safetyStock;
    private ProductStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

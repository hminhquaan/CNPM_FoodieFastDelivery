package entity;

import enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product",
       uniqueConstraints = @UniqueConstraint(name = "uniq_store_sku", columnNames = {"store_id", "sku"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false,
               foreignKey = @ForeignKey(name = "fk_product_category"))
    ProductCategory category;

    @Column(name = "sku", nullable = false, length = 100)
    String sku;

    @Column(name = "name", nullable = false, length = 150)
    String name;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "store_id", nullable = false, insertable = false, updatable = false)
    Long storeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    Store store;

    @Builder.Default
    @Column(name = "safety_stock")
    Integer safetyStock = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    BigDecimal basePrice;

    @Column(name = "currency", length = 3)
    String currency;

    @Column(name = "media_primary_url", length = 500)
    String mediaPrimaryUrl;

    @Column(name = "extra_json", columnDefinition = "json")
    String extraJson;

    @Column(name = "weight_gram")
    Integer weightGram;

    @Column(name = "length_cm", precision = 6, scale = 2)
    BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 6, scale = 2)
    BigDecimal widthCm;

    @Column(name = "height_cm", precision = 6, scale = 2)
    BigDecimal heightCm;

    @Builder.Default
    @Column(name = "quantity_available")
    Integer quantityAvailable = 0;

    @Builder.Default
    @Column(name = "reserved_quantity")
    Integer reservedQuantity = 0;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "datetime DEFAULT CURRENT_TIMESTAMP")
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false,
            columnDefinition = "datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    LocalDateTime updatedAt;
}

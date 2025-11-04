package entity;

import enums.StoreStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "store")
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @Column(name = "owner_user_id", nullable = false)
    Long ownerUserId;

    @Column(name = "name", nullable = false, length = 200)
    String name;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "bank_account_name")
    String bankAccountName;

    @Column(name = "bank_account_number", length = 64)
    String bankAccountNumber;

    @Column(name = "bank_name")
    String bankName;

    @Column(name = "bank_branch")
    String bankBranch;

    @Column(name = "payout_email")
    String payoutEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    StoreStatus storeStatus;

    @Column(name = "created_at", updatable = false, insertable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    LocalDateTime updatedAt;
}
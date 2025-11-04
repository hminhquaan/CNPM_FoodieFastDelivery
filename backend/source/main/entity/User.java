package entity;

import enums.UserStatus;
import enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    String fullName;

    @Column(name = "phone", unique = true, length = 20)
    String phone;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            columnDefinition = "enum('ACTIVE','LOCKED','PENDING') default 'PENDING'"
    )
    @Builder.Default
    UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", updatable = false, insertable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    LocalDateTime updatedAt;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", columnDefinition = "enum('MALE','FEMALE','OTHER')")
    Gender gender;

    // Many-to-Many relationship with Role through user_role table
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    Set<Roles> roles;

    // One-to-Many relationship with UserAddress
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    List<UserAddress> addresses;
}

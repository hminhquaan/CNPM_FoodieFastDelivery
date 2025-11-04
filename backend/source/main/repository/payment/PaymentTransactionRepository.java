package repository.payment;

import entity.PaymentTransaction;
import enums.PaymentTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByOrderId(Long orderId);

    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);

    List<PaymentTransaction> findByStatus(PaymentTransactionStatus status);

    List<PaymentTransaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<PaymentTransaction> findByStatusAndCreatedAtBetween(
        PaymentTransactionStatus status,
        LocalDateTime start,
        LocalDateTime end
    );
}


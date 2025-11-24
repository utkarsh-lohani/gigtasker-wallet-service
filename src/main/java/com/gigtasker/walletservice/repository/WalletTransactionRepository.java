package com.gigtasker.walletservice.repository;

import com.gigtasker.walletservice.entity.WalletTransaction;
import com.gigtasker.walletservice.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    Optional<WalletTransaction> findByTaskIdAndType(Long taskId, TransactionType type);
}

package com.gigtasker.walletservice.service;

import com.gigtasker.walletservice.entity.*;
import com.gigtasker.walletservice.enums.TransactionType;
import com.gigtasker.walletservice.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WalletService {

    private WalletService self;

    @Autowired
    @Lazy
    private void setSelf(WalletService self) {
        this.self = self;
    }

    public WalletService(WalletRepository walletRepository, WalletTransactionRepository walletTransactionRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public Wallet getWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> createWallet(userId));
    }

    private Wallet createWallet(UUID userId) {
        return walletRepository.save(Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .heldFunds(BigDecimal.ZERO)
                .build());
    }

    @Transactional
    public Wallet depositFunds(UUID userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be positive");

        Wallet wallet = self.getWallet(userId);
        wallet.setBalance(wallet.getBalance().add(amount));

        logTransaction(wallet.getId(), amount, TransactionType.DEPOSIT, "Added funds", null);
        return walletRepository.save(wallet);
    }

    @Transactional
    public void holdFunds(UUID userId, BigDecimal amount, Long taskId) {
        Wallet wallet = self.getWallet(userId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds: Balance is " + wallet.getBalance());
        }

        // Move from Balance -> Held
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setHeldFunds(wallet.getHeldFunds().add(amount));

        walletRepository.save(wallet);
        logTransaction(wallet.getId(), amount, TransactionType.HOLD, "Funds held for Task #" + taskId, taskId);
    }

    @Transactional
    public void transferFunds(UUID posterId, UUID workerId, BigDecimal amount, Long taskId) {
        Wallet posterWallet = self.getWallet(posterId);
        Wallet workerWallet = self.getWallet(workerId);

        // A. Deduct from Poster's HELD funds (not balance)
        if (posterWallet.getHeldFunds().compareTo(amount) < 0) {
            throw new IllegalStateException("System Error: Escrow underflow for Poster " + posterId);
        }
        posterWallet.setHeldFunds(posterWallet.getHeldFunds().subtract(amount));

        // B. Add to Worker's BALANCE
        workerWallet.setBalance(workerWallet.getBalance().add(amount));

        walletRepository.save(posterWallet);
        walletRepository.save(workerWallet);

        // Audit logs
        logTransaction(posterWallet.getId(), amount.negate(), TransactionType.RELEASE, "Payment sent for Task #" + taskId, taskId);
        logTransaction(workerWallet.getId(), amount, TransactionType.DEPOSIT, "Payment received for Task #" + taskId, taskId);
    }

    private void logTransaction(Long walletId, BigDecimal amount, TransactionType type, String desc, Long taskId) {
        WalletTransaction txn = WalletTransaction.builder()
                .walletId(walletId)
                .amount(amount)
                .type(type)
                .description(desc)
                .taskId(taskId)
                .timestamp(LocalDateTime.now())
                .build();
        walletTransactionRepository.save(txn);
    }
}
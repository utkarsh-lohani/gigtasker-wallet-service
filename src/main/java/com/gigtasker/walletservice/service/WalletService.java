package com.gigtasker.walletservice.service;

import com.gigtasker.walletservice.entity.Wallet;
import com.gigtasker.walletservice.entity.WalletTransaction;
import com.gigtasker.walletservice.enums.TransactionType;
import com.gigtasker.walletservice.repository.WalletRepository;
import com.gigtasker.walletservice.repository.WalletTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    private WalletService self;

    // Self-inject to allow calling @Transactional methods from within the same class
    // This ensures the Transaction Proxy intercepts internal calls (like createWalletIsolated)
    @Lazy
    @Autowired
    private void setSelf(WalletService self) {
        this.self = self;
    }

    public WalletService(WalletRepository walletRepository, WalletTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    // --- READ OPERATIONS ---

    @Transactional(readOnly = true)
    public List<WalletTransaction> getWalletTransactionsHistory(UUID uuid) {
        // Use 'self' to ensure getWallet's transaction logic runs
        Wallet wallet = self.getWallet(uuid);
        return transactionRepository.findAllByWalletIdOrderByTimestampDesc(wallet.getId());
    }

    /**
     * Gets a wallet, creating it if it doesn't exist.
     * Uses a "Check-Then-Act" pattern with a safe fallback for race conditions.
     */
    @Transactional
    public Wallet getWallet(UUID userId) {
        // 1. Optimistic fetch: try to find it normally
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    try {
                        // 2. If missing, try to create it in a separate, isolated transaction.
                        // If this fails (duplicate key), it won't poison the main transaction.
                        return self.createWalletIsolated(userId);
                    } catch (DataIntegrityViolationException e) {
                        // 3. We lost the race; fetch the wallet the other thread created.
                        return walletRepository.findByUserId(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet creation failed unexpectedly"));
                    }
                });
    }

    // --- WRITE OPERATIONS (LOCKED) ---

    @Transactional
    public Wallet depositFunds(UUID userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        // LOCK the wallet to prevent lost updates
        Wallet wallet = self.getWalletWithLock(userId);

        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet savedWallet = walletRepository.save(wallet);

        logTransaction(savedWallet.getId(), amount, TransactionType.DEPOSIT, "Funds Deposited", null);
        log.info("User {} deposited ${}", userId, amount);

        return savedWallet;
    }

    @Transactional
    public void holdFunds(UUID userId, BigDecimal amount, Long taskId) {
        Wallet wallet = self.getWalletWithLock(userId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds for Task #" + taskId);
        }

        // Move from Balance -> Held
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setHeldFunds(wallet.getHeldFunds().add(amount));
        walletRepository.save(wallet);

        logTransaction(wallet.getId(), amount, TransactionType.HOLD, "Held for Task #" + taskId, taskId);
    }

    @Transactional
    public void releaseFunds(UUID posterId, UUID workerId, BigDecimal amount, Long taskId) {
        // 1. Deduct from Poster (Locked)
        Wallet posterWallet = self.getWalletWithLock(posterId);

        if (posterWallet.getHeldFunds().compareTo(amount) < 0) {
            // This suggests a logic error elsewhere, but we must protect the ledger.
            log.error("Data Integrity Error: Poster {} has insufficient held funds!", posterId);
            throw new IllegalStateException("Insufficient held funds to release.");
        }

        posterWallet.setHeldFunds(posterWallet.getHeldFunds().subtract(amount));
        walletRepository.save(posterWallet);
        logTransaction(posterWallet.getId(), amount, TransactionType.RELEASE, "Payout for Task #" + taskId, taskId);

        // 2. Add to Worker (Locked)
        // Acquiring locks in a consistent order (Poster then Worker) prevents deadlocks in this flow.
        Wallet workerWallet = self.getWalletWithLock(workerId);
        workerWallet.setBalance(workerWallet.getBalance().add(amount));
        walletRepository.save(workerWallet);
        logTransaction(workerWallet.getId(), amount, TransactionType.DEPOSIT, "Payment received for Task #" + taskId, taskId);
    }

    @Transactional
    public void refundFunds(UUID posterId, Long taskId) {
        Wallet wallet = self.getWalletWithLock(posterId);

        // Find original Hold transaction to know exact amount
        WalletTransaction holdTx = transactionRepository.findByTaskIdAndType(taskId, TransactionType.HOLD)
                .orElseThrow(() -> new RuntimeException("No active HOLD found for Task " + taskId));

        BigDecimal amount = holdTx.getAmount();

        // Reverse: Held -> Balance
        wallet.setHeldFunds(wallet.getHeldFunds().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        logTransaction(wallet.getId(), amount, TransactionType.REFUND, "Refund for Task #" + taskId, taskId);
    }

    // --- HELPERS ---

    /**
     * Helper to lock the wallet row. Must be called inside an existing transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    protected Wallet getWalletWithLock(UUID userId) {
        // Ensure wallet exists first using the safe method (handles creation race conditions)
        self.getWallet(userId);

        // Now fetch it again with a PESSIMISTIC_WRITE lock
        return walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found (unexpected)"));
    }

    /**
     * Creates a wallet in a completely new transaction.
     * If this fails (due to constraint violation), it rolls back ONLY this method,
     * leaving the caller's transaction intact.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Wallet createWalletIsolated(UUID userId) {
        return walletRepository.saveAndFlush(Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .heldFunds(BigDecimal.ZERO)
                .build());
    }

    private void logTransaction(Long walletId, BigDecimal amount, TransactionType type, String desc, Long taskId) {
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(walletId)
                .amount(amount)
                .type(type)
                .description(desc)
                .taskId(taskId)
                .build();
        transactionRepository.save(tx);
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
}
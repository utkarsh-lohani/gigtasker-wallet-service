package com.gigtasker.walletservice.enums;

public enum TransactionType {
    DEPOSIT,      // User adds money
    WITHDRAWAL,   // User takes money out
    HOLD,         // Money moved to Escrow (Bid Accepted)
    RELEASE,      // Money moved from Escrow to Worker (Task Complete)
    REFUND        // Money moved from Escrow back to Balance (Task Cancelled)
}

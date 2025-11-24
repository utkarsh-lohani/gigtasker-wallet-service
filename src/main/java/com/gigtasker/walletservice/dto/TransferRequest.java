package com.gigtasker.walletservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(UUID posterId, UUID workerId, BigDecimal amount, Long taskId) {}

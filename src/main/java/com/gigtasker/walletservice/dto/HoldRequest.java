package com.gigtasker.walletservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record HoldRequest(UUID userId, BigDecimal amount, Long taskId) {}

package com.gigtasker.walletservice.dto;

import java.util.UUID;

public record TaskCancelledEvent(Long taskId, UUID posterId) {}

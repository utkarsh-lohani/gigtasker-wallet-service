package com.gigtasker.walletservice.dto;

import java.util.UUID;

public record TaskCompletedEvent(TaskDTO task, UUID posterId, UUID workerId) {}

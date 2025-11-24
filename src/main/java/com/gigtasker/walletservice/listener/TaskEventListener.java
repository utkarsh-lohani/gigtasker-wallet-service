package com.gigtasker.walletservice.listener;

import com.gigtasker.walletservice.dto.TaskCompletedEvent;
import com.gigtasker.walletservice.entity.WalletTransaction;
import com.gigtasker.walletservice.enums.TransactionType;
import com.gigtasker.walletservice.repository.WalletTransactionRepository;
import com.gigtasker.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaskEventListener {

    private final WalletService walletService;
    private final WalletTransactionRepository transactionRepository;

    @RabbitListener(queues = "wallet.task.completed.queue")
    public void handleTaskCompleted(TaskCompletedEvent event) {
        Long taskId = event.task().getId();
        log.info("Received Completion Event for Task {}", taskId);

        try {
            WalletTransaction holdTx = transactionRepository.findByTaskIdAndType(taskId, TransactionType.HOLD)
                    .orElseThrow(() -> new RuntimeException("Critical: No HOLD transaction found for Task " + taskId));

            BigDecimal amount = holdTx.getAmount();

            walletService.transferFunds(
                    event.posterId(),
                    event.workerId(),
                    amount,
                    taskId
            );

            log.info("✅ PAYMENT SUCCESS: ${} moved from Poster {} to Worker {}", amount, event.posterId(), event.workerId());

        } catch (Exception e) {
            log.error("PAYMENT FAILED for Task #{}", taskId, e);
        }
    }
}

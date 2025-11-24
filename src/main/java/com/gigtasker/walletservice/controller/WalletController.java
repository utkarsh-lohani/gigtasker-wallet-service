package com.gigtasker.walletservice.controller;

import com.gigtasker.walletservice.dto.HoldRequest;
import com.gigtasker.walletservice.dto.TransferRequest;
import com.gigtasker.walletservice.entity.Wallet;
import com.gigtasker.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public ResponseEntity<Wallet> getWallet(@AuthenticationPrincipal Jwt jwt){
        UUID userId = UUID.fromString(jwt.getClaimAsString("sub"));
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<Wallet> deposit(@AuthenticationPrincipal Jwt jwt, @RequestBody BigDecimal amount) {
        UUID userId = UUID.fromString(jwt.getClaimAsString("sub"));
        return ResponseEntity.ok(walletService.depositFunds(userId, amount));
    }

    @PostMapping("/hold")
    public ResponseEntity<Void> holdFunds(@RequestBody HoldRequest request) {
        walletService.holdFunds(request.userId(), request.amount(), request.taskId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transferFunds(@RequestBody TransferRequest request) {
        walletService.transferFunds(
                request.posterId(),
                request.workerId(),
                request.amount(),
                request.taskId());
        return ResponseEntity.ok().build();
    }
}

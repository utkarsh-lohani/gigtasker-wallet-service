package com.gigtasker.walletservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WalletServiceApplication {

    private WalletServiceApplication() {}

    static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }

}

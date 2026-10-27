package dev.proofstack.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Optional "ask my portfolio" service (ADR-002, ADR-007). The static site works without it.
 * Components are declared explicitly in {@link ServiceConfiguration}; adapters are not scanned.
 */
@SpringBootApplication(scanBasePackages = "dev.proofstack.service")
@EnableConfigurationProperties(ProofstackProperties.class)
public class ProofstackAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProofstackAiServiceApplication.class, args);
    }
}

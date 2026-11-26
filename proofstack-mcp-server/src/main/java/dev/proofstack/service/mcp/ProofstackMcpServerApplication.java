package dev.proofstack.service.mcp;

import dev.proofstack.adapter.in.mcp.PortfolioMcpTools;
import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local MCP server over stdio (ADR-008). stdout belongs to the protocol, so the profile is loaded and
 * validated <em>before</em> Spring starts and every problem is reported on stderr.
 */
@SpringBootApplication(scanBasePackages = "dev.proofstack.service.mcp")
public class ProofstackMcpServerApplication {

    public static final String PROFILE_ENV = "PROOFSTACK_PROFILE";

    public static void main(String[] args) {
        Profile profile;
        try {
            profile = loadProfile(System.getenv(PROFILE_ENV));
        } catch (IllegalArgumentException | ProfileValidationException e) {
            System.err.println("proofstack-mcp: " + e.getMessage());
            System.exit(1);
            return;
        }
        SpringApplication app = new SpringApplication(ProofstackMcpServerApplication.class);
        app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("profile", profile));
        app.run(args);
    }

    static Profile loadProfile(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException(PROFILE_ENV + " must point to your profile.yaml");
        }
        Path path = Path.of(location.strip());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("profile not found: " + path.toAbsolutePath());
        }
        return new YamlProfileSource().load(path);
    }

    @Bean
    PortfolioMcpTools portfolioMcpTools(Profile profile) {
        return new PortfolioMcpTools(profile);
    }
}

package dev.proofstack.adapter.in.rest;

import dev.proofstack.core.domain.knowledge.Answer;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.port.in.AskPortfolioUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntSupplier;

/** Driving adapter: HTTP API of the "ask my portfolio" assistant. */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class AskController {

    public record AskRequest(String question) {
    }

    public record CitationDto(String id, String type, String title) {
    }

    public record AskResponse(String status, String answer, List<CitationDto> citations, List<String> warnings) {
        static AskResponse from(Answer answer) {
            return new AskResponse(answer.status().name(), answer.text(),
                    answer.citations().stream().map(AskController::citation).toList(), answer.warnings());
        }
    }

    public record StatusResponse(int indexedChunks, String model) {
    }

    /** Read-only facts exposed by {@code /api/status}. */
    public record ServiceInfo(IntSupplier indexedChunks, String model) {
    }

    private final AskPortfolioUseCase ask;
    private final ServiceInfo info;

    public AskController(AskPortfolioUseCase ask, ServiceInfo info) {
        this.ask = Objects.requireNonNull(ask, "ask");
        this.info = Objects.requireNonNull(info, "info");
    }

    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AskResponse ask(@RequestBody AskRequest request) {
        return AskResponse.from(ask.ask(request == null ? null : request.question()));
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(info.indexedChunks().getAsInt(), info.model());
    }

    private static CitationDto citation(KnowledgeChunk chunk) {
        return new CitationDto(chunk.id(), chunk.type().name().toLowerCase(Locale.ROOT), chunk.title());
    }
}

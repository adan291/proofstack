package dev.proofstack.service;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.knowledge.Answer;
import dev.proofstack.core.domain.knowledge.InvalidQuestionException;
import dev.proofstack.core.port.in.AskPortfolioUseCase;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.Objects;

/**
 * Decorator that adds domain metrics around the use case, so neither the core nor the REST adapter
 * depends on Micrometer. Only the question <em>length</em> is recorded, never its text.
 */
final class ObservedAskPortfolio implements AskPortfolioUseCase {

    static final String TIMER = "proofstack.ask";
    static final String QUESTION_LENGTH = "proofstack.ask.question.length";

    private final AskPortfolioUseCase delegate;
    private final MeterRegistry registry;
    private final DistributionSummary questionLength;

    ObservedAskPortfolio(AskPortfolioUseCase delegate, MeterRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.questionLength = DistributionSummary.builder(QUESTION_LENGTH)
                .description("Length in characters of the questions received")
                .baseUnit("characters")
                .register(registry);
    }

    @Override
    public Answer ask(String question) {
        Timer.Sample sample = Timer.start(registry);
        String status = "error";
        try {
            Answer answer = delegate.ask(question);
            status = answer.status().name().toLowerCase(Locale.ROOT);
            return answer;
        } catch (InvalidQuestionException e) {
            status = "invalid";
            throw e;
        } catch (ContentGenerationException e) {
            status = "unavailable";
            throw e;
        } finally {
            if (question != null) {
                questionLength.record(question.length());
            }
            sample.stop(Timer.builder(TIMER)
                    .description("Time to answer a portfolio question, by outcome")
                    .tag("status", status)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }
}

package dev.proofstack.core.port.in;

import dev.proofstack.core.domain.knowledge.Answer;

/** Driving port: answer a visitor's question about the portfolio. */
public interface AskPortfolioUseCase {

    /**
     * @throws dev.proofstack.core.domain.knowledge.InvalidQuestionException if the question is empty or too long
     */
    Answer ask(String question);
}

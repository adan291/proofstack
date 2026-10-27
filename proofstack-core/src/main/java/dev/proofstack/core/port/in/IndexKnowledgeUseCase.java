package dev.proofstack.core.port.in;

/** Driving port: (re)build the knowledge index from the profile. */
public interface IndexKnowledgeUseCase {

    /** @return number of indexed chunks */
    int index();
}

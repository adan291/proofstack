package dev.proofstack.core.domain.assist;

/** What the author asks of the assistant. */
public enum AssistMode {
    /** Rewrite the text for its purpose, following the author's request if there is one. */
    IMPROVE,
    /** Same text, same facts, in another language. */
    TRANSLATE,
    /** Rewrite the text to stress what matches a pasted job offer. */
    JOB_OFFER
}

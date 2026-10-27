package dev.proofstack.core.domain.knowledge;

import java.util.List;

/** What a generator proposes. Untrusted: {@code AskService} checks it before it becomes an {@link Answer}. */
public record DraftAnswer(boolean answerable, String text, List<String> citedIds) {

    public DraftAnswer {
        text = text == null ? "" : text.strip();
        citedIds = citedIds == null ? List.of() : citedIds.stream().filter(java.util.Objects::nonNull)
                .map(String::strip).distinct().toList();
    }
}

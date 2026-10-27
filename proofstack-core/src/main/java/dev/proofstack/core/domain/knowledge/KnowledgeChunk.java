package dev.proofstack.core.domain.knowledge;

import java.util.Objects;

/** A retrievable piece of the profile. The id is stable across rebuilds so citations can be linked. */
public record KnowledgeChunk(String id, ChunkType type, String title, String text) {

    public enum ChunkType { PERSON, EXPERIENCE, EDUCATION, PROJECT, EVIDENCE, LAB, SKILLS }

    public KnowledgeChunk {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("chunk " + id + " has no text");
        }
    }
}

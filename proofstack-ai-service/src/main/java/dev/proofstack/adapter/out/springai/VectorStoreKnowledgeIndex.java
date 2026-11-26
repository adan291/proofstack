package dev.proofstack.adapter.out.springai;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.domain.knowledge.ScoredChunk;
import dev.proofstack.core.port.out.KnowledgeIndex;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Driven adapter: {@link KnowledgeIndex} on top of any Spring AI {@link VectorStore}.
 *
 * <p>Some embedding models are trained with task prefixes (nomic-embed-text expects
 * {@code search_document:} / {@code search_query:}); they are configurable and empty by default
 * at this level so the adapter stays model-agnostic.
 */
public final class VectorStoreKnowledgeIndex implements KnowledgeIndex {

    static final String CHUNK_ID = "chunkId";

    private final VectorStore store;
    private final String documentPrefix;
    private final String queryPrefix;
    private final Map<String, KnowledgeChunk> chunks = new ConcurrentHashMap<>();

    public VectorStoreKnowledgeIndex(VectorStore store, String documentPrefix, String queryPrefix) {
        this.store = Objects.requireNonNull(store, "store");
        this.documentPrefix = Objects.requireNonNullElse(documentPrefix, "");
        this.queryPrefix = Objects.requireNonNullElse(queryPrefix, "");
    }

    @Override
    public synchronized void index(List<KnowledgeChunk> newChunks) {
        if (!chunks.isEmpty()) {
            store.delete(List.copyOf(chunks.keySet()));
            chunks.clear();
        }
        List<Document> documents = new ArrayList<>();
        for (KnowledgeChunk chunk : newChunks) {
            chunks.put(chunk.id(), chunk);
            documents.add(new Document(chunk.id(), documentPrefix + chunk.title() + "\n" + chunk.text(),
                    Map.of(CHUNK_ID, chunk.id(), "type", chunk.type().name())));
        }
        if (!documents.isEmpty()) {
            store.add(documents);
        }
    }

    @Override
    public List<ScoredChunk> search(String query, int topK) {
        List<Document> hits;
        try {
            hits = store.similaritySearch(SearchRequest.builder()
                    .query(queryPrefix + query)
                    .topK(topK)
                    .similarityThresholdAll()
                    .build());
        } catch (RuntimeException e) {
            // The query is embedded by the model provider: down, it is the same failure as the chat model (503),
            // and its message (hosts, ports) never reaches the visitor.
            throw new ContentGenerationException("embedding model unavailable: " + e.getClass().getSimpleName(),
                    ContentGenerationException.Kind.PROVIDER_UNAVAILABLE, e);
        }
        List<ScoredChunk> result = new ArrayList<>();
        for (Document hit : hits) {
            KnowledgeChunk chunk = chunks.get(String.valueOf(hit.getMetadata().getOrDefault(CHUNK_ID, hit.getId())));
            if (chunk != null) {
                result.add(new ScoredChunk(chunk, hit.getScore() == null ? 0 : hit.getScore()));
            }
        }
        return result;
    }

    public int size() {
        return chunks.size();
    }
}

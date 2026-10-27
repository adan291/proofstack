package dev.proofstack.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Deterministic stand-ins for Ollama so tests run anywhere (CI included). */
public final class Fakes {

    private Fakes() {
    }

    /** Bag-of-words hashed into 256 dimensions: shared words ⇒ higher cosine similarity. */
    public static final class BagOfWordsEmbeddingModel implements EmbeddingModel {
        private static final int DIMENSIONS = 256;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> texts = request.getInstructions();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(new Embedding(vector(texts.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (word.length() > 2 && !word.equals("search_document") && !word.equals("search_query")) {
                    v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < v.length; i++) {
                    v[i] /= (float) norm;
                }
            }
            return v;
        }
    }

    /** Chat model that answers with a function of the prompt and records the last prompt. */
    public static final class ScriptedChatModel implements ChatModel {
        public final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();
        private volatile Function<Prompt, String> script;

        public ScriptedChatModel(Function<Prompt, String> script) {
            this.script = script;
        }

        public void script(Function<Prompt, String> newScript) {
            this.script = newScript;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(script.apply(prompt)))));
        }
    }
}

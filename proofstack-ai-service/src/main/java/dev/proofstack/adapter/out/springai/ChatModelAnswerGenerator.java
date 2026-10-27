package dev.proofstack.adapter.out.springai;

import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import dev.proofstack.core.domain.knowledge.DraftAnswer;
import dev.proofstack.core.domain.knowledge.KnowledgeChunk;
import dev.proofstack.core.port.out.AnswerGenerator;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Driven adapter: answers with any Spring AI {@link ChatModel} (Ollama today; Gemini/OpenAI by swapping the starter).
 *
 * <p>The prompt is built as plain messages (no template rendering), so braces or template syntax in
 * profile data can never be interpreted. Sources are fenced in {@code <portfolio-data>} and the model is
 * told to treat them as data (prompt-injection mitigation, ADR-006).
 */
public final class ChatModelAnswerGenerator implements AnswerGenerator {

    /** Structured output contract requested from the model. */
    public record DraftAnswerDto(boolean answerable, String answer, List<String> citations) {
    }

    private final ChatModel chatModel;
    private final BeanOutputConverter<DraftAnswerDto> converter = new BeanOutputConverter<>(DraftAnswerDto.class);
    private final String systemTemplate;

    public ChatModelAnswerGenerator(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.systemTemplate = resource("/prompts/ask-system.md");
    }

    @Override
    public DraftAnswer answer(String question, String language, List<KnowledgeChunk> context) {
        String system = systemTemplate.replace("{{language}}", languageName(language)) + "\n\n" + converter.getFormat();
        String user = "<portfolio-data>\n" + sources(context) + "</portfolio-data>\n\nQuestion: " + neutralize(question);

        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(user))));
        } catch (RuntimeException e) {
            throw new ContentGenerationException("chat model unavailable: " + e.getClass().getSimpleName(),
                    Kind.PROVIDER_UNAVAILABLE, e);
        }
        String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new ContentGenerationException("chat model returned an empty answer", Kind.INVALID_OUTPUT);
        }
        DraftAnswerDto dto;
        try {
            dto = converter.convert(text);
        } catch (RuntimeException e) {
            throw new ContentGenerationException("chat model answer is not valid JSON for the contract",
                    Kind.INVALID_OUTPUT, e);
        }
        if (dto == null) {
            throw new ContentGenerationException("chat model answer is empty JSON", Kind.INVALID_OUTPUT);
        }
        return new DraftAnswer(dto.answerable(), dto.answer(), dto.citations());
    }

    static String sources(List<KnowledgeChunk> context) {
        return context.stream()
                .map(c -> "<source id=\"" + neutralize(c.id()) + "\" type=\"" + c.type().name().toLowerCase(Locale.ROOT)
                        + "\" title=\"" + neutralize(c.title()) + "\">\n" + neutralize(c.text()) + "\n</source>\n")
                .collect(Collectors.joining());
    }

    /** Data can never open or close the fences used by the prompt. */
    static String neutralize(String text) {
        return text.replace("<", "‹").replace(">", "›").replace("\"", "'");
    }

    static String languageName(String tag) {
        String name = Locale.forLanguageTag(tag == null ? "en" : tag).getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() ? "English" : name;
    }

    private static String resource(String path) {
        try (InputStream in = ChatModelAnswerGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

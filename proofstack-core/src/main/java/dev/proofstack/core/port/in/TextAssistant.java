package dev.proofstack.core.port.in;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;

import java.util.Locale;

/** Driving port: propose a better text for one field of the profile. Never changes the profile. */
public interface TextAssistant {

    /**
     * @param profile      the saved profile: it is the only source of facts
     * @param currentText  the text being edited, which may be an unsaved draft; empty to write from the profile
     * @param request      what the author wants, or the pasted job offer in {@link AssistMode#JOB_OFFER}
     * @param targetLanguage language to translate to; ignored unless the mode is {@link AssistMode#TRANSLATE}
     * @throws IllegalArgumentException if an input is over its limit or missing for the mode
     * @throws dev.proofstack.core.domain.content.ContentGenerationException if the model fails or answers nothing usable
     */
    Suggestion suggest(Profile profile, FieldPath path, String currentText, String request, AssistMode mode,
                       Locale targetLanguage);
}

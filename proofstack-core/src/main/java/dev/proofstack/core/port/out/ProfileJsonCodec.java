package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;

/**
 * Driven port: a profile as the JSON document the editor works on (same shape as {@code profile.yaml}).
 * Lets the core turn an unsaved draft into a {@link Profile} and back without a serialisation library.
 */
public interface ProfileJsonCodec {

    /**
     * @throws dev.proofstack.core.domain.ProfileValidationException if the document is not a valid profile
     */
    Profile parse(String json);

    String toJson(Profile profile);
}

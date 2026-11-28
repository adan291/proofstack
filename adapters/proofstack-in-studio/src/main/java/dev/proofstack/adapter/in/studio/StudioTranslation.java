package dev.proofstack.adapter.in.studio;

import java.util.function.BooleanSupplier;

import dev.proofstack.core.port.in.EditProfileUseCase;

/**
 * The other-language file of the profile ({@code profile.yaml} and {@code profile.en.yaml}), where a translation is
 * written. {@code exists} is asked on every use: the file may be created while Studio runs.
 */
public record StudioTranslation(String fileName, BooleanSupplier exists, EditProfileUseCase editor) {
}

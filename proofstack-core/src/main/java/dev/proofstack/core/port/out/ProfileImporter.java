package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;

import java.nio.file.Path;

/** Driven port: builds a draft profile from a third-party source (e.g. the LinkedIn data export). */
public interface ProfileImporter {

    Profile importProfile(Path source);
}

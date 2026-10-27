package dev.proofstack.core.port.in;

import java.nio.file.Path;

/** Driving port: create a starter profile.yaml for a new user. */
public interface ScaffoldProfileUseCase {

    /**
     * @throws dev.proofstack.core.port.out.ProfileWriter.TargetExistsException if target exists and overwrite is false
     */
    void scaffold(Path target, String name, String headline, String language, boolean overwrite);
}

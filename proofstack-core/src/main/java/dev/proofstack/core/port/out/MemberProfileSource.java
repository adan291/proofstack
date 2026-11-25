package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;

/**
 * Driven port: the authenticated member's own profile, pulled from a platform API
 * (the LinkedIn Member Data Portability API today). Unlike {@link ProfileImporter} there is no file.
 *
 * @see ImportException for failures (not authorised, unavailable...)
 */
public interface MemberProfileSource {

    Profile fetchProfile();
}

package dev.proofstack.adapter.in.linkedin;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The LinkedIn app of this user (client id, secret, redirect URL), saved once by {@code proofstack setup} so that
 * importing is one click afterwards. Lives in the user's home, never in the repository, readable only by its owner.
 * Environment variables still win over the file. The access token is never saved: it expires and is asked for again.
 */
public final class LinkedInSettings {

    public static final String HOME_ENV = "PROOFSTACK_HOME";
    public static final String DEFAULT_REDIRECT = "http://127.0.0.1:63141/callback";
    private static final List<String> KEYS = List.of(LinkedInMemberDataSource.CLIENT_ID_ENV,
            LinkedInMemberDataSource.CLIENT_SECRET_ENV, LinkedInMemberDataSource.REDIRECT_ENV);

    private LinkedInSettings() {
    }

    /** {@code ~/.proofstack/linkedin.properties}, or under {@code PROOFSTACK_HOME} when it is set. */
    public static Path file(Map<String, String> env) {
        String home = env.get(HOME_ENV);
        Path base = home == null || home.isBlank() ? Path.of(System.getProperty("user.home"), ".proofstack") : Path.of(home);
        return base.resolve("linkedin.properties");
    }

    /** The environment with the saved app filled in where a variable is missing, and the default redirect URL. */
    public static Map<String, String> withSaved(Map<String, String> env) {
        Map<String, String> merged = new HashMap<>();
        Path file = file(env);
        if (Files.isRegularFile(file)) {
            Properties saved = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                saved.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            KEYS.forEach(k -> {
                String v = saved.getProperty(k);
                if (v != null && !v.isBlank()) {
                    merged.put(k, v.strip());
                }
            });
        }
        env.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                merged.put(k, v);
            }
        });
        merged.putIfAbsent(LinkedInMemberDataSource.REDIRECT_ENV, DEFAULT_REDIRECT);
        return Map.copyOf(merged);
    }

    /** True when an import can start: an access token, or an app to log in with. */
    public static boolean configured(Map<String, String> env) {
        Map<String, String> all = withSaved(env);
        return all.containsKey(LinkedInMemberDataSource.TOKEN_ENV)
                || (all.containsKey(LinkedInMemberDataSource.CLIENT_ID_ENV)
                && all.containsKey(LinkedInMemberDataSource.CLIENT_SECRET_ENV));
    }

    /** Saves the app atomically, with permissions for the owner only. Returns the file. */
    public static Path save(Map<String, String> env, String clientId, String clientSecret, String redirect) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("client id and client secret are required");
        }
        String redirectUri = redirect == null || redirect.isBlank() ? DEFAULT_REDIRECT : redirect.strip();
        try {
            LinkedInOAuth.loopback(java.net.URI.create(redirectUri)); // same rule as the login: a 127.0.0.1 URL
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        Path file = file(env);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "linkedin", ".tmp");
            restrict(tmp);
            Properties p = new Properties();
            p.setProperty(LinkedInMemberDataSource.CLIENT_ID_ENV, clientId.strip());
            p.setProperty(LinkedInMemberDataSource.CLIENT_SECRET_ENV, clientSecret.strip());
            p.setProperty(LinkedInMemberDataSource.REDIRECT_ENV, redirectUri);
            try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(out, "LinkedIn app for proofstack import linkedin-api. Keep it private.");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    /** rw------- on POSIX; on Windows an ACL with a single entry for the owner. */
    private static void restrict(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }
}

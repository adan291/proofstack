package dev.proofstack.adapter.out.cv;

import dev.proofstack.core.domain.Area;

/**
 * How the CV is tailored.
 *
 * @param focus       area whose skills and projects come first, or {@code null} for the profile's own order
 * @param maxProjects how many projects to show; a CV is a selection, not an inventory
 * @param job         a job posting to tailor to, or {@code null}. It only re-orders what the profile already says
 * @param phone       phone number for the contact line, or {@code null}. Given at build time and never stored in the
 *                    profile, because the profile and the published site are public
 */
public record CvOptions(Area focus, int maxProjects, JobOffer job, String phone) {

    public static final int DEFAULT_MAX_PROJECTS = 5;
    private static final int LIMIT = 12;

    public CvOptions {
        if (maxProjects < 1 || maxProjects > LIMIT) {
            throw new IllegalArgumentException("maxProjects must be between 1 and " + LIMIT);
        }
        phone = phone == null || phone.isBlank() ? null : phone.strip();
        if (phone != null && !phone.matches("\\+?[0-9][0-9 ().-]{5,19}")) {
            throw new IllegalArgumentException("phone must contain only digits, spaces, +, -, . or parentheses: " + phone);
        }
    }

    public CvOptions(Area focus, int maxProjects, JobOffer job) {
        this(focus, maxProjects, job, null);
    }

    public CvOptions(Area focus, int maxProjects) {
        this(focus, maxProjects, null, null);
    }

    public static CvOptions defaults() {
        return new CvOptions(null, DEFAULT_MAX_PROJECTS, null, null);
    }
}
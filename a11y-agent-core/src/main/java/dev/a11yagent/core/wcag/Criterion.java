package dev.a11yagent.core.wcag;

import java.util.Optional;

/**
 * A WCAG success criterion.
 *
 * @param id          e.g. "2.4.7"
 * @param name        e.g. "Focus Visible"
 * @param level       level in the version where it was introduced (and in 2.1)
 * @param since       first WCAG version containing this criterion
 * @param removedIn   version in which the criterion was removed/obsoleted (only 4.1.1 in 2.2)
 * @param levelIn22   level override in WCAG 2.2 when it differs (2.4.7 moved from AA to A)
 */
public record Criterion(
        String id,
        String name,
        Level level,
        WcagVersion since,
        Optional<WcagVersion> removedIn,
        Optional<Level> levelIn22) {

    public Criterion {
        removedIn = removedIn == null ? Optional.empty() : removedIn;
        levelIn22 = levelIn22 == null ? Optional.empty() : levelIn22;
    }

    public static Criterion of(String id, String name, Level level, WcagVersion since) {
        return new Criterion(id, name, level, since, Optional.empty(), Optional.empty());
    }

    /** Level of this criterion when evaluating against a specific WCAG version. */
    public Level levelIn(WcagVersion version) {
        if (version == WcagVersion.V2_2 && levelIn22.isPresent()) {
            return levelIn22.get();
        }
        return level;
    }

    /** True when this criterion is part of the given WCAG version. */
    public boolean existsIn(WcagVersion version) {
        if (!version.isAtLeast(since)) {
            return false;
        }
        return removedIn.map(r -> !version.isAtLeast(r)).orElse(true);
    }

    public String guidelineId() {
        int idx = id.lastIndexOf('.');
        return id.substring(0, idx);
    }

    public String principleId() {
        return id.substring(0, id.indexOf('.'));
    }

    @Override
    public String toString() {
        return id + " " + name + " (" + level + ")";
    }
}

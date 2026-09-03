package dev.a11yagent.core.journey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A scripted user journey: an optional start URL followed by named steps. Each step performs an action
 * (through the caller's own automation API, e.g. Playwright) and the resulting page state is audited.
 * Cross-step rules (consistent navigation, consistent identification, consistent help, redundant entry)
 * are evaluated over all collected states when the journey finishes.
 */
public final class Journey {

    /** One step: {@code action} runs before the page state is audited. */
    public record Step(String name, Runnable action) {
        public Step {
            Objects.requireNonNull(name, "step name");
            Objects.requireNonNull(action, "step action");
        }
    }

    private final String name;
    private final String startUrl;
    private final List<Step> steps;

    private Journey(String name, String startUrl, List<Step> steps) {
        this.name = name;
        this.startUrl = startUrl;
        this.steps = List.copyOf(steps);
    }

    public String name() {
        return name;
    }

    /** May be null when the caller navigates in the first step. */
    public String startUrl() {
        return startUrl;
    }

    public List<Step> steps() {
        return steps;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String startUrl;
        private final List<Step> steps = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /** Navigates here before the first step; the landing page is audited as step "start". */
        public Builder start(String url) {
            this.startUrl = url;
            return this;
        }

        public Builder step(String stepName, Runnable action) {
            steps.add(new Step(stepName, action));
            return this;
        }

        public Journey build() {
            if (startUrl == null && steps.isEmpty()) {
                throw new IllegalStateException("Journey needs a start URL or at least one step");
            }
            return new Journey(name, startUrl, steps);
        }
    }
}

package dev.a11yagent.core.config;

import dev.a11yagent.core.ai.ModelClient;
import dev.a11yagent.core.wcag.Level;
import dev.a11yagent.core.wcag.WcagVersion;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** Immutable configuration for an audit. Build with {@link #builder()}. */
public final class A11yConfig {

    private final WcagVersion targetVersion;
    private final Level targetLevel;
    private final Path artifactsDir;
    private final ModelClient modelClient;
    private final boolean screenshots;
    private final int maxFocusStops;
    private final int maxAiJudgements;
    private final Set<String> includeRules;
    private final Set<String> excludeRules;

    private A11yConfig(Builder b) {
        this.targetVersion = b.targetVersion;
        this.targetLevel = b.targetLevel;
        this.artifactsDir = b.artifactsDir;
        this.modelClient = b.modelClient;
        this.screenshots = b.screenshots;
        this.maxFocusStops = b.maxFocusStops;
        this.maxAiJudgements = b.maxAiJudgements;
        this.includeRules = Set.copyOf(b.includeRules);
        this.excludeRules = Set.copyOf(b.excludeRules);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static A11yConfig defaults() {
        return builder().build();
    }

    public WcagVersion targetVersion() { return targetVersion; }
    public Level targetLevel() { return targetLevel; }
    public Path artifactsDir() { return artifactsDir; }
    public Optional<ModelClient> modelClient() { return Optional.ofNullable(modelClient); }
    public boolean aiEnabled() { return modelClient != null; }
    public boolean screenshots() { return screenshots; }
    public int maxFocusStops() { return maxFocusStops; }
    public int maxAiJudgements() { return maxAiJudgements; }
    public Set<String> includeRules() { return includeRules; }
    public Set<String> excludeRules() { return excludeRules; }

    public boolean ruleEnabled(String ruleId) {
        if (excludeRules.contains(ruleId)) {
            return false;
        }
        return includeRules.isEmpty() || includeRules.contains(ruleId);
    }

    public static final class Builder {
        private WcagVersion targetVersion = WcagVersion.V2_2;
        private Level targetLevel = Level.AAA;
        private Path artifactsDir = Path.of("a11y-artifacts");
        private ModelClient modelClient;
        private boolean screenshots = true;
        private int maxFocusStops = 150;
        private int maxAiJudgements = 25;
        private Set<String> includeRules = Set.of();
        private Set<String> excludeRules = Set.of();

        public Builder targetVersion(WcagVersion v) { this.targetVersion = v; return this; }
        public Builder targetLevel(Level l) { this.targetLevel = l; return this; }
        public Builder artifactsDir(Path p) { this.artifactsDir = p; return this; }
        /** Enables AI/vision judgements with the given model client. */
        public Builder modelClient(ModelClient c) { this.modelClient = c; return this; }
        public Builder screenshots(boolean b) { this.screenshots = b; return this; }
        public Builder maxFocusStops(int n) { this.maxFocusStops = n; return this; }
        public Builder maxAiJudgements(int n) { this.maxAiJudgements = n; return this; }
        public Builder includeRules(Set<String> ids) { this.includeRules = ids; return this; }
        public Builder excludeRules(Set<String> ids) { this.excludeRules = ids; return this; }

        public A11yConfig build() {
            return new A11yConfig(this);
        }
    }
}

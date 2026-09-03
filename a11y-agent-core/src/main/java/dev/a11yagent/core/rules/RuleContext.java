package dev.a11yagent.core.rules;

import dev.a11yagent.core.ai.Judge;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.driver.PageDriver;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Everything a rule needs to evaluate one page state. One context is created per page state. */
public final class RuleContext {

    private final PageDriver driver;
    private final A11yConfig config;
    private final Judge judge;
    private final ArtifactStore artifacts;
    private final InPageEngine inPage;
    private final String stepName;
    private final Map<String, Object> cache = new HashMap<>();

    public RuleContext(PageDriver driver, A11yConfig config, Judge judge, ArtifactStore artifacts, String stepName) {
        this.driver = driver;
        this.config = config;
        this.judge = judge;
        this.artifacts = artifacts;
        this.inPage = new InPageEngine(driver);
        this.stepName = stepName;
    }

    public PageDriver driver() { return driver; }
    public A11yConfig config() { return config; }
    public Optional<Judge> judge() { return Optional.ofNullable(judge); }
    public ArtifactStore artifacts() { return artifacts; }
    public InPageEngine inPage() { return inPage; }
    public String stepName() { return stepName; }

    /** Memoises expensive shared computations (e.g. the keyboard traversal) across rules. */
    @SuppressWarnings("unchecked")
    public <T> T cached(String key, Supplier<T> supplier) {
        return (T) cache.computeIfAbsent(key, k -> supplier.get());
    }
}

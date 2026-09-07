package dev.a11yagent.core.rules;

import dev.a11yagent.core.ai.Judge;
import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.driver.PageDriver;
import dev.a11yagent.core.observe.Observation;
import dev.a11yagent.core.report.AuditRecorder;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final AuditRecorder recorder;
    private final Observation observation;
    private final Map<String, Object> cache = new HashMap<>();

    public RuleContext(PageDriver driver, A11yConfig config, Judge judge, ArtifactStore artifacts, String stepName) {
        this(driver, config, judge, artifacts, stepName, null, null);
    }

    public RuleContext(PageDriver driver, A11yConfig config, Judge judge, ArtifactStore artifacts, String stepName,
                       AuditRecorder recorder) {
        this(driver, config, judge, artifacts, stepName, recorder, null);
    }

    public RuleContext(PageDriver driver, A11yConfig config, Judge judge, ArtifactStore artifacts, String stepName,
                       AuditRecorder recorder, Observation observation) {
        this.driver = driver;
        this.config = config;
        this.judge = judge;
        this.artifacts = artifacts;
        this.inPage = new InPageEngine(driver);
        this.stepName = stepName;
        this.recorder = recorder;
        this.observation = observation;
    }

    public PageDriver driver() { return driver; }
    public A11yConfig config() { return config; }
    public Optional<Judge> judge() { return Optional.ofNullable(judge); }
    public ArtifactStore artifacts() { return artifacts; }
    public InPageEngine inPage() { return inPage; }
    public String stepName() { return stepName; }
    public Optional<AuditRecorder> recorder() { return Optional.ofNullable(recorder); }
    public Optional<Observation> observation() { return Optional.ofNullable(observation); }

    /** Options forwarded to in-page rules (scope, observation summary). */
    public Map<String, Object> ruleOptions() {
        Map<String, Object> opts = new LinkedHashMap<>();
        config.scopeSelector().ifPresent(s -> opts.put("scopeSelector", s));
        if (observation != null) {
            opts.put("observation", observation.toData());
        }
        return opts;
    }

    /** Browser accessibility tree for this page state, fetched once and shared by the AX rules. */
    public Optional<dev.a11yagent.core.ax.AxTree> axTree() {
        return cached("ax-tree", () -> driver.accessibilityTree());
    }

    /** Memoises expensive shared computations (e.g. the keyboard traversal) across rules. */
    @SuppressWarnings("unchecked")
    public <T> T cached(String key, Supplier<T> supplier) {
        return (T) cache.computeIfAbsent(key, k -> supplier.get());
    }
}

package dev.a11yagent.core.rules;

import dev.a11yagent.core.driver.PageDriver;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Injects the in-page rules bundle ({@code a11y-agent.js}) and exposes its functions. The bundle is
 * plain browser JavaScript with no dependencies so the identical file can be loaded by a browser
 * extension content script.
 */
public final class InPageEngine {

    private static final String RESOURCE = "/dev/a11yagent/inpage/a11y-agent.js";
    private static final String SCRIPT = load();

    private final PageDriver driver;

    public InPageEngine(PageDriver driver) {
        this.driver = driver;
    }

    private static String load() {
        try (InputStream in = InPageEngine.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String script() {
        return SCRIPT;
    }

    /** Installs the bundle if the page does not have it yet (navigations reset it). */
    public void ensureInstalled() {
        Object present = driver.evaluate("() => typeof window.__a11yAgent === 'object'");
        if (!Boolean.TRUE.equals(present)) {
            driver.evaluate("() => { " + SCRIPT + "\n }");
        }
    }

    /** Runs one in-page rule and returns raw finding maps. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runRule(String jsRuleId, Map<String, Object> options) {
        ensureInstalled();
        Object result = driver.evaluate(
                "(arg) => window.__a11yAgent.runRule(arg.id, arg.options)",
                Map.of("id", jsRuleId, "options", options == null ? Map.of() : options));
        return (List<Map<String, Object>>) result;
    }

    /** Structural snapshot used by cross-step rules. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> snapshot() {
        ensureInstalled();
        return (Map<String, Object>) driver.evaluate("() => window.__a11yAgent.snapshot()");
    }

    /** Calls an arbitrary helper exported by the bundle. */
    public Object call(String helper, Object arg) {
        ensureInstalled();
        return driver.evaluate("(arg) => window.__a11yAgent." + helper + "(arg)", arg);
    }
}

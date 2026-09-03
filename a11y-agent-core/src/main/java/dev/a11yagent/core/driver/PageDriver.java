package dev.a11yagent.core.driver;

import dev.a11yagent.core.ax.AxTree;
import java.util.Optional;

/**
 * Minimal browser abstraction the rules engine needs. Implemented for Playwright in
 * {@code a11y-agent-playwright}; a Selenium BiDi implementation can be added without touching core.
 *
 * <p>Design note: everything that can be answered from the DOM/CSSOM/AOM runs inside the page via
 * {@link #evaluate(String, Object)} so the same in-page script can also be shipped inside a browser
 * extension. Only things that require real user-agent behaviour (keyboard events, viewport changes,
 * screenshots) go through the driver.
 */
public interface PageDriver {

    String url();

    String title();

    /**
     * Evaluates a JavaScript function expression (e.g. {@code "(arg) => arg.x"}) in the page and returns
     * the JSON-serialisable result mapped to Java {@code Map}/{@code List}/{@code Number}/{@code String}/
     * {@code Boolean}.
     */
    Object evaluate(String functionExpression, Object arg);

    default Object evaluate(String functionExpression) {
        return evaluate(functionExpression, null);
    }

    /** Full page (or viewport) PNG screenshot. */
    byte[] screenshot(boolean fullPage);

    /** PNG screenshot of a viewport-relative clip. */
    byte[] screenshotClip(Rect clip);

    /** Sends a keyboard key (Playwright key syntax, e.g. "Tab", "Shift+Tab", "Escape"). */
    void press(String key);

    Viewport viewport();

    void setViewport(Viewport viewport);

    void navigate(String url);

    void waitMillis(long millis);

    /**
     * The browser's accessibility tree (what a screen reader receives), when the driver can obtain it
     * (Chromium via CDP {@code Accessibility.getFullAXTree}). Drivers without access return empty and the
     * AX-tree rules fall back to DOM heuristics or report CANT_TELL.
     */
    default Optional<AxTree> accessibilityTree() {
        return Optional.empty();
    }
}

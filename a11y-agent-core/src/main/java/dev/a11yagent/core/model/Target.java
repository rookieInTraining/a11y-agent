package dev.a11yagent.core.model;

import dev.a11yagent.core.driver.Rect;

/**
 * The element a finding refers to.
 *
 * @param selector CSS selector that uniquely identifies the element at audit time
 * @param html     outer HTML snippet (truncated)
 * @param rect     bounding box in CSS pixels relative to the viewport, may be null
 */
public record Target(String selector, String html, Rect rect) {

    public static Target page() {
        return new Target("html", "", null);
    }
}

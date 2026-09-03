package dev.a11yagent.core.driver;

import java.util.Map;

/** Bounding box in CSS pixels. */
public record Rect(double x, double y, double width, double height) {

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public double centerX() {
        return x + width / 2;
    }

    public double centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    /** Expands the box by {@code pad} pixels on every side, clamping at 0. */
    public Rect pad(double pad) {
        double nx = Math.max(0, x - pad);
        double ny = Math.max(0, y - pad);
        return new Rect(nx, ny, width + (x - nx) + pad, height + (y - ny) + pad);
    }

    @SuppressWarnings("unchecked")
    public static Rect from(Object o) {
        if (o == null) {
            return null;
        }
        Map<String, Object> m = (Map<String, Object>) o;
        return new Rect(num(m.get("x")), num(m.get("y")), num(m.get("width")), num(m.get("height")));
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}

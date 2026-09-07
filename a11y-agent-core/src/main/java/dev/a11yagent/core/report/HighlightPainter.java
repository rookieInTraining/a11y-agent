package dev.a11yagent.core.report;

import dev.a11yagent.core.driver.Rect;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Draws a viewport-relative highlight box onto a PNG when the in-page overlay is missing. */
public final class HighlightPainter {

    private static final Color STROKE = new Color(0xE1, 0x1D, 0x48);

    private HighlightPainter() {
    }

    public static byte[] paint(byte[] png, Rect cssRect, int viewportWidth, double dpr) {
        if (png == null || png.length == 0 || cssRect == null || cssRect.isEmpty() || viewportWidth <= 0) {
            return png;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                return png;
            }
            double scale = dpr > 0 ? dpr : (double) img.getWidth() / viewportWidth;
            int x = (int) Math.round(cssRect.x() * scale);
            int y = (int) Math.round(cssRect.y() * scale);
            int w = Math.max(2, (int) Math.round(cssRect.width() * scale));
            int h = Math.max(2, (int) Math.round(cssRect.height() * scale));
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(Math.max(3, (float) (3 * scale))));
            g.setColor(STROKE);
            g.drawRect(x, y, w, h);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return png;
        }
    }
}

package dev.a11yagent.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.a11yagent.core.driver.Rect;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HighlightPainterTest {

    @Test
    void paintsARoseBoxInCssPixelsScaledToThePng() throws IOException {
        BufferedImage src = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ImageIO.write(src, "png", raw);

        byte[] out = HighlightPainter.paint(raw.toByteArray(), new Rect(20, 10, 40, 20), 200, 1);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out));
        Color stroke = new Color(img.getRGB(20, 10));
        assertEquals(0xE1, stroke.getRed(), 30);
        assertTrue(stroke.getGreen() < 80, stroke.toString());
        Color inside = new Color(img.getRGB(40, 20));
        assertEquals(255, inside.getRed());
        assertEquals(255, inside.getGreen());
    }
}

package dev.a11yagent.core.report;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegTest {

    @Test
    void encodesAPngSequenceToWebmWhenFfmpegIsInstalled(@TempDir Path dir) throws Exception {
        assumeTrue(Ffmpeg.binary().isPresent(), "ffmpeg not on PATH / A11Y_FFMPEG / ms-playwright");
        Path frames = dir.resolve("frames");
        Files.createDirectories(frames);
        for (int i = 1; i <= 6; i++) {
            BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(20 * i, 40, 80));
            g.fillRect(0, 0, 64, 48);
            g.dispose();
            ImageIO.write(img, "png", frames.resolve(String.format("%06d.png", i)).toFile());
        }
        Path webm = dir.resolve("audit.webm");
        assertTrue(Ffmpeg.encodePngSequence(frames, webm, 6));
        assertTrue(Files.size(webm) > 0);
    }
}

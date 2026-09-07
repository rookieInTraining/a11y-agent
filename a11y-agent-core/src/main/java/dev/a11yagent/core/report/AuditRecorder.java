package dev.a11yagent.core.report;

import dev.a11yagent.core.driver.PageDriver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Captures viewport screenshots on the calling thread (Playwright/Selenium are not thread-safe) and
 * muxes them to {@code audit.webm} via ffmpeg when the audit finishes.
 */
public final class AuditRecorder {

    public static final String FILENAME = "audit.webm";

    private final PageDriver driver;
    private final Path frameDir;
    private final Path webm;
    private int frames;
    private int ticks;

    public AuditRecorder(PageDriver driver, Path artifactsDir) {
        this.driver = driver;
        this.frameDir = artifactsDir.resolve("video-frames");
        this.webm = artifactsDir.resolve(FILENAME);
    }

    public void capture() {
        ticks++;
        try {
            offer(driver.screenshot(false));
        } catch (RuntimeException ignored) {
            // recording is best-effort
        }
    }

    public void offer(byte[] png) {
        if (png == null || png.length == 0) {
            return;
        }
        try {
            Files.createDirectories(frameDir);
            frames++;
            Files.write(frameDir.resolve(String.format("%06d.png", frames)), png);
        } catch (IOException ignored) {
            // recording is best-effort
        }
    }

    /** Capture at a lower rate during keyboard traversal. */
    public void captureSparse() {
        if (ticks % 3 == 0) {
            capture();
        } else {
            ticks++;
        }
    }

    /** Evenly spaced PNG frames for a post-run vision review. Does not delete the sequence. */
    public List<byte[]> sample(int max) {
        if (frames == 0 || max <= 0) {
            return List.of();
        }
        int n = Math.min(max, frames);
        List<byte[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int idx = n == 1 ? 1 : 1 + (int) Math.round(i * (frames - 1) / (double) (n - 1));
            Path file = frameDir.resolve(String.format("%06d.png", idx));
            try {
                out.add(Files.readAllBytes(file));
            } catch (IOException ignored) {
                // skip unreadable frames
            }
        }
        return out;
    }

    public Optional<String> finish() {
        if (frames == 0) {
            return Optional.empty();
        }
        boolean ok = Ffmpeg.encodePngSequence(frameDir, webm, 6);
        deleteFrames();
        if (ok) {
            return Optional.of(FILENAME);
        }
        return Optional.empty();
    }

    private void deleteFrames() {
        try (Stream<Path> walk = Files.walk(frameDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // leftover frames are harmless
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
    }
}

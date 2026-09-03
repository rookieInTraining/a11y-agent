package dev.a11yagent.core.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Writes screenshots and other evidence files under the artifacts directory. */
public final class ArtifactStore {

    private final Path root;
    private final AtomicInteger counter = new AtomicInteger();

    public ArtifactStore(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    /** Saves a PNG and returns its path relative to the artifacts root. */
    public String savePng(String prefix, byte[] png) {
        if (png == null || png.length == 0) {
            return null;
        }
        try {
            Files.createDirectories(root.resolve("screenshots"));
            String name = "screenshots/" + sanitize(prefix) + "-" + counter.incrementAndGet() + ".png";
            Files.write(root.resolve(name), png);
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sanitize(String s) {
        String out = s.replaceAll("[^A-Za-z0-9._-]+", "-");
        return out.length() > 60 ? out.substring(0, 60) : out;
    }
}

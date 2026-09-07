package dev.a11yagent.core.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Locates an ffmpeg binary and encodes a numbered PNG sequence to VP8 WebM. */
public final class Ffmpeg {

    private Ffmpeg() {
    }

    public static Optional<Path> binary() {
        String env = System.getenv("A11Y_FFMPEG");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        for (String name : List.of("ffmpeg", "ffmpeg.exe")) {
            Path onPath = which(name);
            if (onPath != null) {
                return Optional.of(onPath);
            }
        }
        Path home = Path.of(System.getProperty("user.home"));
        for (Path root : List.of(
                home.resolve("AppData/Local/ms-playwright"),
                home.resolve(".cache/ms-playwright"),
                Path.of(System.getenv().getOrDefault("LOCALAPPDATA", ""), "ms-playwright"))) {
            Optional<Path> found = findUnder(root, 4);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public static boolean encodePngSequence(Path frameDir, Path webm, int fps) {
        Optional<Path> ff = binary();
        if (ff.isEmpty() || !Files.isDirectory(frameDir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(frameDir)) {
            long n = files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
            if (n == 0) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        try {
            Files.createDirectories(webm.getParent() == null ? Path.of(".") : webm.getParent());
            List<String> cmd = new ArrayList<>();
            cmd.add(ff.get().toString());
            cmd.add("-y");
            cmd.add("-framerate");
            cmd.add(String.valueOf(Math.max(1, fps)));
            cmd.add("-i");
            cmd.add(frameDir.resolve("%06d.png").toString());
            cmd.add("-vf");
            cmd.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
            cmd.add("-c:v");
            cmd.add("libvpx");
            cmd.add("-b:v");
            cmd.add("1M");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-auto-alt-ref");
            cmd.add("0");
            cmd.add(webm.toString());
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            boolean done = p.waitFor(120, TimeUnit.SECONDS);
            return done && p.exitValue() == 0 && Files.size(webm) > 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static Path which(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(path.contains(";") ? ";" : ":")) {
            Path cand = Path.of(dir, name);
            if (Files.isRegularFile(cand)) {
                return cand;
            }
        }
        return null;
    }

    private static Optional<Path> findUnder(Path root, int maxDepth) {
        if (root == null || root.toString().isBlank() || !Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root, maxDepth)) {
            return walk.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.equals("ffmpeg") || n.equals("ffmpeg.exe") || n.startsWith("ffmpeg-win");
            }).filter(Files::isRegularFile).min(Comparator.comparing(Path::toString));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}

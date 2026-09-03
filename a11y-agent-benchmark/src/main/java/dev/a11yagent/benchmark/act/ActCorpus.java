package dev.a11yagent.benchmark.act;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the W3C ACT Rules test case corpus into a local cache and serves it over HTTP with the
 * original {@code /WAI/content-assets/wcag-act-rules/...} paths preserved, so test cases that reference
 * shared assets (images, scripts, iframe documents) resolve exactly as they do on w3.org.
 */
public final class ActCorpus implements AutoCloseable {

    public static final String TESTCASES_URL = "https://www.w3.org/WAI/content-assets/wcag-act-rules/testcases.json";
    private static final String W3C = "https://www.w3.org";
    private static final String PREFIX = "/WAI/content-assets/wcag-act-rules/";
    private static final Pattern REF = Pattern.compile("(?:src|href|data)=\"(/WAI/[^\"]+)\"");

    private final Path root;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private HttpServer server;
    private String baseUrl;

    public ActCorpus(Path cacheDir) {
        this.root = cacheDir;
    }

    /** Downloads {@code testcases.json} and every case page plus referenced assets (idempotent). */
    public List<ActTestCase> fetch(boolean refresh) {
        try {
            Files.createDirectories(root);
            Path metaFile = root.resolve("testcases.json");
            if (refresh || !Files.exists(metaFile)) {
                Files.writeString(metaFile, get(TESTCASES_URL));
            }
            JsonNode meta = json.readTree(Files.readString(metaFile));
            List<ActTestCase> cases = new ArrayList<>();
            for (JsonNode n : meta.path("testcases")) {
                String url = n.path("url").asText();
                Set<String> requirements = new LinkedHashSet<>();
                n.path("ruleAccessibilityRequirements").fieldNames().forEachRemaining(requirements::add);
                cases.add(new ActTestCase(
                        n.path("ruleId").asText(),
                        n.path("ruleName").asText(),
                        n.path("testcaseId").asText(),
                        url,
                        localPath(url),
                        ActTestCase.Expected.parse(n.path("expected").asText()),
                        requirements));
            }
            List<String> pending = new ArrayList<>();
            for (ActTestCase c : cases) {
                pending.add(PREFIX + c.relativePath());
            }
            download(pending, refresh, 3);
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Downloads paths under {@code /WAI/...}, following references found inside HTML for {@code depth} levels. */
    private void download(List<String> paths, boolean refresh, int depth) throws IOException {
        Set<String> seen = new LinkedHashSet<>(paths);
        List<String> level = new ArrayList<>(paths);
        for (int d = 0; d < depth && !level.isEmpty(); d++) {
            List<String> next = new ArrayList<>();
            var pool = Executors.newFixedThreadPool(12);
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (String p : level) {
                futures.add(pool.submit(() -> fetchOne(p, refresh)));
            }
            for (java.util.concurrent.Future<String> f : futures) {
                try {
                    String body = f.get();
                    if (body == null) {
                        continue;
                    }
                    Matcher m = REF.matcher(body);
                    while (m.find()) {
                        String ref = m.group(1);
                        if (ref.startsWith(PREFIX) && seen.add(ref)) {
                            next.add(ref);
                        }
                    }
                } catch (Exception ignored) {
                    // a missing asset is part of some test cases (e.g. does-not-exist.png)
                }
            }
            pool.shutdown();
            level = next;
        }
    }

    /** Returns the body when the resource is HTML (so references can be followed), null otherwise. */
    private String fetchOne(String waiPath, boolean refresh) {
        Path target = root.resolve(waiPath.substring(PREFIX.length()));
        try {
            if (!refresh && Files.exists(target)) {
                return isHtml(target) ? Files.readString(target) : null;
            }
            Files.createDirectories(target.getParent());
            HttpResponse<byte[]> resp = http.send(
                    HttpRequest.newBuilder(URI.create(W3C + waiPath)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                return null;
            }
            Files.write(target, resp.body());
            return isHtml(target) ? new String(resp.body(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isHtml(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".html") || n.endsWith(".htm");
    }

    private static String localPath(String url) {
        return URI.create(url).getPath().substring(PREFIX.length() + 1);
    }

    private String get(String url) throws IOException {
        try {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("GET " + url + " -> " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /** Starts the local server; returns the base URL. Case URLs are {@code baseUrl + PREFIX + relativePath}. */
    public String serve() {
        if (baseUrl != null) {
            return baseUrl;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 64);
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] body;
                int status = 200;
                Path file = path.startsWith(PREFIX) ? root.resolve(path.substring(PREFIX.length())) : null;
                if (file != null && Files.isDirectory(file)) {
                    file = file.resolve("index.html");
                }
                if (file != null && Files.isRegularFile(file) && file.normalize().startsWith(root.normalize())) {
                    body = Files.readAllBytes(file);
                    exchange.getResponseHeaders().add("Content-Type", contentType(file));
                } else {
                    status = 404;
                    body = "<!doctype html><html lang=\"en\"><head><title>Not found</title></head><body><p>404</p></body></html>".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                }
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            return baseUrl;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String urlFor(ActTestCase c) {
        return serve() + PREFIX + c.relativePath();
    }

    private static String contentType(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (n.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (n.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (n.endsWith(".json")) {
            return "application/json";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (n.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (n.endsWith(".webm")) {
            return "video/webm";
        }
        if (n.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (n.endsWith(".vtt")) {
            return "text/vtt";
        }
        return "application/octet-stream";
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }
}

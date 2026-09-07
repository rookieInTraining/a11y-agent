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
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
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
            List<String> missing = new ArrayList<>();
            for (String p : pending) {
                if (!Files.isRegularFile(toLocal(p))) {
                    missing.add(p);
                }
            }
            if (!missing.isEmpty()) {
                System.out.printf("Retrying %d missing test case pages sequentially...%n", missing.size());
                System.out.flush();
                for (String p : missing) {
                    fetchOne(p, true);
                }
            }
            long have = pending.stream().filter(p -> Files.isRegularFile(toLocal(p))).count();
            System.out.printf("Corpus cache: %d/%d test case pages on disk.%n", have, pending.size());
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
            var pool = Executors.newFixedThreadPool(4);
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
        Path target = toLocal(waiPath);
        try {
            if (!refresh && Files.isRegularFile(target)) {
                return isHtml(target) ? Files.readString(target) : null;
            }
            byte[] body = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    HttpResponse<byte[]> resp = http.send(
                            HttpRequest.newBuilder(URI.create(W3C + waiPath))
                                    .timeout(Duration.ofSeconds(15))
                                    .header("User-Agent", "a11y-agent-benchmark/0.1 (ACT corpus mirror)")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    int code = resp.statusCode();
                    if (code / 100 == 2) {
                        body = resp.body();
                        break;
                    }
                    if (code == 404) {
                        return null; // some cases intentionally reference missing assets
                    }
                } catch (Exception ignored) {
                    // retry below
                }
                Thread.sleep(150L * attempt);
            }
            if (body == null) {
                return null;
            }
            Files.createDirectories(target.getParent());
            Files.write(target, body);
            return isHtml(target) ? new String(body, StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Path toLocal(String waiPath) {
        String rel = waiPath.startsWith(PREFIX) ? waiPath.substring(PREFIX.length()) : waiPath;
        Path p = root;
        for (String part : rel.split("/")) {
            if (!part.isEmpty()) {
                p = p.resolve(part);
            }
        }
        return p;
    }

    private static boolean isHtml(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".html") || n.endsWith(".htm");
    }

    private static String localPath(String url) {
        String path = URI.create(url).getPath();
        return path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path.replaceFirst("^.*/", "");
    }

    /** Base URL of the local server, or null until {@link #serve()} has been called. */
    public String baseUrl() {
        return baseUrl;
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
                Path file = path.startsWith(PREFIX) ? toLocal(path) : null;
                if (file != null && Files.isDirectory(file)) {
                    file = file.resolve("index.html");
                }
                if (file != null && Files.isRegularFile(file) && file.normalize().startsWith(root.normalize())) {
                    body = Files.readAllBytes(file);
                    String type = contentType(file);
                    if (type.startsWith("text/html")) {
                        body = stampMetaRefresh(body);
                    }
                    exchange.getResponseHeaders().add("Content-Type", type);
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

    /**
     * Chromium treats {@code <meta http-equiv=refresh>} as a parser pragma: it may navigate away
     * (aborted by the corpus network filter) or drop the tag from the live DOM before the audit runs.
     * Copy the declared content onto {@code <html>} and rename the pragma so the in-page rule can still
     * see it without the browser consuming it.
     */
    static byte[] stampMetaRefresh(byte[] body) {
        String html = new String(body, StandardCharsets.UTF_8);
        Matcher tags = Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);
        StringBuilder contents = new StringBuilder();
        while (tags.find()) {
            String tag = tags.group();
            if (!Pattern.compile("(?is)http-equiv\\s*=\\s*['\"]?refresh['\"]?").matcher(tag).find()) {
                continue;
            }
            Matcher cm = Pattern.compile("(?is)\\bcontent\\s*=\\s*['\"]([^'\"]*)['\"]").matcher(tag);
            String content = cm.find() ? cm.group(1) : null;
            if (content == null) {
                cm = Pattern.compile("(?is)\\bcontent\\s*=\\s*([^\\s>]+)").matcher(tag);
                if (cm.find()) {
                    content = cm.group(1);
                }
            }
            if (content != null) {
                if (contents.length() > 0) {
                    contents.append('\n');
                }
                contents.append(content);
            }
        }
        if (contents.length() == 0) {
            return body;
        }
        String attr = contents.toString()
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("\n", "&#10;");
        Matcher htmlTag = Pattern.compile("(?is)<html\\b([^>]*)>").matcher(html);
        String stamped;
        if (htmlTag.find()) {
            stamped = html.substring(0, htmlTag.start())
                    + "<html data-a11y-meta-refresh=\"" + attr + "\"" + htmlTag.group(1) + ">"
                    + html.substring(htmlTag.end());
        } else {
            stamped = "<html data-a11y-meta-refresh=\"" + attr + "\">" + html;
        }
        stamped = Pattern.compile("(?i)(http-equiv\\s*=\\s*)(['\"]?)refresh\\2")
                .matcher(stamped)
                .replaceAll("$1$2x-a11y-refresh$2");
        String[] parts = contents.toString().split("\n", -1);
        StringBuilder payload = new StringBuilder("[");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                payload.append(',');
            }
            payload.append('"')
                    .append(parts[i].replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"');
        }
        payload.append(']');
        String injection = "<script type=\"application/json\" id=\"a11y-agent-meta-refresh\">" + payload + "</script>";
        Matcher head = Pattern.compile("(?is)<head\\b[^>]*>").matcher(stamped);
        if (head.find()) {
            stamped = stamped.substring(0, head.end()) + injection + stamped.substring(head.end());
        } else {
            stamped = injection + stamped;
        }
        return stamped.getBytes(StandardCharsets.UTF_8);
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

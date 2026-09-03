package dev.a11yagent.cli;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Serves a directory over HTTP so a mirrored corpus can be evaluated with the same URL paths as the
 * original site (root-absolute asset references keep working without rewriting any HTML).
 */
final class StaticFileServer implements AutoCloseable {

    private final HttpServer server;
    private final Path root;

    StaticFileServer(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", exchange -> {
            Path file = resolve(exchange.getRequestURI().getPath());
            try {
                if (file == null || !Files.isRegularFile(file)) {
                    byte[] body = "not found".getBytes();
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }
                byte[] body = Files.readAllBytes(file);
                exchange.getResponseHeaders().add("Content-Type", contentType(file));
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private Path resolve(String urlPath) {
        Path p = root.resolve(urlPath.startsWith("/") ? urlPath.substring(1) : urlPath).normalize();
        if (!p.startsWith(root)) {
            return null;
        }
        return Files.isDirectory(p) ? p.resolve("index.html") : p;
    }

    private static String contentType(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".vtt")) return "text/vtt";
        return "application/octet-stream";
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

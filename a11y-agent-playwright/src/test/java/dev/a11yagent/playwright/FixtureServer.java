package dev.a11yagent.playwright;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/** Serves {@code src/test/resources/fixtures} over HTTP so journeys use real navigations. */
final class FixtureServer implements AutoCloseable {

    private final HttpServer server;

    FixtureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/good.html";
            }
            try (InputStream in = FixtureServer.class.getResourceAsStream("/fixtures" + path)) {
                if (in == null) {
                    byte[] body = "<!doctype html><html lang=\"en\"><title>404</title><body><h1>Not found</h1></body></html>".getBytes();
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(404, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                    return;
                }
                byte[] body = in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

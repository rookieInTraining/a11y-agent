package dev.a11yagent.benchmark.act;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ActCorpusStampTest {

    @Test
    void stampsDelayAndDisablesThePragma() {
        byte[] out = ActCorpus.stampMetaRefresh("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta http-equiv="refresh" content="30" />
                </head>
                <body><p>hi</p></body>
                </html>
                """.getBytes(StandardCharsets.UTF_8));
        String html = new String(out, StandardCharsets.UTF_8);
        assertTrue(html.contains("data-a11y-meta-refresh=\"30\""));
        assertTrue(html.contains("id=\"a11y-agent-meta-refresh\""));
        assertTrue(html.contains("[\"30\"]"));
        assertTrue(html.contains("http-equiv=\"x-a11y-refresh\""));
        assertFalse(html.contains("http-equiv=\"refresh\""));
    }

    @Test
    void keepsLaterValidRefreshWhenTheFirstPragmaIsInvalid() {
        byte[] out = ActCorpus.stampMetaRefresh("""
                <html lang="en">
                <head>
                <meta http-equiv="refresh" content="0: https://w3.org" />
                <meta http-equiv="refresh" content="5; https://w3.org" />
                </head>
                <body></body>
                </html>
                """.getBytes(StandardCharsets.UTF_8));
        String html = new String(out, StandardCharsets.UTF_8);
        assertTrue(html.contains("data-a11y-meta-refresh=\"0: https://w3.org&#10;5; https://w3.org\"")
                || html.contains("0: https://w3.org"));
        assertFalse(html.contains("http-equiv=\"refresh\""));
    }
}

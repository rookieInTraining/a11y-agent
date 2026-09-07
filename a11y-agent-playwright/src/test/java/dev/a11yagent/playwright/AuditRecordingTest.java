package dev.a11yagent.playwright;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.a11yagent.core.config.A11yConfig;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.core.report.Ffmpeg;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditRecordingTest extends BrowserTestBase {

    @Test
    void checkWritesAWebmWhenFfmpegIsAvailable() throws Exception {
        assumeTrue(Ffmpeg.binary().isPresent(), "ffmpeg not on PATH / A11Y_FFMPEG / ms-playwright");
        Path dir = Files.createTempDirectory("a11y-agent-video");
        A11yConfig config = A11yConfig.builder().artifactsDir(dir).recordVideo(true).screenshots(true).build();
        page.navigate(server.url("/dom-bad.html"));
        AuditReport report = A11yAgent.forPage(page, config).check("image-alt");
        assertTrue(report.hasVideo(), "expected audit.webm on the report");
        Path webm = dir.resolve("audit.webm");
        assertTrue(Files.isRegularFile(webm), webm.toString());
        assertTrue(Files.size(webm) > 0);
        String html = dev.a11yagent.core.report.HtmlReportWriter.render(report);
        assertTrue(html.contains("type=\"video/webm\""), html);
    }
}

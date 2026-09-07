package dev.a11yagent.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditRecorderTest {

    @Test
    void sampleReturnsEvenlySpacedFrames(@TempDir Path dir) {
        AuditRecorder r = new AuditRecorder(null, dir);
        for (int i = 1; i <= 5; i++) {
            r.offer(new byte[] {(byte) i, 0, 1});
        }
        List<byte[]> three = r.sample(3);
        assertEquals(3, three.size());
        assertEquals(1, three.get(0)[0]);
        assertEquals(3, three.get(1)[0]);
        assertEquals(5, three.get(2)[0]);
        assertEquals(1, r.sample(1).size());
        assertEquals(5, r.sample(10).size());
    }
}

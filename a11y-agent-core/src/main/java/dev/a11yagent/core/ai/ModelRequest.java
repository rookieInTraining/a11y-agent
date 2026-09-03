package dev.a11yagent.core.ai;

import java.util.List;

/**
 * Provider-agnostic multimodal prompt.
 *
 * @param system system instructions
 * @param user   user text
 * @param images PNG images (raw bytes) attached to the user turn
 */
public record ModelRequest(String system, String user, List<byte[]> images) {

    public ModelRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static ModelRequest text(String system, String user) {
        return new ModelRequest(system, user, List.of());
    }
}

package dev.a11yagent.playwright;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.model.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads Chromium's full accessibility tree through the DevTools protocol and resolves nodes back to DOM
 * elements with {@code DOM.resolveNode} + {@code Runtime.callFunctionOn}.
 */
final class CdpAccessibilityTree {

    private static final String DESCRIBE_FN = """
            function () {
              var el = this;
              if (!el || el.nodeType !== 1) { el = el && el.parentElement; }
              if (!el) return null;
              function esc(s) { return (window.CSS && CSS.escape) ? CSS.escape(s) : s; }
              function path(node) {
                if (node === document.documentElement) return 'html';
                if (node === document.body) return 'body';
                if (node.id && document.querySelectorAll('#' + esc(node.id)).length === 1) return '#' + esc(node.id);
                var parts = [];
                while (node && node.nodeType === 1 && node !== document.body && node !== document.documentElement) {
                  var parent = node.parentElement; if (!parent) break;
                  var same = Array.prototype.filter.call(parent.children, function (c) { return c.nodeName === node.nodeName; });
                  var part = node.nodeName.toLowerCase();
                  if (same.length > 1) part += ':nth-of-type(' + (same.indexOf(node) + 1) + ')';
                  parts.unshift(part);
                  if (parent.id && document.querySelectorAll('#' + esc(parent.id)).length === 1) { parts.unshift('#' + esc(parent.id)); return parts.join(' > '); }
                  node = parent;
                }
                parts.unshift('body');
                return parts.join(' > ');
              }
              var r = el.getBoundingClientRect();
              var html = el.outerHTML || '';
              return { selector: path(el), html: html.length > 300 ? html.slice(0, 300) + '…' : html, rect: { x: r.left, y: r.top, width: r.width, height: r.height } };
            }
            """;

    private CdpAccessibilityTree() {
    }

    static Optional<AxTree> fetch(Page page) {
        CDPSession cdp;
        try {
            cdp = page.context().newCDPSession(page);
        } catch (RuntimeException e) {
            return Optional.empty(); // not Chromium
        }
        try {
            cdp.send("Accessibility.enable");
            JsonObject res = cdp.send("Accessibility.getFullAXTree");
            List<AxNode> nodes = new ArrayList<>();
            for (JsonElement e : res.getAsJsonArray("nodes")) {
                nodes.add(toNode(e.getAsJsonObject()));
            }
            // Session stays open for lazy node resolution; closed with the page.
            return Optional.of(new AxTree(nodes, n -> resolve(cdp, n)));
        } catch (RuntimeException e) {
            try {
                cdp.detach();
            } catch (RuntimeException ignored) {
                // best effort
            }
            return Optional.empty();
        }
    }

    private static AxNode toNode(JsonObject o) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (o.has("properties")) {
            for (JsonElement p : o.getAsJsonArray("properties")) {
                JsonObject po = p.getAsJsonObject();
                props.put(po.get("name").getAsString(), value(po.get("value")));
            }
        }
        List<String> children = new ArrayList<>();
        if (o.has("childIds")) {
            for (JsonElement c : o.getAsJsonArray("childIds")) {
                children.add(c.getAsString());
            }
        }
        List<String> reasons = new ArrayList<>();
        if (o.has("ignoredReasons")) {
            for (JsonElement r : o.getAsJsonArray("ignoredReasons")) {
                reasons.add(r.getAsJsonObject().get("name").getAsString());
            }
        }
        return new AxNode(
                o.get("nodeId").getAsString(),
                o.has("parentId") ? o.get("parentId").getAsString() : null,
                str(o.get("role")),
                str(o.get("name")),
                str(o.get("description")),
                str(o.get("value")),
                o.has("ignored") && o.get("ignored").getAsBoolean(),
                reasons,
                props,
                children,
                o.has("backendDOMNodeId") ? o.get("backendDOMNodeId").getAsLong() : null);
    }

    private static String str(JsonElement axValue) {
        if (axValue == null || axValue.isJsonNull()) {
            return null;
        }
        JsonObject v = axValue.getAsJsonObject();
        JsonElement val = v.get("value");
        return val == null || val.isJsonNull() ? null : val.isJsonPrimitive() ? val.getAsString() : val.toString();
    }

    private static Object value(JsonElement axValue) {
        if (axValue == null || axValue.isJsonNull()) {
            return null;
        }
        JsonObject v = axValue.getAsJsonObject();
        JsonElement val = v.get("value");
        if (val == null || val.isJsonNull()) {
            return null;
        }
        if (val.isJsonPrimitive()) {
            var p = val.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber()) {
                return p.getAsNumber();
            }
            return p.getAsString();
        }
        if (val.isJsonArray()) {
            List<String> ids = new ArrayList<>();
            for (JsonElement e : val.getAsJsonArray()) {
                if (e.isJsonObject() && e.getAsJsonObject().has("idref")) {
                    ids.add(e.getAsJsonObject().get("idref").getAsString());
                } else {
                    ids.add(e.toString());
                }
            }
            return ids;
        }
        return val.toString();
    }

    private static Target resolve(CDPSession cdp, AxNode n) {
        if (n.backendNodeId() == null) {
            return null;
        }
        JsonObject args = new JsonObject();
        args.addProperty("backendNodeId", n.backendNodeId());
        JsonObject resolved = cdp.send("DOM.resolveNode", args);
        String objectId = resolved.getAsJsonObject("object").get("objectId").getAsString();
        JsonObject call = new JsonObject();
        call.addProperty("objectId", objectId);
        call.addProperty("functionDeclaration", DESCRIBE_FN);
        call.addProperty("returnByValue", true);
        JsonObject result = cdp.send("Runtime.callFunctionOn", call);
        JsonElement value = result.getAsJsonObject("result").get("value");
        if (value == null || value.isJsonNull() || !value.isJsonObject()) {
            return null;
        }
        JsonObject v = value.getAsJsonObject();
        JsonObject r = v.getAsJsonObject("rect");
        Rect rect = r == null ? null : new Rect(r.get("x").getAsDouble(), r.get("y").getAsDouble(), r.get("width").getAsDouble(), r.get("height").getAsDouble());
        return new Target(v.get("selector").getAsString(), v.get("html").getAsString(), rect);
    }

    /** Compact textual rendering of the exposed tree, the "screen reader view" used by the CLI. */
    static String render(AxTree tree, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        tree.nodes().stream().filter(n -> n.parentId() == null).findFirst().ifPresent(root -> render(tree, root, 0, maxDepth, sb));
        return sb.toString();
    }

    private static void render(AxTree tree, AxNode n, int depth, int maxDepth, StringBuilder sb) {
        if (depth > maxDepth) {
            return;
        }
        if (!n.ignored()) {
            sb.append("  ".repeat(depth)).append(n.role());
            if (n.hasName()) {
                sb.append(" \"").append(n.name().length() > 80 ? n.name().substring(0, 80) + "…" : n.name()).append('"');
            }
            List<String> flags = new ArrayList<>();
            for (String k : List.of("focusable", "expanded", "pressed", "checked", "selected", "required", "invalid", "level", "live", "disabled", "haspopup")) {
                Object v = n.property(k);
                if (v != null && !"false".equals(String.valueOf(v)) && !"off".equals(String.valueOf(v))) {
                    flags.add(k + (Boolean.TRUE.equals(v) ? "" : "=" + v));
                }
            }
            if (!flags.isEmpty()) {
                sb.append(" [").append(String.join(", ", flags)).append(']');
            }
            if (n.description() != null && !n.description().isBlank()) {
                sb.append(" — ").append(n.description().length() > 60 ? n.description().substring(0, 60) + "…" : n.description());
            }
            sb.append('\n');
        }
        int next = n.ignored() ? depth : depth + 1;
        for (AxNode c : tree.children(n)) {
            render(tree, c, next, maxDepth, sb);
        }
    }
}

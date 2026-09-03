package dev.a11yagent.core.ax;

import dev.a11yagent.core.model.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The browser accessibility tree plus a resolver that maps a node back to its DOM element (selector,
 * HTML, bounding box) so findings can carry evidence. The resolver is supplied by the driver because it
 * needs a DevTools session.
 */
public final class AxTree {

    private final List<AxNode> nodes;
    private final Map<String, AxNode> byId = new LinkedHashMap<>();
    private final Function<AxNode, Target> resolver;
    private final Map<String, Target> resolved = new LinkedHashMap<>();

    public AxTree(List<AxNode> nodes, Function<AxNode, Target> resolver) {
        this.nodes = List.copyOf(nodes);
        this.resolver = resolver;
        for (AxNode n : this.nodes) {
            byId.put(n.id(), n);
        }
    }

    public List<AxNode> nodes() {
        return nodes;
    }

    public Stream<AxNode> stream() {
        return nodes.stream();
    }

    /** Nodes that assistive technology actually receives. */
    public Stream<AxNode> exposed() {
        return nodes.stream().filter(n -> !n.ignored());
    }

    public Optional<AxNode> node(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<AxNode> parent(AxNode n) {
        return n.parentId() == null ? Optional.empty() : node(n.parentId());
    }

    public List<AxNode> children(AxNode n) {
        List<AxNode> out = new ArrayList<>();
        for (String id : n.childIds()) {
            AxNode c = byId.get(id);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    public List<AxNode> ancestors(AxNode n) {
        List<AxNode> out = new ArrayList<>();
        Optional<AxNode> p = parent(n);
        int guard = 0;
        while (p.isPresent() && guard++ < 500) {
            out.add(p.get());
            p = parent(p.get());
        }
        return out;
    }

    /** Text descendants (StaticText nodes) joined, used as a fallback description of a node. */
    public String textOf(AxNode n) {
        StringBuilder sb = new StringBuilder();
        collectText(n, sb, 0);
        return sb.toString().trim();
    }

    private void collectText(AxNode n, StringBuilder sb, int depth) {
        if (depth > 30) {
            return;
        }
        if ("StaticText".equals(n.role()) && n.name() != null) {
            sb.append(n.name()).append(' ');
        }
        for (AxNode c : children(n)) {
            collectText(c, sb, depth + 1);
        }
    }

    /** Resolves the node to its DOM element; cached per node. */
    public Target target(AxNode n) {
        return resolved.computeIfAbsent(n.id(), k -> {
            try {
                Target t = resolver.apply(n);
                return t == null ? new Target("(ax:" + n.role() + ")", "", null) : t;
            } catch (RuntimeException e) {
                return new Target("(ax:" + n.role() + ")", "", null);
            }
        });
    }

    public long countRole(String role) {
        return exposed().filter(n -> role.equals(n.role())).count();
    }
}

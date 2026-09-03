package dev.a11yagent.core.report;

/** Tiny HTML escaping helper for the report and VPAT writers. */
public final class Html {

    private Html() {
    }

    public static String esc(Object o) {
        if (o == null) {
            return "";
        }
        String s = String.valueOf(o);
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Shared stylesheet: high contrast, visible focus, respects reduced motion. Our own reports must pass. */
    public static final String CSS = """
            :root { color-scheme: light dark; --fg: #1a1a1a; --bg: #ffffff; --muted: #4a4a4a; --line: #c9c9c9; --fail: #a4161a; --warn: #8a5a00; --pass: #1b6e3a; --info: #2f4f8f; }
            @media (prefers-color-scheme: dark) { :root { --fg: #f2f2f2; --bg: #121212; --muted: #c0c0c0; --line: #444; --fail: #ff8a8e; --warn: #ffcc66; --pass: #7bd88f; --info: #9ab8ff; } }
            body { font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: var(--fg); background: var(--bg); margin: 0; padding: 1.5rem; max-width: 72rem; margin-inline: auto; }
            h1, h2, h3 { line-height: 1.25; }
            table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
            th, td { border: 1px solid var(--line); padding: .5rem .6rem; text-align: left; vertical-align: top; }
            th { background: rgba(127,127,127,.12); }
            caption { text-align: left; font-weight: 600; padding: .5rem 0; }
            code, pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: .9em; }
            pre { white-space: pre-wrap; word-break: break-word; background: rgba(127,127,127,.1); padding: .5rem; border-radius: 4px; }
            .badge { display: inline-block; padding: .1rem .5rem; border-radius: 999px; font-weight: 600; font-size: .85em; border: 1px solid currentColor; }
            .FAILED { color: var(--fail); } .NEEDS_REVIEW, .CANT_TELL { color: var(--warn); } .PASSED { color: var(--pass); } .INAPPLICABLE { color: var(--muted); }
            .Supports { color: var(--pass); } .Partially, .PartiallySupports { color: var(--warn); } .DoesNot, .DoesNotSupport { color: var(--fail); } .NotApplicable, .NotEvaluated { color: var(--muted); }
            a:focus-visible, button:focus-visible, summary:focus-visible, [tabindex]:focus-visible { outline: 3px solid var(--info); outline-offset: 2px; }
            details { border: 1px solid var(--line); border-radius: 6px; padding: .5rem .8rem; margin: .6rem 0; }
            summary { cursor: pointer; font-weight: 600; }
            img.shot { max-width: 100%; height: auto; border: 1px solid var(--line); }
            .muted { color: var(--muted); }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: .8rem; }
            .card { border: 1px solid var(--line); border-radius: 6px; padding: .8rem; }
            .card strong { font-size: 1.6rem; display: block; }
            @media (prefers-reduced-motion: reduce) { * { animation: none !important; transition: none !important; } }
            """;
}

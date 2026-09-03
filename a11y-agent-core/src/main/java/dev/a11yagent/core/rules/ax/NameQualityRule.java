package dev.a11yagent.core.rules.ax;

import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.model.Finding;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.model.Outcome;
import dev.a11yagent.core.model.Target;
import dev.a11yagent.core.rules.Findings;
import dev.a11yagent.core.rules.RuleContext;
import dev.a11yagent.core.wcag.Wcag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Screen-reader experience of accessible names, judged on the browser-computed name (so aria-label,
 * aria-labelledby, alt, title and content are all accounted for exactly as AT receives them):
 * <ul>
 *   <li>names that embed interaction instructions ("click here to…", "press enter to access") — announced
 *       verbatim, wrong for touch/switch/voice users;</li>
 *   <li>names that repeat the role ("Submit button", "Home link", "image of…") — double announcement;</li>
 *   <li>names that are only a role word, a URL or a file name;</li>
 *   <li>overlong names on controls;</li>
 *   <li>descriptions identical to the name (announced twice).</li>
 * </ul>
 */
public final class NameQualityRule extends AxRule {

    private static final Pattern INSTRUCTIONS = Pattern.compile("\\b(click|clicking|double[- ]?click|right[- ]?click|press (enter|return|space)|hit enter|tap here|tap to|actionable|mouse ?over|hover)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_LIKE = Pattern.compile("^(https?://|www\\.)\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_LIKE = Pattern.compile("^[\\w-]+\\.(png|jpe?g|gif|svg|webp|pdf|docx?)$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Pattern> ROLE_WORDS = Map.of(
            "button", Pattern.compile("\\bbutton\\b$", Pattern.CASE_INSENSITIVE),
            "link", Pattern.compile("\\blink\\b$", Pattern.CASE_INSENSITIVE),
            "image", Pattern.compile("^(image|picture|photo|graphic|icon)( of)?\\b|\\b(image|icon|logo image)$", Pattern.CASE_INSENSITIVE),
            "heading", Pattern.compile("^heading\\b|\\bheading$", Pattern.CASE_INSENSITIVE),
            "checkbox", Pattern.compile("\\bcheckbox$", Pattern.CASE_INSENSITIVE),
            "tab", Pattern.compile("\\btab$", Pattern.CASE_INSENSITIVE),
            "menuitem", Pattern.compile("\\bmenu item$", Pattern.CASE_INSENSITIVE));
    private static final Set<String> ONLY_ROLE_WORD = Set.of("button", "link", "image", "icon", "img", "picture", "logo", "menu", "tab", "checkbox", "toggle", "input", "field", "text", "item", "element", "div", "span");
    private static final Set<String> CONTROL_ROLES = Set.of("button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "menuitemcheckbox", "menuitemradio", "textbox", "searchbox", "combobox", "slider", "spinbutton", "option", "treeitem", "image", "heading");

    public NameQualityRule() {
        super("ax-name-quality",
                "Accessible names as computed by the browser are announced cleanly: no embedded mouse instructions, no repeated role words, not a URL/file name, not overlong, description not equal to name.",
                Set.of(Wcag.get("4.1.2"), Wcag.get("2.4.6")), Impact.MODERATE);
    }

    @Override
    protected List<Finding> evaluate(RuleContext ctx, AxTree tree) {
        String url = ctx.driver().url();
        List<Finding> out = new ArrayList<>();
        int checked = 0;
        for (AxNode n : tree.nodes()) {
            if (n.ignored() || !CONTROL_ROLES.contains(n.role()) || !n.hasName()) {
                continue;
            }
            checked++;
            String name = n.name().trim();
            String lower = name.toLowerCase(Locale.ROOT);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("role", n.role());
            data.put("name", abbreviate(name, 160));
            if (n.description() != null) {
                data.put("description", abbreviate(n.description(), 160));
            }
            if (ONLY_ROLE_WORD.contains(lower)) {
                out.add(nodeFinding(Outcome.FAILED, tree, n, "The " + n.role() + " is announced only as \"" + name + "\", which repeats its role and conveys no purpose.", data, url));
                continue;
            }
            if (URL_LIKE.matcher(name).matches()) {
                out.add(nodeFinding(Outcome.NEEDS_REVIEW, tree, n, "The " + n.role() + " is announced as a raw URL \"" + abbreviate(name, 80) + "\"; screen readers spell it out character by character.", data, url));
                continue;
            }
            if (FILE_LIKE.matcher(name).matches()) {
                out.add(nodeFinding(Outcome.FAILED, tree, n, "The " + n.role() + " is announced as a file name \"" + name + "\".", data, url));
                continue;
            }
            java.util.regex.Matcher instr = INSTRUCTIONS.matcher(name);
            if (instr.find()) {
                out.add(nodeFinding(Outcome.NEEDS_REVIEW, tree, n, "Accessible name contains pointer/keyboard instructions (\"" + instr.group() + "\") that are read out to every user: \"" + abbreviate(name, 100) + "\". Keep the name to the purpose; put usage hints in aria-description if needed.", data, url));
                continue;
            }
            Pattern roleWord = ROLE_WORDS.get(n.role());
            if (roleWord != null && roleWord.matcher(name).find() && !ONLY_ROLE_WORD.contains(lower)) {
                out.add(nodeFinding(Outcome.NEEDS_REVIEW, tree, n, "Name \"" + abbreviate(name, 80) + "\" repeats the role; a screen reader announces \"" + abbreviate(name, 40) + ", " + n.role() + "\".", data, url));
                continue;
            }
            if (!"heading".equals(n.role()) && name.length() > 120) {
                out.add(nodeFinding(Outcome.NEEDS_REVIEW, tree, n, "Accessible name is " + name.length() + " characters; controls should have concise names (move detail to aria-description or visible text).", data, url));
                continue;
            }
            if (n.description() != null && !n.description().isBlank() && n.description().trim().equalsIgnoreCase(name)) {
                out.add(nodeFinding(Outcome.NEEDS_REVIEW, tree, n, "Accessible description equals the name (\"" + abbreviate(name, 60) + "\"); screen readers announce it twice (typically a redundant title attribute).", data, url));
            }
        }
        if (checked == 0) {
            return List.of(Findings.inapplicable(id(), criteria(), "No named controls in the accessibility tree.", url));
        }
        if (out.isEmpty()) {
            out.add(finding(Outcome.PASSED, Target.page(), checked + " control name(s) are announced cleanly.", Map.of("checked", checked), url));
        }
        return Findings.attachScreenshots(ctx, out);
    }
}

package dev.a11yagent.core.rules;

import dev.a11yagent.core.ax.AxNode;
import dev.a11yagent.core.journey.JourneyRule;
import dev.a11yagent.core.journey.rules.ConsistentHelpRule;
import dev.a11yagent.core.journey.rules.ConsistentIdentificationRule;
import dev.a11yagent.core.journey.rules.ConsistentNavigationRule;
import dev.a11yagent.core.journey.rules.RedundantEntryRule;
import dev.a11yagent.core.model.Impact;
import dev.a11yagent.core.rules.ai.AltTextQualityRule;
import dev.a11yagent.core.rules.ax.AxReconciledNameRule;
import dev.a11yagent.core.rules.ax.FocusableWithoutRoleRule;
import dev.a11yagent.core.rules.ax.NameQualityRule;
import dev.a11yagent.core.rules.runtime.FocusNotObscuredRule;
import dev.a11yagent.core.rules.runtime.FocusOrderRule;
import dev.a11yagent.core.rules.runtime.FocusVisibleRule;
import dev.a11yagent.core.rules.runtime.KeyboardTrapRule;
import dev.a11yagent.core.rules.runtime.ReflowRule;
import dev.a11yagent.core.rules.runtime.ResizeTextRule;
import dev.a11yagent.core.rules.runtime.TextSpacingRule;
import dev.a11yagent.core.wcag.Criterion;
import dev.a11yagent.core.wcag.Wcag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The built-in rule catalogue. Deliberately does not re-implement the checks every DOM linter already
 * performs (missing alt, missing labels, colour contrast of solid text, duplicate ids, ...); it covers
 * the criteria those tools leave to manual testing.
 */
public final class Rules {

    private static final Map<String, Rule> PAGE_RULES = new LinkedHashMap<>();
    private static final Map<String, JourneyRule> JOURNEY_RULES = new LinkedHashMap<>();

    private static Set<Criterion> sc(String... ids) {
        return Set.of(java.util.Arrays.stream(ids).map(Wcag::get).toArray(Criterion[]::new));
    }

    private static void add(Rule r) {
        PAGE_RULES.put(r.id(), r);
    }

    static {
        // ---- DOM analysis baseline (what a linter checks), reconciled with the browser AX tree where it matters
        add(new AxReconciledNameRule("image-alt", "Images, image inputs, image maps and role=img elements have a text alternative (DOM + browser accessibility tree).",
                sc("1.1.1"), Impact.CRITICAL, n -> "image".equals(n.role()) || "img".equals(n.role()), "Image"));
        add(new AxReconciledNameRule("control-name", "Links, buttons, form fields, custom controls and frames have a non-empty accessible name (DOM + browser accessibility tree).",
                sc("4.1.2", "2.4.4", "1.3.1"), Impact.CRITICAL,
                n -> AxNode.NAME_REQUIRED_ROLES.contains(n.role()) && !"image".equals(n.role()) && !"img".equals(n.role()) || "Iframe".equals(n.role()), "Control"));
        add(new InPageRule("color-contrast", "Text has at least 4.5:1 contrast (3:1 for large text) against its composited background.", sc("1.4.3"), Impact.SERIOUS));
        add(new InPageRule("color-contrast-enhanced", "Text has at least 7:1 contrast (4.5:1 for large text) (AAA).", sc("1.4.6"), Impact.MINOR));
        add(new InPageRule("aria-validity", "ARIA roles, attributes, values, id references, required states and contexts are valid; presentational-role conflicts and aria-hidden focusable content are flagged.", sc("4.1.2"), Impact.SERIOUS));
        add(new InPageRule("duplicate-id-aria", "ids referenced by ARIA/label attributes are unique.", sc("4.1.2"), Impact.SERIOUS));
        add(new InPageRule("nested-interactive", "Interactive elements do not contain other interactive elements.", sc("4.1.2"), Impact.SERIOUS));
        add(new InPageRule("landmarks", "Exactly one main; banner/contentinfo top-level and unique; repeated landmarks distinguished by name; content contained in landmarks.", sc("1.3.1"), Impact.MODERATE));
        add(new InPageRule("bypass-blocks", "A skip link, landmarks or heading structure lets users bypass repeated blocks.", sc("2.4.1"), Impact.SERIOUS));
        add(new InPageRule("heading-structure", "Headings are non-empty, include a single h1 and do not skip levels.", sc("1.3.1"), Impact.MODERATE));
        add(new InPageRule("list-structure", "Lists contain only list items; list items live in lists; definition lists are well-formed.", sc("1.3.1"), Impact.MODERATE));
        add(new InPageRule("table-headers", "Data tables have header cells and valid headers references.", sc("1.3.1"), Impact.SERIOUS));
        add(new InPageRule("html-lang", "The page declares a valid default language.", sc("3.1.1"), Impact.SERIOUS));
        add(new InPageRule("lang-attr-valid", "lang attributes on parts of the page are valid BCP 47 tags.", sc("3.1.2"), Impact.MODERATE));
        add(new InPageRule("document-title", "The page has a descriptive title.", sc("2.4.2"), Impact.SERIOUS));
        add(new InPageRule("meta-viewport-zoom", "The viewport meta tag does not disable or cap zooming.", sc("1.4.4"), Impact.CRITICAL));
        add(new InPageRule("scrollable-region-focusable", "Scrollable regions are reachable by keyboard.", sc("2.1.1"), Impact.SERIOUS));
        add(new InPageRule("live-regions", "Live regions are scoped to status messages and not assertive by default.", sc("4.1.3"), Impact.MODERATE));
        add(new InPageRule("autocomplete-valid", "autocomplete attributes use valid tokens.", sc("1.3.5"), Impact.SERIOUS));
        // ---- browser accessibility tree (screen reader view)
        add(new FocusableWithoutRoleRule());
        add(new NameQualityRule());

        // ---- beyond-linter coverage
        // Perceivable
        add(new AltTextQualityRule());
        add(new InPageRule("use-of-color-links", "Inline links in text are distinguished by more than colour.", sc("1.4.1"), Impact.SERIOUS));
        add(new InPageRule("sensory-characteristics", "Instructions do not rely solely on shape, colour, size, visual location or sound.", sc("1.3.3"), Impact.MODERATE));
        add(new InPageRule("orientation", "Content is not locked to a single display orientation.", sc("1.3.4"), Impact.SERIOUS));
        add(new InPageRule("identify-input-purpose", "Fields collecting personal data carry the matching autocomplete token.", sc("1.3.5"), Impact.MODERATE));
        add(new ResizeTextRule());
        add(new ReflowRule());
        add(new TextSpacingRule());
        add(new InPageRule("content-on-hover-title", "Content revealed on hover/focus is dismissible, hoverable and persistent (native title tooltips are not).", sc("1.4.13"), Impact.MINOR));
        // Operable
        add(new InPageRule("keyboard-operable-controls", "Custom controls (onclick, interactive roles, cursor:pointer) are keyboard focusable.", sc("2.1.1"), Impact.CRITICAL));
        add(new KeyboardTrapRule());
        add(new InPageRule("timing-adjustable", "No automatic refresh/redirect with a fixed time limit.", sc("2.2.1"), Impact.SERIOUS));
        add(new InPageRule("pause-stop-hide", "Moving, blinking or auto-updating content longer than 5 seconds can be paused, stopped or hidden.", sc("2.2.2"), Impact.SERIOUS));
        add(new FocusOrderRule());
        add(new InPageRule("link-purpose-in-context", "Link purpose is determinable from the link text or its programmatic context.", sc("2.4.4"), Impact.SERIOUS));
        add(new InPageRule("headings-and-labels-descriptive", "Headings and form labels describe topic or purpose.", sc("2.4.6"), Impact.MODERATE));
        add(new FocusVisibleRule());
        add(new InPageRule("link-purpose-link-only", "Link purpose is determinable from the link text alone (AAA).", sc("2.4.9"), Impact.MODERATE));
        add(FocusNotObscuredRule.minimum());
        add(FocusNotObscuredRule.enhanced());
        add(new InPageRule("label-in-name", "Accessible names of controls contain their visible label text.", sc("2.5.3"), Impact.SERIOUS));
        add(new InPageRule("target-size-enhanced", "Pointer targets are at least 44x44 CSS px (AAA).", sc("2.5.5"), Impact.MINOR));
        add(new InPageRule("dragging-movements", "Dragging operations have a single-pointer alternative.", sc("2.5.7"), Impact.MODERATE));
        add(new InPageRule("target-size-minimum", "Pointer targets are at least 24x24 CSS px or sufficiently spaced/inline.", sc("2.5.8"), Impact.MODERATE));
        // Understandable
        add(new InPageRule("accessible-authentication-minimum", "Authentication does not require a cognitive function test (paste allowed, password managers allowed, CAPTCHA alternatives).", sc("3.3.8"), Impact.SERIOUS));
        add(new InPageRule("accessible-authentication-enhanced", "Authentication has no cognitive function test, including object recognition (AAA).", sc("3.3.9"), Impact.MODERATE));

        for (JourneyRule r : List.of(new ConsistentNavigationRule(), new ConsistentIdentificationRule(), new ConsistentHelpRule(), new RedundantEntryRule())) {
            JOURNEY_RULES.put(r.id(), r);
        }
    }

    private Rules() {
    }

    public static List<Rule> pageRules() {
        return List.copyOf(PAGE_RULES.values());
    }

    public static List<JourneyRule> journeyRules() {
        return List.copyOf(JOURNEY_RULES.values());
    }

    public static Optional<Rule> pageRule(String id) {
        return Optional.ofNullable(PAGE_RULES.get(id));
    }

    public static Optional<JourneyRule> journeyRule(String id) {
        return Optional.ofNullable(JOURNEY_RULES.get(id));
    }

    public static boolean exists(String id) {
        return PAGE_RULES.containsKey(id) || JOURNEY_RULES.containsKey(id);
    }

    /** Page rules covering the given success criterion. */
    public static List<Rule> pageRulesFor(Criterion c) {
        return PAGE_RULES.values().stream().filter(r -> r.criteria().contains(c)).toList();
    }

    public static List<JourneyRule> journeyRulesFor(Criterion c) {
        return JOURNEY_RULES.values().stream().filter(r -> r.criteria().contains(c)).toList();
    }

    /** All criteria covered by at least one rule. */
    public static Set<Criterion> coveredCriteria() {
        Set<Criterion> out = new java.util.LinkedHashSet<>();
        PAGE_RULES.values().forEach(r -> out.addAll(r.criteria()));
        JOURNEY_RULES.values().forEach(r -> out.addAll(r.criteria()));
        return out;
    }
}

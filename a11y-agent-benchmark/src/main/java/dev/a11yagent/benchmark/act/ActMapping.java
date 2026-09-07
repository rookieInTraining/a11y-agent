package dev.a11yagent.benchmark.act;

import dev.a11yagent.core.benchmark.RuleSelector;
import dev.a11yagent.core.model.Finding;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Many-to-many mapping from ACT rule ids to a11y-agent rule selectors, as the ACT Rules format expects
 * ("rule mapping"). A selector is {@code ruleId} or {@code ruleId#check}: the check narrows a single
 * a11y-agent rule so it can implement several ACT rules and still be scored precisely.
 *
 * <p>Only rules listed here are claimed as implemented; every other ACT rule is reported as a coverage
 * gap rather than silently scored.
 */
public final class ActMapping {

    private static final Map<String, Claim> CLAIMS = new LinkedHashMap<>();

    /**
     * @param ruleIds   a11y-agent rules to run for this ACT rule
     * @param selectors findings to score (rule plus optional sub-check)
     * @param note      why the rule is only partially implemented, or null
     */
    public record Claim(String actRuleId, String actRuleName, Set<String> ruleIds, List<RuleSelector> selectors, String note) {
        public boolean partial() {
            return note != null;
        }
    }

    private static void claim(String actRuleId, String actRuleName, String... selectors) {
        List<RuleSelector> list = new ArrayList<>(selectors.length);
        Set<String> ruleIds = new LinkedHashSet<>();
        for (String spec : selectors) {
            RuleSelector s = RuleSelector.parse(spec);
            list.add(s);
            ruleIds.add(s.ruleId());
        }
        CLAIMS.put(actRuleId, new Claim(actRuleId, actRuleName, ruleIds, List.copyOf(list), null));
    }

    static {
        // --- ARIA validity -------------------------------------------------
        claim("674b10", "Role attribute has valid value", "aria-validity#role-valid");
        claim("6a7281", "ARIA state or property has valid value", "aria-validity#aria-attr-value");
        claim("5f99a7", "ARIA attribute is defined in WAI-ARIA", "aria-validity#aria-attr-defined");
        claim("5c01ea", "ARIA state or property is permitted", "aria-validity#aria-attr-permitted");
        claim("4e8ab6", "Element with role attribute has required states and properties", "aria-validity#role-required-attrs");
        claim("ff89c9", "ARIA required context role", "aria-validity#role-required-context");
        claim("bc4a75", "ARIA required owned elements", "aria-validity#role-required-owned");
        claim("kb1m8s", "ARIA global properties not used where prohibited", "aria-validity#aria-prohibited");
        claim("6cfa84", "Element with aria-hidden has no content in sequential focus navigation", "aria-validity#aria-hidden-focusable");
        claim("307n5z", "Element with presentational children has no focusable content", "aria-validity#presentational-children");
        claim("46ca7f", "Element marked as decorative is not exposed",
                "aria-validity#decorative-not-exposed", "image-alt#decorative-not-exposed");

        // --- names ---------------------------------------------------------
        claim("23a2a8", "Image has non-empty accessible name", "image-alt#image-name");
        claim("59796f", "Image button has non-empty accessible name", "image-alt#image-button-name");
        claim("7d6734", "SVG element with explicit role has non-empty accessible name", "image-alt#svg-name");
        claim("8fc3b6", "Object element rendering non-text content has non-empty accessible name", "image-alt#object-name");
        claim("c487ae", "Link has non-empty accessible name", "control-name#link-name");
        claim("e086e5", "Form field has non-empty accessible name", "control-name#field-name");
        claim("97a4e1", "Button has non-empty accessible name", "control-name#button-name");
        claim("cae760", "Iframe element has non-empty accessible name", "control-name#iframe-name");
        claim("m6b1q3", "Menuitem has non-empty accessible name", "control-name#menuitem-name");
        claim("2t702h", "Summary element has non-empty accessible name", "control-name#summary-name");
        claim("ffd0e9", "Heading has non-empty accessible name", "control-name#heading-name");
        claim("2ee8b8", "Visible label is part of accessible name", "label-in-name");

        // --- contrast ------------------------------------------------------
        claim("afw4f7", "Text has minimum contrast", "color-contrast#contrast");
        claim("09o5cg", "Text has enhanced contrast", "color-contrast-enhanced#contrast");

        // --- language, title, viewport, refresh ----------------------------
        claim("b5c3f8", "HTML page has lang attribute", "html-lang#lang-present");
        claim("bf051a", "HTML page lang attribute has valid language tag", "html-lang#lang-valid");
        claim("de46e4", "Element with lang attribute has valid language tag", "lang-attr-valid#part-lang-valid");
        claim("2779a5", "HTML page has non-empty title", "document-title#title-present");
        claim("b4f0c3", "Meta viewport allows for zoom", "meta-viewport-zoom");
        claim("bc659a", "Meta element has no refresh delay", "timing-adjustable#meta-refresh");
        claim("bisz58", "Meta element has no refresh delay (no exception)", "timing-adjustable#meta-refresh-strict");
        claim("73f2c2", "autocomplete attribute has valid value", "autocomplete-valid");

        // --- tables --------------------------------------------------------
        claim("a25f45", "Headers attribute specified on a cell refers to cells in the same table element",
                "table-headers#headers-same-table");
        claim("d0f69e", "Table header cell has assigned cells", "table-headers#header-assigned");

        // --- keyboard / focus ----------------------------------------------
        claim("80af7b", "Focusable element has no keyboard trap", "no-keyboard-trap");
        claim("0ssw9k", "Scrollable content can be reached with sequential focus navigation", "scrollable-region-focusable");
        claim("oj04fd", "Element in sequential focus order has visible focus", "focus-visible");

        // --- text spacing (1.4.12), orientation, bypass --------------------
        claim("78fd32", "Important line height in style attributes is wide enough", "text-spacing-style-attr#line-height");
        claim("24afc2", "Important letter spacing in style attributes is wide enough", "text-spacing-style-attr#letter-spacing");
        claim("9e45ec", "Important word spacing in style attributes is wide enough", "text-spacing-style-attr#word-spacing");
        claim("b33eff", "Orientation of the page is not restricted using CSS transforms", "orientation");
        claim("cf77f2", "Bypass Blocks of Repeated Content", "bypass-blocks");
    }

    private ActMapping() {
    }

    public static Map<String, Claim> claims() {
        return Map.copyOf(CLAIMS);
    }

    public static boolean claimed(String actRuleId) {
        return CLAIMS.containsKey(actRuleId);
    }

    public static Set<String> rulesFor(String actRuleId) {
        Claim c = CLAIMS.get(actRuleId);
        return c == null ? Set.of() : c.ruleIds();
    }

    public static List<RuleSelector> selectorsFor(String actRuleId) {
        Claim c = CLAIMS.get(actRuleId);
        return c == null ? List.of() : c.selectors();
    }

    /** Findings that belong to the mapped selectors for this ACT rule. */
    public static List<Finding> select(String actRuleId, List<Finding> findings) {
        List<RuleSelector> selectors = selectorsFor(actRuleId);
        if (selectors.isEmpty()) {
            return List.of();
        }
        List<Finding> out = new ArrayList<>();
        for (Finding f : findings) {
            for (RuleSelector s : selectors) {
                if (s.matches(f)) {
                    out.add(f);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * ACT rules deliberately not implemented, with the reason. Used in the report so coverage gaps are
     * explicit instead of looking like failures.
     */
    public static final Map<String, String> NOT_IMPLEMENTED = Map.ofEntries(
            Map.entry("fd3a94", "Requires judging whether identical link names with different destinations serve an equivalent purpose."),
            Map.entry("b20e66", "Requires judging whether identical link names serve an equivalent purpose."),
            Map.entry("4b1c6c", "Requires judging whether identically named iframes have equivalent purpose."),
            Map.entry("9bd38c", "Requires judging whether a textual alternative for a visual reference exists (1.3.3)."),
            Map.entry("5effbb", "Requires judging whether a link is descriptive in context (covered as needs-review by link-purpose-in-context)."),
            Map.entry("aizyf1", "Requires judging whether link text alone is descriptive (covered as needs-review by link-purpose-link-only)."),
            Map.entry("b49b2e", "Requires judging whether a heading is descriptive (covered as needs-review by headings-and-labels-descriptive)."),
            Map.entry("cc0f0a", "Requires judging whether a form field label is descriptive (covered as needs-review)."),
            Map.entry("qt1vmo", "Requires judging whether an image name is descriptive (covered as needs-review by alt-text-quality)."),
            Map.entry("c4a8a4", "Requires judging whether a page title is descriptive (covered as needs-review by document-title)."),
            Map.entry("0va7u6", "Requires recognising text inside images; needs the vision model, not a deterministic rule."),
            Map.entry("ucwvc8", "Requires natural-language identification of the page's primary language."),
            Map.entry("off6ek", "Requires natural-language identification of the language of a passage."),
            Map.entry("36b590", "Requires judging whether an error message describes the invalid value."),
            Map.entry("3e12e1", "Requires judging whether a block of repeated content is collapsible in a usable way."),
            Map.entry("7677a9", "Device motion actuation cannot be exercised in a desktop browser session."),
            Map.entry("c249d5", "Device motion actuation cannot be exercised in a desktop browser session."),
            Map.entry("ebe86a", "Keyboard traps via non-standard navigation need author-documented key combinations."),
            Map.entry("a1b64e", "Covered by no-keyboard-trap for standard Tab; not claimed separately to avoid double-scoring."),
            Map.entry("2eb176", "Audio transcript equivalence requires human review."),
            Map.entry("e7aa44", "Audio text alternative equivalence requires human review."),
            Map.entry("afb423", "Audio as media alternative for text requires human review."),
            Map.entry("f51b46", "Caption correctness requires human review."),
            Map.entry("ee13b5", "Video transcript equivalence requires human review."),
            Map.entry("c3232f", "Video visual-only alternative requires human review."),
            Map.entry("c5a4ea", "Video alternative equivalence requires human review."),
            Map.entry("1a02b0", "Audio and visual transcript equivalence requires human review."),
            Map.entry("1ea59c", "Audio description correctness requires human review."),
            Map.entry("fd26cf", "Video as media alternative for text requires human review."),
            Map.entry("ab4d13", "Video as media alternative for text requires human review."),
            Map.entry("1ec09b", "Strict alternative equivalence requires human review."),
            Map.entry("eac66b", "Video auditory alternative requires human review."),
            Map.entry("d7ba54", "Audio track alternative requires human review."),
            Map.entry("aaa1bf", "Requires measuring audio duration of autoplaying media beyond 3 seconds with real playback."),
            Map.entry("80f0bf", "Autoplaying audio duration needs real media playback timing."),
            Map.entry("4c31df", "Autoplaying media control mechanism needs real playback."),
            Map.entry("efbfc7", "Pause/stop/hide of moving text is heuristic; claimed would inflate false positives."),
            Map.entry("59br37", "Zoom clipping is heuristic (covered as needs-review by resize-text)."),
            Map.entry("e88epe", "Decorative-image AX-tree exposure needs the vision/AX combination not yet claimed."),
            Map.entry("akn7bn", "Iframe tab-order exclusion is not yet a dedicated rule."),
            Map.entry("ffbc54", "Character-key shortcuts cannot be detected without exercising every printable key."),
            Map.entry("047fe0", "Bypass via heading for non-repeated content is a judgement of what is repeated."),
            Map.entry("ye5d6e", "Bypass via focus instrument for non-repeated content is a judgement of what is repeated."),
            Map.entry("b40fd1", "Bypass via landmark for non-repeated content is a judgement of what is repeated."));
}

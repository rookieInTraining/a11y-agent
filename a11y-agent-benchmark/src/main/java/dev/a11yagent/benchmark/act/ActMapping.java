package dev.a11yagent.benchmark.act;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Many-to-many mapping from ACT rule ids to a11y-agent rule ids, as the ACT Rules format expects
 * ("rule mapping"). Only rules listed here are claimed as implemented; every other ACT rule is
 * reported as a coverage gap rather than silently scored.
 */
public final class ActMapping {

    private static final Map<String, Claim> CLAIMS = new LinkedHashMap<>();

    /**
     * @param ruleIds  a11y-agent rules to run for this ACT rule
     * @param note     why the rule is only partially implemented, or null
     */
    public record Claim(String actRuleId, String actRuleName, Set<String> ruleIds, String note) {
        public boolean partial() {
            return note != null;
        }
    }

    private static void claim(String actRuleId, String actRuleName, String... ruleIds) {
        CLAIMS.put(actRuleId, new Claim(actRuleId, actRuleName, new LinkedHashSet<>(List.of(ruleIds)), null));
    }

    private static void partial(String actRuleId, String actRuleName, String note, String... ruleIds) {
        CLAIMS.put(actRuleId, new Claim(actRuleId, actRuleName, new LinkedHashSet<>(List.of(ruleIds)), note));
    }

    static {
        // --- ARIA validity -------------------------------------------------
        claim("674b10", "Role attribute has valid value", "aria-validity");
        claim("6a7281", "ARIA state or property has valid value", "aria-validity");
        claim("5f99a7", "ARIA attribute is defined in WAI-ARIA", "aria-validity");
        claim("5c01ea", "ARIA state or property is permitted", "aria-validity");
        claim("4e8ab6", "Element with role attribute has required states and properties", "aria-validity");
        claim("ff89c9", "ARIA required context role", "aria-validity");
        claim("bc4a75", "ARIA required owned elements", "aria-required-owned");
        claim("kb1m8s", "ARIA global properties not used where prohibited", "aria-validity");
        claim("6cfa84", "Element with aria-hidden has no content in sequential focus navigation", "aria-validity");
        claim("307n5z", "Element with presentational children has no focusable content", "presentational-children");
        claim("46ca7f", "Element marked as decorative is not exposed", "decorative-not-exposed");
        claim("e88epe", "Image not in the accessibility tree is decorative", "decorative-image-not-exposed");

        // --- names ---------------------------------------------------------
        claim("23a2a8", "Image has non-empty accessible name", "image-alt");
        claim("59796f", "Image button has non-empty accessible name", "image-alt", "control-name");
        claim("7d6734", "SVG element with explicit role has non-empty accessible name", "image-alt");
        claim("8fc3b6", "Object element rendering non-text content has non-empty accessible name", "control-name");
        claim("c487ae", "Link has non-empty accessible name", "control-name");
        claim("e086e5", "Form field has non-empty accessible name", "control-name");
        claim("97a4e1", "Button has non-empty accessible name", "control-name");
        claim("cae760", "Iframe element has non-empty accessible name", "control-name");
        claim("m6b1q3", "Menuitem has non-empty accessible name", "control-name");
        claim("2t702h", "Summary element has non-empty accessible name", "control-name");
        claim("ffd0e9", "Heading has non-empty accessible name", "heading-structure");
        claim("2ee8b8", "Visible label is part of accessible name", "label-in-name");

        // --- contrast ------------------------------------------------------
        claim("afw4f7", "Text has minimum contrast", "color-contrast");
        claim("09o5cg", "Text has enhanced contrast", "color-contrast-enhanced");

        // --- language, title, viewport, refresh ----------------------------
        claim("b5c3f8", "HTML page has lang attribute", "html-lang");
        claim("bf051a", "HTML page lang attribute has valid language tag", "html-lang");
        claim("de46e4", "Element with lang attribute has valid language tag", "lang-attr-valid");
        claim("2779a5", "HTML page has non-empty title", "document-title");
        claim("b4f0c3", "Meta viewport allows for zoom", "meta-viewport-zoom");
        claim("bc659a", "Meta element has no refresh delay", "timing-adjustable");
        claim("bisz58", "Meta element has no refresh delay (no exception)", "timing-adjustable-no-exception");

        // --- tables --------------------------------------------------------
        claim("a25f45", "Headers attribute specified on a cell refers to cells in the same table element", "table-headers");
        claim("d0f69e", "Table header cell has assigned cells", "table-header-assigned");

        // --- keyboard / focus ----------------------------------------------
        claim("80af7b", "Focusable element has no keyboard trap", "no-keyboard-trap");
        claim("a1b64e", "Focusable element has no keyboard trap via standard navigation", "no-keyboard-trap");
        claim("0ssw9k", "Scrollable content can be reached with sequential focus navigation", "scrollable-region-focusable");
        claim("akn7bn", "Iframe with interactive elements is not excluded from tab-order", "iframe-not-excluded-from-tab-order");
        claim("oj04fd", "Element in sequential focus order has visible focus", "focus-visible");
        claim("ffbc54", "No keyboard shortcut uses only printable characters", "character-key-shortcuts");

        // --- text spacing (1.4.12), zoom, orientation ----------------------
        claim("78fd32", "Important line height in style attributes is wide enough", "style-line-height");
        claim("24afc2", "Important letter spacing in style attributes is wide enough", "style-letter-spacing");
        claim("9e45ec", "Important word spacing in style attributes is wide enough", "style-word-spacing");
        claim("59br37", "Zoomed text node is not clipped with CSS overflow", "zoomed-text-not-clipped");
        claim("b33eff", "Orientation of the page is not restricted using CSS transforms", "orientation");

        // --- media with automatable expectations ---------------------------
        claim("80f0bf", "Audio or video element avoids automatically playing audio", "autoplay-audio");
        claim("4c31df", "Audio or video element that plays automatically has a control mechanism", "autoplay-control");
        claim("efbfc7", "Text content that changes automatically can be paused, stopped or hidden", "pause-stop-hide");

        // --- bypass blocks family -----------------------------------------
        claim("cf77f2", "Bypass Blocks of Repeated Content", "bypass-blocks");
        claim("047fe0", "Document has heading for non-repeated content", "heading-for-main-content");
        claim("ye5d6e", "Document has an instrument to move focus to non-repeated content", "focus-instrument-for-main-content");
        claim("b40fd1", "Document has a landmark with non-repeated content", "landmark-for-main-content");
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
            Map.entry("aaa1bf", "Requires measuring audio duration of autoplaying media beyond 3 seconds with real playback."));
}

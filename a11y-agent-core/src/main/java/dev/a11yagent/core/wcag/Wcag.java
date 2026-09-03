package dev.a11yagent.core.wcag;

import static dev.a11yagent.core.wcag.Level.A;
import static dev.a11yagent.core.wcag.Level.AA;
import static dev.a11yagent.core.wcag.Level.AAA;
import static dev.a11yagent.core.wcag.WcagVersion.V2_0;
import static dev.a11yagent.core.wcag.WcagVersion.V2_1;
import static dev.a11yagent.core.wcag.WcagVersion.V2_2;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Registry of all WCAG 2.x success criteria (2.0, 2.1 and 2.2; levels A, AA and AAA). */
public final class Wcag {

    private static final Map<String, Criterion> BY_ID = new LinkedHashMap<>();

    private static void c(String id, String name, Level level, WcagVersion since) {
        BY_ID.put(id, Criterion.of(id, name, level, since));
    }

    static {
        // Principle 1: Perceivable
        c("1.1.1", "Non-text Content", A, V2_0);
        c("1.2.1", "Audio-only and Video-only (Prerecorded)", A, V2_0);
        c("1.2.2", "Captions (Prerecorded)", A, V2_0);
        c("1.2.3", "Audio Description or Media Alternative (Prerecorded)", A, V2_0);
        c("1.2.4", "Captions (Live)", AA, V2_0);
        c("1.2.5", "Audio Description (Prerecorded)", AA, V2_0);
        c("1.2.6", "Sign Language (Prerecorded)", AAA, V2_0);
        c("1.2.7", "Extended Audio Description (Prerecorded)", AAA, V2_0);
        c("1.2.8", "Media Alternative (Prerecorded)", AAA, V2_0);
        c("1.2.9", "Audio-only (Live)", AAA, V2_0);
        c("1.3.1", "Info and Relationships", A, V2_0);
        c("1.3.2", "Meaningful Sequence", A, V2_0);
        c("1.3.3", "Sensory Characteristics", A, V2_0);
        c("1.3.4", "Orientation", AA, V2_1);
        c("1.3.5", "Identify Input Purpose", AA, V2_1);
        c("1.3.6", "Identify Purpose", AAA, V2_1);
        c("1.4.1", "Use of Color", A, V2_0);
        c("1.4.2", "Audio Control", A, V2_0);
        c("1.4.3", "Contrast (Minimum)", AA, V2_0);
        c("1.4.4", "Resize Text", AA, V2_0);
        c("1.4.5", "Images of Text", AA, V2_0);
        c("1.4.6", "Contrast (Enhanced)", AAA, V2_0);
        c("1.4.7", "Low or No Background Audio", AAA, V2_0);
        c("1.4.8", "Visual Presentation", AAA, V2_0);
        c("1.4.9", "Images of Text (No Exception)", AAA, V2_0);
        c("1.4.10", "Reflow", AA, V2_1);
        c("1.4.11", "Non-text Contrast", AA, V2_1);
        c("1.4.12", "Text Spacing", AA, V2_1);
        c("1.4.13", "Content on Hover or Focus", AA, V2_1);
        // Principle 2: Operable
        c("2.1.1", "Keyboard", A, V2_0);
        c("2.1.2", "No Keyboard Trap", A, V2_0);
        c("2.1.3", "Keyboard (No Exception)", AAA, V2_0);
        c("2.1.4", "Character Key Shortcuts", A, V2_1);
        c("2.2.1", "Timing Adjustable", A, V2_0);
        c("2.2.2", "Pause, Stop, Hide", A, V2_0);
        c("2.2.3", "No Timing", AAA, V2_0);
        c("2.2.4", "Interruptions", AAA, V2_0);
        c("2.2.5", "Re-authenticating", AAA, V2_0);
        c("2.2.6", "Timeouts", AAA, V2_1);
        c("2.3.1", "Three Flashes or Below Threshold", A, V2_0);
        c("2.3.2", "Three Flashes", AAA, V2_0);
        c("2.3.3", "Animation from Interactions", AAA, V2_1);
        c("2.4.1", "Bypass Blocks", A, V2_0);
        c("2.4.2", "Page Titled", A, V2_0);
        c("2.4.3", "Focus Order", A, V2_0);
        c("2.4.4", "Link Purpose (In Context)", A, V2_0);
        c("2.4.5", "Multiple Ways", AA, V2_0);
        c("2.4.6", "Headings and Labels", AA, V2_0);
        BY_ID.put("2.4.7", new Criterion("2.4.7", "Focus Visible", AA, V2_0, Optional.empty(), Optional.of(A)));
        c("2.4.8", "Location", AAA, V2_0);
        c("2.4.9", "Link Purpose (Link Only)", AAA, V2_0);
        c("2.4.10", "Section Headings", AAA, V2_0);
        c("2.4.11", "Focus Not Obscured (Minimum)", AA, V2_2);
        c("2.4.12", "Focus Not Obscured (Enhanced)", AAA, V2_2);
        c("2.4.13", "Focus Appearance", AAA, V2_2);
        c("2.5.1", "Pointer Gestures", A, V2_1);
        c("2.5.2", "Pointer Cancellation", A, V2_1);
        c("2.5.3", "Label in Name", A, V2_1);
        c("2.5.4", "Motion Actuation", A, V2_1);
        c("2.5.5", "Target Size (Enhanced)", AAA, V2_1);
        c("2.5.6", "Concurrent Input Mechanisms", AAA, V2_1);
        c("2.5.7", "Dragging Movements", AA, V2_2);
        c("2.5.8", "Target Size (Minimum)", AA, V2_2);
        // Principle 3: Understandable
        c("3.1.1", "Language of Page", A, V2_0);
        c("3.1.2", "Language of Parts", AA, V2_0);
        c("3.1.3", "Unusual Words", AAA, V2_0);
        c("3.1.4", "Abbreviations", AAA, V2_0);
        c("3.1.5", "Reading Level", AAA, V2_0);
        c("3.1.6", "Pronunciation", AAA, V2_0);
        c("3.2.1", "On Focus", A, V2_0);
        c("3.2.2", "On Input", A, V2_0);
        c("3.2.3", "Consistent Navigation", AA, V2_0);
        c("3.2.4", "Consistent Identification", AA, V2_0);
        c("3.2.5", "Change on Request", AAA, V2_0);
        c("3.2.6", "Consistent Help", A, V2_2);
        c("3.3.1", "Error Identification", A, V2_0);
        c("3.3.2", "Labels or Instructions", A, V2_0);
        c("3.3.3", "Error Suggestion", AA, V2_0);
        c("3.3.4", "Error Prevention (Legal, Financial, Data)", AA, V2_0);
        c("3.3.5", "Help", AAA, V2_0);
        c("3.3.6", "Error Prevention (All)", AAA, V2_0);
        c("3.3.7", "Redundant Entry", A, V2_2);
        c("3.3.8", "Accessible Authentication (Minimum)", AA, V2_2);
        c("3.3.9", "Accessible Authentication (Enhanced)", AAA, V2_2);
        // Principle 4: Robust
        BY_ID.put("4.1.1", new Criterion("4.1.1", "Parsing", A, V2_0, Optional.of(V2_2), Optional.empty()));
        c("4.1.2", "Name, Role, Value", A, V2_0);
        c("4.1.3", "Status Messages", AA, V2_1);
    }

    private Wcag() {
    }

    public static Criterion get(String id) {
        Criterion c = BY_ID.get(id);
        if (c == null) {
            throw new IllegalArgumentException("Unknown WCAG success criterion: " + id);
        }
        return c;
    }

    public static Optional<Criterion> find(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** All criteria across every version, in document order. */
    public static List<Criterion> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Criteria that exist in {@code version} and whose level (in that version) is at most {@code maxLevel}. */
    public static List<Criterion> forConformance(WcagVersion version, Level maxLevel) {
        return BY_ID.values().stream()
                .filter(c -> c.existsIn(version))
                .filter(c -> c.levelIn(version).includedIn(maxLevel))
                .sorted(Comparator.comparing(Wcag::sortKey))
                .collect(Collectors.toList());
    }

    static String sortKey(Criterion c) {
        String[] parts = c.id().split("\\.");
        return String.format("%02d.%02d.%02d",
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public static String principleName(String principleId) {
        return switch (principleId) {
            case "1" -> "Perceivable";
            case "2" -> "Operable";
            case "3" -> "Understandable";
            case "4" -> "Robust";
            default -> principleId;
        };
    }
}

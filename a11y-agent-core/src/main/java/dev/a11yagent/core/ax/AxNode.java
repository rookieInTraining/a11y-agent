package dev.a11yagent.core.ax;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One node of the browser's accessibility tree (what assistive technology receives), as exposed by
 * Chromium's {@code Accessibility.getFullAXTree}.
 *
 * @param id             tree node id
 * @param parentId       parent node id, null for the root
 * @param role           computed role ("button", "link", "generic", "StaticText", "image", ...)
 * @param name           computed accessible name (browser accname, authoritative)
 * @param description    computed accessible description
 * @param value          current value for controls
 * @param ignored        true when the node is pruned from the AT tree (aria-hidden, presentational, ...)
 * @param ignoredReasons reasons reported by the browser
 * @param properties     ARIA/state properties: focusable, hidden, invalid, required, expanded, level, live, ...
 * @param childIds       child node ids
 * @param backendNodeId  DOM backend node id, used to resolve the node back to an element
 */
public record AxNode(
        String id,
        String parentId,
        String role,
        String name,
        String description,
        String value,
        boolean ignored,
        List<String> ignoredReasons,
        Map<String, Object> properties,
        List<String> childIds,
        Long backendNodeId) {

    public static final Set<String> INTERACTIVE_ROLES = Set.of(
            "button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "menuitemcheckbox", "menuitemradio",
            "textbox", "searchbox", "combobox", "listbox", "slider", "spinbutton", "option", "treeitem", "scrollbar");

    public static final Set<String> NAME_REQUIRED_ROLES = Set.of(
            "button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "menuitemcheckbox", "menuitemradio",
            "textbox", "searchbox", "combobox", "listbox", "slider", "spinbutton", "progressbar", "meter", "image",
            "img", "dialog", "alertdialog", "radiogroup", "tree", "treegrid", "grid", "table", "region");

    public boolean focusable() {
        return Boolean.TRUE.equals(properties.get("focusable"));
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public boolean isGeneric() {
        return role == null || role.isBlank() || role.equals("generic") || role.equals("none") || role.equals("presentation")
                || role.equals("GenericContainer");
    }

    public boolean isInteractive() {
        return INTERACTIVE_ROLES.contains(role);
    }

    public Object property(String key) {
        return properties.get(key);
    }
}

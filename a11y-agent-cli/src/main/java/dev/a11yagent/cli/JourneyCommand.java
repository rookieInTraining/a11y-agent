package dev.a11yagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import dev.a11yagent.core.model.AuditReport;
import dev.a11yagent.playwright.A11yAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * Runs a journey described in YAML:
 *
 * <pre>
 * name: checkout
 * start: https://shop.example/
 * steps:
 *   - name: open cart
 *     actions:
 *       - click: "text=Cart"
 *   - name: identify
 *     actions:
 *       - fill: { selector: "#email", value: "jane@example.com" }
 *       - press: Enter
 *       - waitFor: "#address"          # selector to wait for
 *       - wait: 500                     # milliseconds
 *       - navigate: https://shop.example/checkout
 *       - clickRole: { role: button, name: "Continue" }
 *       - select: { selector: "#country", value: "DE" }
 *       - check: "#terms"
 * </pre>
 */
@Command(name = "journey", description = "Run a scripted multi-step journey from a YAML file and evaluate cross-page rules.", mixinStandardHelpOptions = true)
final class JourneyCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Journey YAML file.")
    Path file;

    @Mixin
    CommonOptions opts;

    @Override
    public Integer call() throws IOException {
        JsonNode def = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(file));
        String name = def.path("name").asText(file.getFileName().toString());
        try (BrowserSession s = new BrowserSession(opts)) {
            A11yAgent.JourneyBuilder jb = s.agent.journey(name);
            if (def.hasNonNull("start")) {
                jb.start(def.get("start").asText());
            }
            for (JsonNode step : def.path("steps")) {
                String stepName = step.path("name").asText("step");
                JsonNode actions = step.path("actions");
                jb.step(stepName, page -> actions.forEach(a -> apply(page, a)));
            }
            AuditReport report = jb.run();
            return opts.finish(report);
        }
    }

    static void apply(Page page, JsonNode action) {
        var fields = action.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            JsonNode v = e.getValue();
            switch (e.getKey()) {
                case "navigate" -> { page.navigate(v.asText()); page.waitForLoadState(); }
                case "click" -> page.locator(v.asText()).first().click();
                case "clickRole" -> page.getByRole(AriaRole.valueOf(v.path("role").asText("button").toUpperCase()),
                        new Page.GetByRoleOptions().setName(v.path("name").asText())).first().click();
                case "fill" -> page.locator(v.path("selector").asText()).first().fill(v.path("value").asText());
                case "select" -> page.locator(v.path("selector").asText()).first().selectOption(v.path("value").asText());
                case "check" -> page.locator(v.asText()).first().check();
                case "press" -> page.keyboard().press(v.asText());
                case "type" -> page.keyboard().type(v.asText());
                case "wait" -> page.waitForTimeout(v.asLong());
                case "waitFor" -> page.locator(v.asText()).first().waitFor();
                case "evaluate" -> page.evaluate(v.asText());
                default -> throw new IllegalArgumentException("Unknown journey action: " + e.getKey());
            }
        }
        page.waitForLoadState();
    }
}

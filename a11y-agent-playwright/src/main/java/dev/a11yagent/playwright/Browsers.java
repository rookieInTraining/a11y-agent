package dev.a11yagent.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * Convenience launcher used by the CLI and tests. Honours {@code A11Y_BROWSER_CHANNEL} (e.g. {@code chrome},
 * {@code msedge}) so a system browser can be used instead of the Playwright-managed Chromium, and
 * {@code A11Y_BROWSER_EXECUTABLE} for an explicit binary.
 */
public final class Browsers {

    private Browsers() {
    }

    public static Browser launchChromium(Playwright playwright, boolean headless) {
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(headless);
        String channel = System.getenv("A11Y_BROWSER_CHANNEL");
        String exe = System.getenv("A11Y_BROWSER_EXECUTABLE");
        if (exe != null && !exe.isBlank()) {
            opts.setExecutablePath(java.nio.file.Path.of(exe));
        } else if (channel != null && !channel.isBlank()) {
            opts.setChannel(channel);
        }
        return playwright.chromium().launch(opts);
    }
}

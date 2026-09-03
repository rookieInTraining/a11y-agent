package dev.a11yagent.playwright;

import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Clip;
import com.microsoft.playwright.options.ViewportSize;
import dev.a11yagent.core.ax.AxTree;
import dev.a11yagent.core.driver.PageDriver;
import dev.a11yagent.core.driver.Rect;
import dev.a11yagent.core.driver.Viewport;
import java.util.Optional;

/** {@link PageDriver} backed by a Playwright {@link Page}. */
public final class PlaywrightDriver implements PageDriver {

    private final Page page;

    public PlaywrightDriver(Page page) {
        this.page = page;
    }

    public Page page() {
        return page;
    }

    @Override
    public String url() {
        return page.url();
    }

    @Override
    public String title() {
        return page.title();
    }

    @Override
    public Object evaluate(String functionExpression, Object arg) {
        return page.evaluate(functionExpression, arg);
    }

    @Override
    public byte[] screenshot(boolean fullPage) {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(fullPage).setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
    }

    @Override
    public byte[] screenshotClip(Rect clip) {
        return page.screenshot(new Page.ScreenshotOptions()
                .setClip(new Clip(clip.x(), clip.y(), Math.max(1, clip.width()), Math.max(1, clip.height())))
                .setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
    }

    @Override
    public void press(String key) {
        page.keyboard().press(key);
    }

    @Override
    public Viewport viewport() {
        ViewportSize v = page.viewportSize();
        if (v == null) {
            Object dims = page.evaluate("() => ({w: window.innerWidth, h: window.innerHeight})");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) dims;
            return new Viewport(((Number) m.get("w")).intValue(), ((Number) m.get("h")).intValue());
        }
        return new Viewport(v.width, v.height);
    }

    @Override
    public void setViewport(Viewport viewport) {
        page.setViewportSize(viewport.width(), viewport.height());
    }

    @Override
    public void navigate(String url) {
        page.navigate(url);
        page.waitForLoadState();
    }

    @Override
    public void waitMillis(long millis) {
        page.waitForTimeout(millis);
    }

    private CDPSession cdp;
    private boolean cdpUnavailable;

    /** One DevTools session per page, created lazily and reused for every audit of that page. */
    private Optional<CDPSession> cdpSession() {
        if (cdp == null && !cdpUnavailable) {
            Optional<CDPSession> s = CdpAccessibilityTree.session(page);
            cdpUnavailable = s.isEmpty();
            cdp = s.orElse(null);
        }
        return Optional.ofNullable(cdp);
    }

    @Override
    public Optional<AxTree> accessibilityTree() {
        return cdpSession().flatMap(CdpAccessibilityTree::fetch);
    }

    /** Human readable rendering of the exposed accessibility tree (roles, names, states). */
    public String renderAccessibilityTree(int maxDepth) {
        return accessibilityTree().map(t -> CdpAccessibilityTree.render(t, maxDepth)).orElse("(accessibility tree unavailable: not a Chromium page)");
    }
}

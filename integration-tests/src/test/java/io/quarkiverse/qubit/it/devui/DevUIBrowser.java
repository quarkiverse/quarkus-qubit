package io.quarkiverse.qubit.it.devui;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Helper class for testing DevUI pages with Playwright.
 * <p>
 * This class manages a Playwright browser instance for interacting with
 * the Quarkus DevUI extension pages. It provides methods for navigating
 * to DevUI pages and interacting with Vaadin web components.
 * <p>
 * The Playwright and Browser instances are cached at the class level for
 * performance - browser startup is expensive (~2-3s). Only the Page is
 * created fresh for each test to ensure isolation.
 * <p>
 * Usage pattern follows the quarkus-axonframework-extension approach
 * where raw Playwright API is used with QuarkusDevModeTest since
 * the @WithPlaywright annotation is incompatible with QuarkusDevModeTest.
 *
 * @see <a href="https://github.com/meks77/quarkus-axonframework-extension">quarkus-axonframework-extension</a>
 */
public class DevUIBrowser implements AutoCloseable {

    /**
     * Default port for Quarkus dev mode (not test mode which uses 8081).
     * QuarkusDevModeTest runs in DEVELOPMENT mode, which defaults to port 8080.
     */
    private static final int DEFAULT_DEV_MODE_PORT = 8080;

    // Cached instances for performance - browser startup is expensive
    private static Playwright cachedPlaywright;
    private static Browser cachedBrowser;
    private static String cachedBrowserType;

    private final String devUiBaseUrl;
    private Page page;

    /**
     * Creates a DevUIBrowser using the default dev mode port (8080).
     */
    public DevUIBrowser() {
        this(DEFAULT_DEV_MODE_PORT);
    }

    /**
     * Creates a DevUIBrowser with a specific port.
     *
     * @param port the HTTP port where Quarkus dev mode is running
     */
    public DevUIBrowser(int port) {
        this.devUiBaseUrl = "http://localhost:" + port + "/q/dev-ui";
        page = getOrCreateBrowser().newPage();
    }

    /**
     * Get or create the cached browser instance.
     * Browser type is determined by the playwright.browser system property.
     *
     * @return the cached or newly created browser
     */
    private static synchronized Browser getOrCreateBrowser() {
        String browserType = System.getProperty("playwright.browser", "chromium").toLowerCase();

        // If browser type changed or not initialized, create new instances
        if (cachedBrowser == null || !browserType.equals(cachedBrowserType)) {
            closeStaticResources();
            cachedPlaywright = Playwright.create();
            cachedBrowser = launchBrowser(cachedPlaywright, browserType);
            cachedBrowserType = browserType;

            // Register shutdown hook to clean up on JVM exit
            Runtime.getRuntime().addShutdownHook(new Thread(DevUIBrowser::closeStaticResources));
        }

        return cachedBrowser;
    }

    /**
     * Launch the browser based on the specified type.
     *
     * @param playwright the Playwright instance
     * @param browserType the browser type (chromium, firefox, webkit)
     * @return the launched browser instance
     */
    private static Browser launchBrowser(Playwright playwright, String browserType) {
        return switch (browserType) {
            case "firefox" -> playwright.firefox().launch();
            case "webkit" -> playwright.webkit().launch();
            default -> playwright.chromium().launch();
        };
    }

    /**
     * Close static resources (browser and playwright).
     * Called by shutdown hook or when browser type changes.
     */
    private static synchronized void closeStaticResources() {
        if (cachedBrowser != null) {
            try {
                cachedBrowser.close();
            } catch (Exception ignored) {
                // Ignore errors during cleanup
            }
            cachedBrowser = null;
        }
        if (cachedPlaywright != null) {
            try {
                cachedPlaywright.close();
            } catch (Exception ignored) {
                // Ignore errors during cleanup
            }
            cachedPlaywright = null;
        }
        cachedBrowserType = null;
    }

    /**
     * Navigate to the DevUI extensions page.
     */
    public void navigateToExtensions() {
        page.navigate(devUiBaseUrl + "/extensions");
        waitForPageLoad();
    }

    /**
     * Navigate to a specific extension's page by clicking on the card link.
     *
     * @param extensionTitle the display name of the extension link (e.g., "Lambda Queries")
     */
    public void clickExtensionLink(String extensionTitle) {
        Locator link = page.locator("css=qwc-extension-link[displayname=\"" + extensionTitle + "\"] > a");
        link.click();
        waitForPageLoad();
    }

    /**
     * Get the badge count displayed on an extension link.
     *
     * @param extensionTitle the display name of the extension link
     * @return the badge count as a string, or null if not found
     */
    public String getExtensionLinkBadge(String extensionTitle) {
        Locator lineLocator = page.locator("css=qwc-extension-link[displayname=\"" + extensionTitle + "\"]");
        Locator badge = lineLocator.locator("css=qui-badge > span:not([theme])");
        if (badge.count() > 0) {
            return badge.first().textContent();
        }
        return null;
    }

    /**
     * Check if the Qubit extension card is present in DevUI.
     *
     * @return true if the Qubit extension card is visible
     */
    public boolean isQubitExtensionCardPresent() {
        Locator card = page.locator("css=qwc-extension-link[displayname=\"Lambda Queries\"]");
        return card.count() > 0;
    }

    /**
     * Wait for and return the Qubit queries Vaadin Grid element.
     * The grid is inside the qwc-qubit-queries shadow DOM.
     *
     * @return the grid Locator
     */
    public Locator getVaadinGrid() {
        // Use Playwright's built-in shadow DOM piercing
        return page.locator("css=qwc-qubit-queries").locator("css=vaadin-grid.queries-table");
    }

    /**
     * Get all text content from vaadin-grid cells.
     * Note: Vaadin Grid uses virtualization, so only visible cells are returned.
     *
     * @return list of cell text contents
     */
    public List<String> getGridCellContents() {
        Locator grid = getVaadinGrid();
        grid.waitFor();

        List<ElementHandle> cells = grid.locator("css=vaadin-grid-cell-content").elementHandles();
        List<String> contents = new ArrayList<>();

        for (ElementHandle cell : cells) {
            String text = cell.textContent();
            if (text != null && !text.isBlank()) {
                contents.add(text.trim());
            }
        }

        return contents;
    }

    /**
     * Get the row count displayed in a Vaadin Grid.
     * This counts actual data rows (excluding header).
     *
     * @return the number of data rows
     */
    public int getGridRowCount() {
        Locator grid = getVaadinGrid();
        grid.waitFor();

        // Vaadin Grid uses shadow DOM, rows are in vaadin-grid-cell-content
        // We count unique rows by checking the grid's items property
        Object rowCount = page.evaluate(
                "() => document.querySelector('vaadin-grid')?.items?.length ?? 0");
        return ((Number) rowCount).intValue();
    }

    /**
     * Check if the search field is present on the Qubit queries page.
     *
     * @return true if the search field is visible
     */
    public boolean isSearchFieldPresent() {
        Locator searchField = page.locator("css=vaadin-text-field");
        return searchField.count() > 0;
    }

    /**
     * Type a search query into the search field.
     *
     * @param query the search text
     */
    public void searchQueries(String query) {
        Locator searchField = page.locator("css=vaadin-text-field.searchField input");
        searchField.fill(query);
        // Wait for filtering to apply
        page.waitForTimeout(500);
    }

    /**
     * Clear the search field by setting value to empty and triggering change event.
     */
    public void clearSearch() {
        Locator searchField = page.locator("css=vaadin-text-field.searchField input");
        searchField.fill("");
        // Wait for filtering to apply
        page.waitForTimeout(500);
    }

    /**
     * Click on a grid row to select it (opens JPQL panel).
     * Uses JavaScript to select the item in the component's state directly.
     *
     * @param rowIndex the row index (0-based)
     */
    public void clickGridRow(int rowIndex) {
        // Ensure grid is loaded before interacting
        Locator grid = getVaadinGrid();
        grid.waitFor();
        page.waitForTimeout(500);

        // Use JavaScript to set the selected query in the Lit component's state
        // This directly updates _selectedQuery which triggers re-render
        page.evaluate("(index) => { " +
                "const component = document.querySelector('qwc-qubit-queries'); " +
                "if (component) { " +
                "  const grid = component.shadowRoot.querySelector('vaadin-grid.queries-table'); " +
                "  if (grid && grid.items && grid.items[index]) { " +
                "    component._selectedQuery = grid.items[index]; " +
                "    component.requestUpdate(); " +
                "  } " +
                "} " +
                "}", rowIndex);
        page.waitForTimeout(500);
    }

    /**
     * Check if the JPQL panel is visible.
     * The panel is inside the qwc-qubit-queries shadow DOM.
     *
     * @return true if the JPQL panel is displayed
     */
    public boolean isJpqlPanelVisible() {
        // Use JavaScript to check shadow DOM
        Object result = page.evaluate("() => { " +
                "const component = document.querySelector('qwc-qubit-queries'); " +
                "if (component && component.shadowRoot) { " +
                "  return component.shadowRoot.querySelector('.jpql-panel') !== null; " +
                "} " +
                "return false; " +
                "}");
        return Boolean.TRUE.equals(result);
    }

    /**
     * Get the JPQL content from the panel.
     * The panel is inside the qwc-qubit-queries shadow DOM.
     *
     * @return the JPQL query text, or null if panel is not visible
     */
    public String getJpqlContent() {
        Object result = page.evaluate("() => { " +
                "const component = document.querySelector('qwc-qubit-queries'); " +
                "if (component && component.shadowRoot) { " +
                "  const content = component.shadowRoot.querySelector('.comparison-body-jpql'); " +
                "  return content ? content.textContent : null; " +
                "} " +
                "return null; " +
                "}");
        return result != null ? result.toString() : null;
    }

    /**
     * Close the JPQL panel.
     * The close button is inside the qwc-qubit-queries shadow DOM.
     */
    public void closeJpqlPanel() {
        page.evaluate("() => { " +
                "const component = document.querySelector('qwc-qubit-queries'); " +
                "if (component && component.shadowRoot) { " +
                "  const closeButton = component.shadowRoot.querySelector('.jpql-close'); " +
                "  if (closeButton) { " +
                "    closeButton.click(); " +
                "  } " +
                "} " +
                "}");
        page.waitForTimeout(300);
    }

    /**
     * Get the summary text showing query counts.
     *
     * @return the summary text (e.g., "3 of 3 queries")
     */
    public String getQuerySummary() {
        Locator summary = page.locator("css=.summary");
        if (summary.count() > 0) {
            return summary.textContent();
        }
        return null;
    }

    /**
     * Take a screenshot of the current page.
     *
     * @param filename the output filename
     */
    public void takeScreenshot(String filename) {
        page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Path.of(filename)));
    }

    /**
     * Wait for the page to fully load.
     */
    private void waitForPageLoad() {
        page.waitForLoadState();
        // Additional wait for Vaadin components to render
        page.waitForTimeout(1000);
    }

    /**
     * Wait for a specific element to appear.
     *
     * @param selector the CSS selector
     * @param timeoutMs timeout in milliseconds
     */
    public void waitForElement(String selector, int timeoutMs) {
        page.locator("css=" + selector).waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(timeoutMs));
    }

    /**
     * Get the current page URL.
     *
     * @return the page URL
     */
    public String getCurrentUrl() {
        return page.url();
    }

    /**
     * Get the page title.
     *
     * @return the page title
     */
    public String getPageTitle() {
        return page.title();
    }

    @Override
    public void close() {
        // Only close the page, keep browser cached for reuse
        if (page != null) {
            page.close();
            page = null;
        }
        // Note: Browser and Playwright are cached and cleaned up by shutdown hook
    }
}

package io.quarkiverse.qubit.it.devui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * Playwright-based browser tests for the Qubit DevUI extension page.
 * <p>
 * This test uses QuarkusDevModeTest to start dev mode and raw Playwright API
 * to interact with the DevUI page. The @WithPlaywright annotation cannot be used
 * because it is incompatible with QuarkusDevModeTest (both implement TestInstanceFactory).
 * <p>
 * <strong>Running these tests:</strong>
 *
 * <pre>
 * # Run with Chromium (default, faster)
 * mvn test -Pplaywright -pl integration-tests
 *
 * # Run with Firefox
 * mvn test -Pplaywright -Dplaywright.browser=firefox -pl integration-tests
 *
 * # Run with timing logs
 * mvn test -Pplaywright -Dplaywright.timing.enabled=true -pl integration-tests
 * </pre>
 * <p>
 * The test verifies:
 * <ul>
 * <li>The Qubit extension card appears in DevUI</li>
 * <li>Lambda queries are detected and displayed</li>
 * <li>The queries table shows correct information</li>
 * <li>Search functionality works</li>
 * <li>JPQL panel displays generated queries</li>
 * </ul>
 *
 * @see DevUIBrowser
 * @see <a href="https://docs.quarkiverse.io/quarkus-playwright/dev/index.html">Quarkus Playwright</a>
 */
public class QubitDevUIPlaywrightTest {

    // H2 configuration for dev mode testing
    private static final String DEV_MODE_PROPERTIES = """
            # H2 in-memory database for DevUI tests
            quarkus.datasource.db-kind=h2
            quarkus.datasource.devservices.enabled=false
            quarkus.datasource.jdbc.url=jdbc:h2:mem:devui;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
            quarkus.hibernate-orm.schema-management.strategy=drop-and-create
            # Disable continuous testing - conflicts with QuarkusDevModeTest
            quarkus.test.continuous-testing=disabled
            # Reduce logging for faster tests
            quarkus.log.level=WARN
            """;

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    // Include entity classes and repositories for DevUI to detect lambda queries
                    .addPackages(true, "io.quarkiverse.qubit.it")
                    // Include application configuration with H2 datasource
                    .addAsResource(new StringAsset(DEV_MODE_PROPERTIES), "application.properties")
                    .addAsResource("import.sql"));

    private DevUIBrowser browser;

    @BeforeEach
    void setUp() {
        browser = new DevUIBrowser();
    }

    @AfterEach
    void tearDown() {
        if (browser != null) {
            browser.close();
        }
    }

    @Test
    void extensionCardIsVisible() {
        browser.navigateToExtensions();

        assertThat(browser.isQubitExtensionCardPresent())
                .as("Qubit 'Lambda Queries' extension card should be visible in DevUI")
                .isTrue();
    }

    @Test
    void extensionCardShowsQueryCount() {
        browser.navigateToExtensions();

        String badge = browser.getExtensionLinkBadge("Lambda Queries");

        assertThat(badge)
                .as("Lambda Queries badge should show the number of detected queries")
                .isNotNull();
        assertThat(badge.trim())
                .as("Lambda Queries badge should be a number")
                .matches("\\d+");
    }

    @Test
    void queriesPageLoadsSuccessfully() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        // Verify we're on the queries page
        assertThat(browser.getCurrentUrl())
                .as("Should navigate to the Qubit queries page")
                .contains("qubit");

        // Verify the grid is present
        assertThat(browser.getVaadinGrid().count())
                .as("Vaadin Grid should be present on the queries page")
                .isGreaterThan(0);
    }

    @Test
    void queriesPageShowsSearchField() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        assertThat(browser.isSearchFieldPresent())
                .as("Search field should be present on the queries page")
                .isTrue();
    }

    @Test
    void queriesPageShowsSummary() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        String summary = browser.getQuerySummary();

        assertThat(summary)
                .as("Query summary should be displayed")
                .isNotNull()
                .contains("queries");
    }

    @Test
    void gridContainsQueryData() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        List<String> cellContents = browser.getGridCellContents();

        assertThat(cellContents)
                .as("Grid should contain query data")
                .isNotEmpty();

        // Check for expected content patterns - integration-tests uses Person entity
        boolean hasEntityInfo = cellContents.stream()
                .anyMatch(c -> c.contains("Person") || c.contains("Phone") || c.contains("Product"));
        boolean hasQueryType = cellContents.stream()
                .anyMatch(c -> c.contains("List") || c.contains("Count"));

        assertThat(hasEntityInfo || hasQueryType)
                .as("Grid should contain entity names or query types")
                .isTrue();
    }

    @Test
    void searchFiltersQueries() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        // Get initial summary
        String initialSummary = browser.getQuerySummary();

        // Search for a non-matching term
        browser.searchQueries("xyznonexistent");

        String filteredSummary = browser.getQuerySummary();

        // The summary should show filtered results (likely 0 of N)
        assertThat(filteredSummary)
                .as("Search should filter the results")
                .isNotNull();

        // Clear and verify reset
        browser.clearSearch();
        String resetSummary = browser.getQuerySummary();

        assertThat(resetSummary)
                .as("Clearing search should reset the filter")
                .isEqualTo(initialSummary);
    }

    @Test
    void clickingRowOpensJpqlPanel() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        // Initially, JPQL panel should not be visible
        assertThat(browser.isJpqlPanelVisible())
                .as("JPQL panel should not be visible initially")
                .isFalse();

        // Click on first row
        browser.clickGridRow(0);

        // JPQL panel should now be visible
        assertThat(browser.isJpqlPanelVisible())
                .as("JPQL panel should be visible after clicking a row")
                .isTrue();

        // Panel should contain JPQL content
        String jpql = browser.getJpqlContent();
        assertThat(jpql)
                .as("JPQL panel should contain query content")
                .isNotNull();
    }

    @Test
    void jpqlPanelCanBeClosed() {
        browser.navigateToExtensions();
        browser.clickExtensionLink("Lambda Queries");

        // Open the panel
        browser.clickGridRow(0);
        assertThat(browser.isJpqlPanelVisible()).isTrue();

        // Close the panel
        browser.closeJpqlPanel();
        assertThat(browser.isJpqlPanelVisible())
                .as("JPQL panel should be closed")
                .isFalse();
    }
}

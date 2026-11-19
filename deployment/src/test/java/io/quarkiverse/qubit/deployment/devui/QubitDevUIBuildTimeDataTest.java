package io.quarkiverse.qubit.deployment.devui;

import java.util.List;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Tests that the Qubit DevUI exposes query build-time data correctly.
 * <p>
 * This test verifies that the DevUI build-time data is properly exposed
 * even when no queries are present (empty array).
 */
public class QubitDevUIBuildTimeDataTest extends DevUIBuildTimeDataTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(Dummy.class)
                    .addAsResource(new StringAsset("""
                            quarkus.datasource.db-kind=h2
                            quarkus.datasource.jdbc.url=jdbc:h2:mem:test
                            quarkus.hibernate-orm.schema-management.strategy=drop-and-create
                            """),
                            "application.properties"));

    public QubitDevUIBuildTimeDataTest() {
        super("quarkus-qubit");
    }

    @Test
    public void testQueriesDataKeyExists() throws Exception {
        List<String> allKeys = super.getAllKeys();
        Assertions.assertNotNull(allKeys);
        Assertions.assertTrue(allKeys.contains("queries"),
                "Expected 'queries' key in build-time data. Found keys: " + allKeys);
    }

    @Test
    public void testQueriesIsArray() throws Exception {
        // not typed as JsonNode: it is the Jackson 2 one in Quarkus 3 and the Jackson 3 one in Quarkus 4
        Object queries = super.getBuildTimeData("queries");
        Assertions.assertNotNull(queries, "Expected queries data to be present");
        Assertions.assertTrue(queries.toString().startsWith("["), "Expected queries to be an array");
    }

    public static class Dummy {
        // Minimal class to satisfy archive requirements
    }
}

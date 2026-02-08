package io.quarkiverse.qubit.deployment.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for individual lambda bytecode analysis.
 * <p>
 * Records timing and metadata for analyzing a single lambda expression.
 * Enables drill-down into which lambdas are slow to analyze.
 */
@Name("io.quarkiverse.qubit.Analysis")
@Label("Qubit Analysis")
@Category({"Qubit", "Analysis"})
@Description("Individual lambda bytecode analysis timing")
@StackTrace(false)
public class QubitAnalysisEvent extends Event {

    @Label("Call Site ID")
    @Description("Unique identifier for the lambda call site")
    public String callSiteId;

    @Label("Owner Class")
    @Description("Class containing the lambda")
    public String ownerClass;

    @Label("Query Type")
    @Description("Type of query (SIMPLE, JOIN, GROUP, AGGREGATION)")
    public String queryType;

    @Label("Success")
    @Description("Whether analysis succeeded")
    public boolean success;

    @Label("Deduplicated")
    @Description("Whether this was a duplicate (reused existing executor)")
    public boolean deduplicated;

    @Label("Lambda Hash")
    @Description("Hash of the analyzed lambda (first 8 chars)")
    public String lambdaHash;

    /**
     * Creates and starts an analysis event.
     */
    public static QubitAnalysisEvent start(String callSiteId, String ownerClass) {
        QubitAnalysisEvent event = new QubitAnalysisEvent();
        event.callSiteId = callSiteId;
        event.ownerClass = ownerClass;
        event.begin();
        return event;
    }

    /**
     * Completes the analysis event with results and commits it.
     */
    public void complete(String queryType, boolean success, boolean deduplicated, String lambdaHash) {
        this.queryType = queryType;
        this.success = success;
        this.deduplicated = deduplicated;
        this.lambdaHash = lambdaHash != null && lambdaHash.length() > 8
                ? lambdaHash.substring(0, 8) : lambdaHash;
        this.end();
        this.commit();
    }

    /**
     * Marks as failed and commits.
     */
    public void fail(String reason) {
        this.queryType = "FAILED";
        this.success = false;
        this.deduplicated = false;
        this.end();
        this.commit();
    }
}

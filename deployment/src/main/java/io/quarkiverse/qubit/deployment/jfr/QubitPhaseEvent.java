package io.quarkiverse.qubit.deployment.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for high-level Qubit build phases.
 * <p>
 * Records timing for major phases: discovery, analysis, code generation, enhancement.
 * Can be visualized in JDK Mission Control or IntelliJ JFR Profiler.
 */
@Name("io.quarkiverse.qubit.Phase")
@Label("Qubit Phase")
@Category({ "Qubit", "Build" })
@Description("High-level Qubit build phase timing")
@StackTrace(false)
public class QubitPhaseEvent extends Event {

    @Label("Phase Name")
    @Description("Name of the build phase (e.g., discovery, analysis, codegen)")
    public String phase;

    @Label("Item Count")
    @Description("Number of items processed in this phase")
    public int itemCount;

    @Label("Success Count")
    @Description("Number of successfully processed items")
    public int successCount;

    @Label("Skip Count")
    @Description("Number of skipped items (deduplicated)")
    public int skipCount;

    /**
     * Creates and starts a phase event.
     */
    public static QubitPhaseEvent start(String phase) {
        QubitPhaseEvent event = new QubitPhaseEvent();
        event.phase = phase;
        event.begin();
        return event;
    }

    /**
     * Completes the phase event with counts and commits it.
     */
    public void complete(int itemCount, int successCount, int skipCount) {
        this.itemCount = itemCount;
        this.successCount = successCount;
        this.skipCount = skipCount;
        this.end();
        this.commit();
    }
}

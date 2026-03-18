package io.quarkiverse.qubit.deployment.jfr;

import org.jspecify.annotations.Nullable;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * JFR event for individual class scanning during lambda discovery.
 * Captures per-class metrics: quick check result, full parse status, call sites found.
 */
@Name("io.quarkiverse.qubit.ClassScan")
@Label("Qubit Class Scan")
@Category({ "Qubit", "Build", "Scanning" })
@Description("Individual class scanning event during lambda discovery phase")
public class QubitScanEvent extends Event {

    @Label("Class Name")
    @Description("Fully qualified name of the scanned class")
    public String className;

    @Label("Quick Check Passed")
    @Description("Whether the quick invokedynamic check detected potential lambdas")
    public boolean quickCheckPassed;

    @Label("Full Parse Performed")
    @Description("Whether full ASM parsing was performed")
    public boolean fullParsed;

    @Label("Call Sites Found")
    @Description("Number of lambda call sites found in this class")
    public int callSitesFound;

    @Label("Skipped Reason")
    @Description("Reason for skipping (null if not skipped)")
    public @Nullable String skippedReason;

    /** Starts a new scan event for the specified class. */
    public static QubitScanEvent start(String className) {
        QubitScanEvent event = new QubitScanEvent();
        event.className = className;
        event.begin();
        return event;
    }

    /** Completes the event after successful scanning. */
    public void complete(boolean quickCheckPassed, boolean fullParsed, int callSitesFound) {
        this.quickCheckPassed = quickCheckPassed;
        this.fullParsed = fullParsed;
        this.callSitesFound = callSitesFound;
        this.skippedReason = null;
        end();
        commit();
    }

    /** Completes the event when scanning is skipped. */
    public void skip(String reason) {
        this.quickCheckPassed = false;
        this.fullParsed = false;
        this.callSitesFound = 0;
        this.skippedReason = reason;
        end();
        commit();
    }
}

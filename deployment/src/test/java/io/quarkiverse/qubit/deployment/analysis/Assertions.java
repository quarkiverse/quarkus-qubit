package io.quarkiverse.qubit.deployment.analysis;

/**
 * Entry point for assertions of different data types. Each method in this class is a static factory for the
 * type-specific assertion objects.
 */
@jakarta.annotation.Generated(value = "assertj-assertions-generator")
public class Assertions {

    /**
     * Creates a new instance of <code>{@link io.quarkiverse.qubit.deployment.analysis.AnalysisOutcomeAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @org.assertj.core.util.CheckReturnValue
    public static io.quarkiverse.qubit.deployment.analysis.AnalysisOutcomeAssert assertThat(
            io.quarkiverse.qubit.deployment.analysis.AnalysisOutcome actual) {
        return new io.quarkiverse.qubit.deployment.analysis.AnalysisOutcomeAssert(actual);
    }

    /**
     * Creates a new instance of <code>{@link io.quarkiverse.qubit.deployment.analysis.MethodScanStateAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @org.assertj.core.util.CheckReturnValue
    public static io.quarkiverse.qubit.deployment.analysis.MethodScanStateAssert assertThat(
            io.quarkiverse.qubit.deployment.analysis.MethodScanState actual) {
        return new io.quarkiverse.qubit.deployment.analysis.MethodScanStateAssert(actual);
    }

    /**
     * Creates a new instance of <code>{@link io.quarkiverse.qubit.deployment.analysis.QueryCharacteristicsAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @org.assertj.core.util.CheckReturnValue
    public static io.quarkiverse.qubit.deployment.analysis.QueryCharacteristicsAssert assertThat(
            io.quarkiverse.qubit.deployment.analysis.QueryCharacteristics actual) {
        return new io.quarkiverse.qubit.deployment.analysis.QueryCharacteristicsAssert(actual);
    }

    /**
     * Creates a new instance of
     * <code>{@link io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContextAssert}</code>.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @org.assertj.core.util.CheckReturnValue
    public static io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContextAssert assertThat(
            io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContext actual) {
        return new io.quarkiverse.qubit.deployment.analysis.instruction.AnalysisContextAssert(actual);
    }

    /**
     * Creates a new <code>{@link Assertions}</code>.
     */
    protected Assertions() {
        // empty
    }
}

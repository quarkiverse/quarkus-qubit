/**
 * JFR custom events for Qubit build-time profiling.
 *
 * <p>
 * Events: {@link QubitPhaseEvent} (phases), {@link QubitAnalysisEvent} (analysis),
 * {@link QubitCodeGenEvent} (codegen), {@link QubitScanEvent} (scanning).
 *
 * <p>
 * Enable: {@code MAVEN_OPTS="-XX:StartFlightRecording=filename=qubit.jfr" mvn package}
 *
 * @since 1.0
 */
@NullMarked
package io.quarkiverse.qubit.deployment.jfr;

import org.jspecify.annotations.NullMarked;

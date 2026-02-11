/**
 * JFR custom events for Qubit build-time profiling.
 *
 * <p>
 * Events: {@link QubitPhaseEvent} (phases), {@link QubitAnalysisEvent} (analysis),
 * {@link QubitCodeGenEvent} (codegen), {@link QubitScanEvent} (scanning).
 *
 * <p>
 * Enable: {@code MAVEN_OPTS="-XX:StartFlightRecording=filename=qubit.jfr" mvn package}
 */
package io.quarkiverse.qubit.deployment.jfr;

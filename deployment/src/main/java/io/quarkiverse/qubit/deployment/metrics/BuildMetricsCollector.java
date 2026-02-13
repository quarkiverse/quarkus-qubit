package io.quarkiverse.qubit.deployment.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe build-time performance metrics for Qubit processing phases.
 *
 * <p>
 * Collects phase timing, granular operations, query type breakdown, cache hit rates,
 * memory usage, thread utilization, and latency histograms. Outputs JSON and flame graph formats.
 */
public class BuildMetricsCollector {

    private final long startTime;
    private final long startHeapUsed;
    private final Map<String, Long> phaseStartTimes = new LinkedHashMap<>();
    private final Map<String, Long> phaseDurations = new LinkedHashMap<>();
    private final AtomicInteger queryCount = new AtomicInteger();

    // GRANULAR TIMING METRICS (in nanoseconds)

    private final AtomicLong bytecodeLoadTimeNanos = new AtomicLong();
    private final AtomicLong asmParsingTimeNanos = new AtomicLong();
    private final AtomicLong instructionAnalysisTimeNanos = new AtomicLong();
    private final AtomicLong codeGenerationTimeNanos = new AtomicLong();
    private final AtomicLong deduplicationCheckTimeNanos = new AtomicLong();
    private final AtomicLong earlyDeduplicationCheckTimeNanos = new AtomicLong();

    // GRANULAR COUNTING METRICS

    private final AtomicLong uniqueClassesLoaded = new AtomicLong();
    private final AtomicLong totalBytecodeLoads = new AtomicLong();
    private final AtomicLong duplicateCount = new AtomicLong();
    private final AtomicLong earlyDeduplicationHits = new AtomicLong();

    // Pre-filter and quick check optimization metrics
    private final AtomicLong classesScanned = new AtomicLong();
    private final AtomicLong jandexPreFilterSkips = new AtomicLong();
    private final AtomicLong quickCheckSkips = new AtomicLong();
    private final AtomicLong quickCheckPasses = new AtomicLong();

    // QUERY TYPE BREAKDOWN METRICS

    /** Query counts by type (SIMPLE, JOIN, GROUP, AGGREGATION). */
    private final ConcurrentHashMap<String, LongAdder> queryTypeCount = new ConcurrentHashMap<>();

    /** Total analysis time by query type in nanoseconds. */
    private final ConcurrentHashMap<String, LongAdder> queryTypeAnalysisTimeNanos = new ConcurrentHashMap<>();

    /** Total code generation time by query type in nanoseconds. */
    private final ConcurrentHashMap<String, LongAdder> queryTypeCodeGenTimeNanos = new ConcurrentHashMap<>();

    // EXPRESSION GENERATION BREAKDOWN METRICS

    /** Expression generation counts by type. */
    private final ConcurrentHashMap<String, LongAdder> expressionTypeCount = new ConcurrentHashMap<>();

    /** Expression generation time by type in nanoseconds. */
    private final ConcurrentHashMap<String, LongAdder> expressionTypeTimeNanos = new ConcurrentHashMap<>();

    // Expression types tracked
    public static final String EXPR_COMPARISON = "COMPARISON";
    public static final String EXPR_STRING = "STRING";
    public static final String EXPR_TEMPORAL = "TEMPORAL";
    public static final String EXPR_ARITHMETIC = "ARITHMETIC";
    public static final String EXPR_BOOLEAN = "BOOLEAN";
    public static final String EXPR_BIG_DECIMAL = "BIG_DECIMAL";
    public static final String EXPR_FIELD_ACCESS = "FIELD_ACCESS";
    public static final String EXPR_METHOD_CALL = "METHOD_CALL";
    public static final String EXPR_SUBQUERY = "SUBQUERY";

    // PER-CLASS ANALYSIS METRICS

    /** Analysis time per class in nanoseconds. */
    private final ConcurrentHashMap<String, LongAdder> perClassAnalysisTimeNanos = new ConcurrentHashMap<>();

    /** Lambda count per class. */
    private final ConcurrentHashMap<String, LongAdder> perClassLambdaCount = new ConcurrentHashMap<>();

    /** Classes where analysis failed. */
    private final Set<String> failedClasses = ConcurrentHashMap.newKeySet();

    // CACHE EFFECTIVENESS METRICS

    private final AtomicLong bytecodeeCacheHits = new AtomicLong();
    private final AtomicLong bytecodeCacheMisses = new AtomicLong();
    private final AtomicLong classNodeCacheHits = new AtomicLong();
    private final AtomicLong classNodeCacheMisses = new AtomicLong();
    private final AtomicLong codeGenCacheHits = new AtomicLong();
    private final AtomicLong codeGenCacheMisses = new AtomicLong();

    // MEMORY USAGE METRICS

    /** Memory snapshots at key phases. */
    private final ConcurrentHashMap<String, Long> memorySnapshots = new ConcurrentHashMap<>();

    // THREAD UTILIZATION METRICS

    /** Unique thread IDs that performed analysis work. */
    private final Set<Long> activeThreadIds = ConcurrentHashMap.newKeySet();

    /** Per-thread work distribution (task counts). */
    private final ConcurrentHashMap<Long, LongAdder> perThreadTaskCount = new ConcurrentHashMap<>();

    /** Per-thread total work time in nanoseconds. */
    private final ConcurrentHashMap<Long, LongAdder> perThreadWorkTimeNanos = new ConcurrentHashMap<>();

    // HISTOGRAM METRICS (latency distributions)

    /** Lambda analysis latencies in microseconds for percentile calculation. */
    private final List<Long> lambdaAnalysisLatenciesMicros = Collections.synchronizedList(new ArrayList<>());

    /** Code generation latencies in microseconds for percentile calculation. */
    private final List<Long> codeGenLatenciesMicros = Collections.synchronizedList(new ArrayList<>());

    /** Maximum latencies to track (prevent memory issues). */
    private static final int MAX_HISTOGRAM_SAMPLES = 10000;

    // ENTITY ENHANCEMENT METRICS

    private final AtomicLong entityEnhancementTimeNanos = new AtomicLong();
    private final AtomicInteger entityClassesEnhanced = new AtomicInteger();
    private final AtomicInteger repositoriesEnhanced = new AtomicInteger();

    // FLAME GRAPH STACKS (for async-profiler compatible output)

    /** Flame graph stacks: stack description -> count/weight. */
    private final ConcurrentHashMap<String, LongAdder> flameGraphStacks = new ConcurrentHashMap<>();

    public BuildMetricsCollector() {
        this.startTime = System.nanoTime();
        this.startHeapUsed = getHeapUsed();
    }

    // PHASE TRACKING

    /** Marks the start of a processing phase. */
    public synchronized void startPhase(String phase) {
        phaseStartTimes.put(phase, System.nanoTime());
        captureMemorySnapshot(phase + "_start");
    }

    /** Marks the end of a processing phase and records duration. */
    public synchronized void endPhase(String phase) {
        Long start = phaseStartTimes.get(phase);
        if (start != null) {
            long durationNanos = System.nanoTime() - start;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            phaseDurations.put(phase, durationMs);
            captureMemorySnapshot(phase + "_end");
        }
    }

    /** Returns duration of a phase in milliseconds. */
    public synchronized long getPhaseDuration(String phase) {
        return phaseDurations.getOrDefault(phase, 0L);
    }

    // QUERY COUNT

    /** Increments the count of processed queries. */
    public void incrementQueryCount() {
        queryCount.incrementAndGet();
    }

    /** Returns total query count. */
    public int getQueryCount() {
        return queryCount.get();
    }

    // GRANULAR TIMING METHODS

    /** Adds time spent loading class bytecode from disk. */
    public void addBytecodeLoadTime(long nanos) {
        bytecodeLoadTimeNanos.addAndGet(nanos);
    }

    /** Adds time spent parsing bytecode with ASM ClassReader. */
    public void addAsmParsingTime(long nanos) {
        asmParsingTimeNanos.addAndGet(nanos);
    }

    /** Adds time spent walking bytecode instructions. */
    public void addInstructionAnalysisTime(long nanos) {
        instructionAnalysisTimeNanos.addAndGet(nanos);
        addHistogramSample(lambdaAnalysisLatenciesMicros, TimeUnit.NANOSECONDS.toMicros(nanos));
    }

    /** Adds time spent generating executor bytecode with Gizmo. */
    public void addCodeGenerationTime(long nanos) {
        codeGenerationTimeNanos.addAndGet(nanos);
        addHistogramSample(codeGenLatenciesMicros, TimeUnit.NANOSECONDS.toMicros(nanos));
    }

    /** Adds time spent checking for duplicates. */
    public void addDeduplicationCheckTime(long nanos) {
        deduplicationCheckTimeNanos.addAndGet(nanos);
    }

    /** Adds time spent on early deduplication checks. */
    public void addEarlyDeduplicationCheckTime(long nanos) {
        earlyDeduplicationCheckTimeNanos.addAndGet(nanos);
    }

    // GRANULAR COUNTING METHODS

    /** Increments the count of unique classes loaded. */
    public void incrementUniqueClassesLoaded() {
        uniqueClassesLoaded.incrementAndGet();
    }

    /** Increments the total number of bytecode load operations. */
    public void incrementTotalBytecodeLoads() {
        totalBytecodeLoads.incrementAndGet();
    }

    /** Increments the count of deduplicated lambdas. */
    public void incrementDuplicateCount() {
        duplicateCount.incrementAndGet();
    }

    /** Increments the count of early deduplication hits (analysis skipped). */
    public void incrementEarlyDeduplicationHits() {
        earlyDeduplicationHits.incrementAndGet();
    }

    /** Records a class scan attempt. */
    public void incrementClassesScanned() {
        classesScanned.incrementAndGet();
    }

    /** Records when Jandex pre-filter skips a class (annotation, pure interface, etc.). */
    public void incrementJandexPreFilterSkips() {
        jandexPreFilterSkips.incrementAndGet();
    }

    /** Records when quick check skips a class (no CONSTANT_InvokeDynamic in constant pool). */
    public void incrementQuickCheckSkips() {
        quickCheckSkips.incrementAndGet();
    }

    /** Records when quick check passes (invokedynamic opcode found). */
    public void incrementQuickCheckPasses() {
        quickCheckPasses.incrementAndGet();
    }

    // QUERY TYPE BREAKDOWN

    /** Records a query of the given type. */
    public void recordQueryType(String queryType) {
        queryTypeCount.computeIfAbsent(queryType, _ -> new LongAdder()).increment();
    }

    /** Records analysis time for a specific query type. */
    public void addQueryTypeAnalysisTime(String queryType, long nanos) {
        queryTypeAnalysisTimeNanos.computeIfAbsent(queryType, _ -> new LongAdder()).add(nanos);
        addFlameGraphStack("qubit;analysis;" + queryType.toLowerCase(), nanos);
    }

    /** Records code generation time for a specific query type. */
    public void addQueryTypeCodeGenTime(String queryType, long nanos) {
        queryTypeCodeGenTimeNanos.computeIfAbsent(queryType, _ -> new LongAdder()).add(nanos);
        addFlameGraphStack("qubit;codegen;" + queryType.toLowerCase(), nanos);
    }

    // EXPRESSION GENERATION BREAKDOWN

    /** Records expression generation of the given type. */
    public void recordExpressionType(String expressionType) {
        expressionTypeCount.computeIfAbsent(expressionType, _ -> new LongAdder()).increment();
    }

    /** Records expression generation time for a specific type. */
    public void addExpressionTypeTime(String expressionType, long nanos) {
        expressionTypeTimeNanos.computeIfAbsent(expressionType, _ -> new LongAdder()).add(nanos);
        addFlameGraphStack("qubit;expression;" + expressionType.toLowerCase(), nanos);
    }

    // PER-CLASS ANALYSIS

    /** Records analysis time for a specific class. */
    public void addClassAnalysisTime(String className, long nanos) {
        perClassAnalysisTimeNanos.computeIfAbsent(className, _ -> new LongAdder()).add(nanos);
        recordThreadWork(nanos);
    }

    /** Records lambda count for a specific class. */
    public void addClassLambdaCount(String className, int count) {
        perClassLambdaCount.computeIfAbsent(className, _ -> new LongAdder()).add(count);
    }

    /** Records a failed class analysis. */
    public void recordFailedClass(String className) {
        failedClasses.add(className);
    }

    // CACHE EFFECTIVENESS

    /** Records a bytecode cache hit. */
    public void recordBytecodeCacheHit() {
        bytecodeeCacheHits.incrementAndGet();
    }

    /** Records a bytecode cache miss. */
    public void recordBytecodeCacheMiss() {
        bytecodeCacheMisses.incrementAndGet();
    }

    /** Records a ClassNode cache hit. */
    public void recordClassNodeCacheHit() {
        classNodeCacheHits.incrementAndGet();
    }

    /** Records a ClassNode cache miss. */
    public void recordClassNodeCacheMiss() {
        classNodeCacheMisses.incrementAndGet();
    }

    /** Returns bytecode cache hit rate (0.0 to 1.0). */
    public double getBytecodeCacheHitRate() {
        long total = bytecodeeCacheHits.get() + bytecodeCacheMisses.get();
        return total > 0 ? (double) bytecodeeCacheHits.get() / total : 0.0;
    }

    /** Returns ClassNode cache hit rate (0.0 to 1.0). */
    public double getClassNodeCacheHitRate() {
        long total = classNodeCacheHits.get() + classNodeCacheMisses.get();
        return total > 0 ? (double) classNodeCacheHits.get() / total : 0.0;
    }

    /** Increments code generation cache hit count. */
    public void incrementCodeGenCacheHits() {
        codeGenCacheHits.incrementAndGet();
    }

    /** Increments code generation cache miss count. */
    public void incrementCodeGenCacheMisses() {
        codeGenCacheMisses.incrementAndGet();
    }

    /** Returns code generation cache hit rate (0.0 to 1.0). */
    public double getCodeGenCacheHitRate() {
        long total = codeGenCacheHits.get() + codeGenCacheMisses.get();
        return total > 0 ? (double) codeGenCacheHits.get() / total : 0.0;
    }

    /** Returns code generation cache hit count. */
    public long getCodeGenCacheHits() {
        return codeGenCacheHits.get();
    }

    /** Returns code generation cache miss count. */
    public long getCodeGenCacheMisses() {
        return codeGenCacheMisses.get();
    }

    // MEMORY USAGE

    /** Captures a memory snapshot at a named point. */
    public void captureMemorySnapshot(String name) {
        memorySnapshots.put(name, getHeapUsed());
    }

    /** Returns current heap memory used in bytes. */
    private long getHeapUsed() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // THREAD UTILIZATION

    /** Records work performed by the current thread. */
    public void recordThreadWork(long nanos) {
        long threadId = Thread.currentThread().threadId();
        activeThreadIds.add(threadId);
        perThreadTaskCount.computeIfAbsent(threadId, _ -> new LongAdder()).increment();
        perThreadWorkTimeNanos.computeIfAbsent(threadId, _ -> new LongAdder()).add(nanos);
    }

    /** Returns the number of unique threads that performed analysis work. */
    public int getActiveThreadCount() {
        return activeThreadIds.size();
    }

    // HISTOGRAM SUPPORT

    /** Adds a sample to a histogram (bounded to prevent memory issues). */
    private void addHistogramSample(List<Long> histogram, long value) {
        if (histogram.size() < MAX_HISTOGRAM_SAMPLES) {
            histogram.add(value);
        }
    }

    /** Calculates percentile from a sorted list. */
    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty())
            return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }

    // ENTITY ENHANCEMENT METRICS

    /** Adds time spent on entity enhancement. */
    public void addEntityEnhancementTime(long nanos) {
        entityEnhancementTimeNanos.addAndGet(nanos);
        addFlameGraphStack("qubit;enhancement;entity", nanos);
    }

    /** Increments count of enhanced entity classes. */
    public void incrementEntityClassesEnhanced() {
        entityClassesEnhanced.incrementAndGet();
    }

    /** Increments count of enhanced repositories. */
    public void incrementRepositoriesEnhanced() {
        repositoriesEnhanced.incrementAndGet();
    }

    // FLAME GRAPH SUPPORT

    /** Adds to flame graph stack trace (collapsed format). */
    public void addFlameGraphStack(String stack, long nanos) {
        // Convert to microseconds for flame graph weight
        long micros = TimeUnit.NANOSECONDS.toMicros(nanos);
        if (micros > 0) {
            flameGraphStacks.computeIfAbsent(stack, _ -> new LongAdder()).add(micros);
        }
    }

    // GETTER METHODS (primarily for testing)

    public long getTotalBytecodeLoads() {
        return totalBytecodeLoads.get();
    }

    public long getUniqueClassesLoaded() {
        return uniqueClassesLoaded.get();
    }

    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    public long getBytecodeLoadTimeNanos() {
        return bytecodeLoadTimeNanos.get();
    }

    public long getEarlyDeduplicationHits() {
        return earlyDeduplicationHits.get();
    }

    public long getEarlyDeduplicationCheckTimeNanos() {
        return earlyDeduplicationCheckTimeNanos.get();
    }

    public long getCodeGenerationTimeNanos() {
        return codeGenerationTimeNanos.get();
    }

    public long getInstructionAnalysisTimeNanos() {
        return instructionAnalysisTimeNanos.get();
    }

    public int getEntityClassesEnhanced() {
        return entityClassesEnhanced.get();
    }

    public int getRepositoriesEnhanced() {
        return repositoriesEnhanced.get();
    }

    public long getClassesScanned() {
        return classesScanned.get();
    }

    public long getJandexPreFilterSkips() {
        return jandexPreFilterSkips.get();
    }

    public long getQuickCheckSkips() {
        return quickCheckSkips.get();
    }

    public long getQuickCheckPasses() {
        return quickCheckPasses.get();
    }

    /** Returns quick check skip rate (0.0 to 1.0). */
    public double getQuickCheckSkipRate() {
        long total = classesScanned.get();
        return total > 0 ? (double) quickCheckSkips.get() / total : 0.0;
    }

    // REPORT GENERATION

    /** Writes comprehensive metrics report to JSON file. */
    public synchronized void writeReport(Path outputPath) throws IOException {
        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        long endHeapUsed = getHeapUsed();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"total_ms\": ").append(totalMs).append(",\n");

        writeMemorySection(json, endHeapUsed);
        writePhasesSection(json);
        json.append("  \"query_count\": ").append(queryCount.get()).append(",\n");
        writeGranularSection(json);
        writeQueryTypesSection(json);
        writeExpressionTypesSection(json);
        writeCacheSection(json);
        writeThreadsSection(json);
        writeHistogramsSection(json);
        writeEnhancementSection(json);
        writeTopClassesSection(json);
        writeFailedClassesSection(json);

        json.append("}\n");

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, json.toString());
    }

    private void writeMemorySection(StringBuilder json, long endHeapUsed) {
        json.append("  \"memory\": {\n");
        json.append("    \"start_heap_bytes\": ").append(startHeapUsed).append(",\n");
        json.append("    \"end_heap_bytes\": ").append(endHeapUsed).append(",\n");
        json.append("    \"delta_bytes\": ").append(endHeapUsed - startHeapUsed).append("\n");
        json.append("  },\n");
    }

    private void writePhasesSection(StringBuilder json) {
        json.append("  \"phases\": {\n");
        boolean first = true;
        for (Map.Entry<String, Long> entry : phaseDurations.entrySet()) {
            if (!first)
                json.append(",\n");
            json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            first = false;
        }
        json.append("\n  },\n");
    }

    private void writeGranularSection(StringBuilder json) {
        json.append("  \"granular\": {\n");
        json.append("    \"bytecode_load_time_nanos\": ").append(bytecodeLoadTimeNanos.get()).append(",\n");
        json.append("    \"asm_parsing_time_nanos\": ").append(asmParsingTimeNanos.get()).append(",\n");
        json.append("    \"instruction_analysis_time_nanos\": ").append(instructionAnalysisTimeNanos.get()).append(",\n");
        json.append("    \"code_generation_time_nanos\": ").append(codeGenerationTimeNanos.get()).append(",\n");
        json.append("    \"deduplication_check_time_nanos\": ").append(deduplicationCheckTimeNanos.get()).append(",\n");
        json.append("    \"unique_classes_loaded\": ").append(uniqueClassesLoaded.get()).append(",\n");
        json.append("    \"total_bytecode_loads\": ").append(totalBytecodeLoads.get()).append(",\n");
        json.append("    \"duplicate_count\": ").append(duplicateCount.get()).append(",\n");
        json.append("    \"early_deduplication_hits\": ").append(earlyDeduplicationHits.get()).append(",\n");
        json.append("    \"early_deduplication_check_time_nanos\": ").append(earlyDeduplicationCheckTimeNanos.get())
                .append(",\n");
        // Pre-filter and quick check optimization metrics
        json.append("    \"classes_scanned\": ").append(classesScanned.get()).append(",\n");
        json.append("    \"jandex_pre_filter_skips\": ").append(jandexPreFilterSkips.get()).append(",\n");
        json.append("    \"quick_check_skips\": ").append(quickCheckSkips.get()).append(",\n");
        json.append("    \"quick_check_passes\": ").append(quickCheckPasses.get()).append(",\n");
        double skipRate = classesScanned.get() > 0 ? (double) quickCheckSkips.get() / classesScanned.get() : 0.0;
        json.append("    \"quick_check_skip_rate\": ").append(String.format("%.4f", skipRate)).append("\n");
        json.append("  },\n");
    }

    private void writeQueryTypesSection(StringBuilder json) {
        json.append("  \"query_types\": {\n");
        boolean first = true;
        for (Map.Entry<String, LongAdder> entry : queryTypeCount.entrySet()) {
            if (!first)
                json.append(",\n");
            String type = entry.getKey();
            json.append("    \"").append(type).append("\": {\n");
            json.append("      \"count\": ").append(entry.getValue().sum()).append(",\n");
            LongAdder analysisTime = queryTypeAnalysisTimeNanos.get(type);
            json.append("      \"analysis_time_nanos\": ").append(analysisTime != null ? analysisTime.sum() : 0).append(",\n");
            LongAdder codeGenTime = queryTypeCodeGenTimeNanos.get(type);
            json.append("      \"codegen_time_nanos\": ").append(codeGenTime != null ? codeGenTime.sum() : 0).append("\n");
            json.append("    }");
            first = false;
        }
        json.append("\n  },\n");
    }

    private void writeExpressionTypesSection(StringBuilder json) {
        json.append("  \"expression_types\": {\n");
        boolean first = true;
        for (Map.Entry<String, LongAdder> entry : expressionTypeCount.entrySet()) {
            if (!first)
                json.append(",\n");
            String type = entry.getKey();
            json.append("    \"").append(type).append("\": {\n");
            json.append("      \"count\": ").append(entry.getValue().sum()).append(",\n");
            LongAdder exprTime = expressionTypeTimeNanos.get(type);
            json.append("      \"time_nanos\": ").append(exprTime != null ? exprTime.sum() : 0).append("\n");
            json.append("    }");
            first = false;
        }
        json.append("\n  },\n");
    }

    private void writeCacheSection(StringBuilder json) {
        json.append("  \"cache\": {\n");
        json.append("    \"bytecode_hits\": ").append(bytecodeeCacheHits.get()).append(",\n");
        json.append("    \"bytecode_misses\": ").append(bytecodeCacheMisses.get()).append(",\n");
        json.append("    \"bytecode_hit_rate\": ").append(String.format("%.4f", getBytecodeCacheHitRate())).append(",\n");
        json.append("    \"classnode_hits\": ").append(classNodeCacheHits.get()).append(",\n");
        json.append("    \"classnode_misses\": ").append(classNodeCacheMisses.get()).append(",\n");
        json.append("    \"classnode_hit_rate\": ").append(String.format("%.4f", getClassNodeCacheHitRate())).append(",\n");
        json.append("    \"codegen_hits\": ").append(codeGenCacheHits.get()).append(",\n");
        json.append("    \"codegen_misses\": ").append(codeGenCacheMisses.get()).append(",\n");
        json.append("    \"codegen_hit_rate\": ").append(String.format("%.4f", getCodeGenCacheHitRate())).append("\n");
        json.append("  },\n");
    }

    private void writeThreadsSection(StringBuilder json) {
        json.append("  \"threads\": {\n");
        json.append("    \"active_count\": ").append(activeThreadIds.size()).append(",\n");
        json.append("    \"distribution\": {\n");
        boolean first = true;
        for (Map.Entry<Long, LongAdder> entry : perThreadTaskCount.entrySet()) {
            if (!first)
                json.append(",\n");
            Long threadId = entry.getKey();
            json.append("      \"").append(threadId).append("\": {\n");
            json.append("        \"tasks\": ").append(entry.getValue().sum()).append(",\n");
            LongAdder workTime = perThreadWorkTimeNanos.get(threadId);
            json.append("        \"work_time_nanos\": ").append(workTime != null ? workTime.sum() : 0).append("\n");
            json.append("      }");
            first = false;
        }
        json.append("\n    }\n");
        json.append("  },\n");
    }

    private void writeHistogramsSection(StringBuilder json) {
        json.append("  \"histograms\": {\n");
        writeHistogram(json, "lambda_analysis_micros", lambdaAnalysisLatenciesMicros);
        json.append(",\n");
        writeHistogram(json, "code_gen_micros", codeGenLatenciesMicros);
        json.append("\n  },\n");
    }

    private void writeEnhancementSection(StringBuilder json) {
        json.append("  \"enhancement\": {\n");
        json.append("    \"entity_classes_enhanced\": ").append(entityClassesEnhanced.get()).append(",\n");
        json.append("    \"repositories_enhanced\": ").append(repositoriesEnhanced.get()).append(",\n");
        json.append("    \"total_time_nanos\": ").append(entityEnhancementTimeNanos.get()).append("\n");
        json.append("  },\n");
    }

    private void writeTopClassesSection(StringBuilder json) {
        json.append("  \"top_classes_by_time\": [\n");
        List<Map.Entry<String, Long>> topClasses = perClassAnalysisTimeNanos.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().sum()))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .toList();
        boolean first = true;
        for (Map.Entry<String, Long> entry : topClasses) {
            if (!first)
                json.append(",\n");
            json.append("    {\"class\": \"").append(entry.getKey())
                    .append("\", \"time_nanos\": ").append(entry.getValue())
                    .append(", \"lambdas\": ");
            LongAdder count = perClassLambdaCount.get(entry.getKey());
            json.append(count != null ? count.sum() : 0).append("}");
            first = false;
        }
        json.append("\n  ],\n");
    }

    private void writeFailedClassesSection(StringBuilder json) {
        json.append("  \"failed_classes\": [");
        boolean first = true;
        for (String className : failedClasses) {
            if (!first)
                json.append(", ");
            json.append("\"").append(className).append("\"");
            first = false;
        }
        json.append("]\n");
    }

    /** Writes histogram section with percentiles. */
    private void writeHistogram(StringBuilder json, String name, List<Long> samples) {
        List<Long> sorted = samples.stream().sorted().toList();
        long sum = sorted.stream().mapToLong(Long::longValue).sum();
        double avg = sorted.isEmpty() ? 0 : (double) sum / sorted.size();

        json.append("    \"").append(name).append("\": {\n");
        json.append("      \"count\": ").append(sorted.size()).append(",\n");
        json.append("      \"sum\": ").append(sum).append(",\n");
        json.append("      \"avg\": ").append(String.format("%.2f", avg)).append(",\n");
        json.append("      \"min\": ").append(sorted.isEmpty() ? 0 : sorted.getFirst()).append(",\n");
        json.append("      \"max\": ").append(sorted.isEmpty() ? 0 : sorted.getLast()).append(",\n");
        json.append("      \"p50\": ").append(percentile(sorted, 50)).append(",\n");
        json.append("      \"p90\": ").append(percentile(sorted, 90)).append(",\n");
        json.append("      \"p95\": ").append(percentile(sorted, 95)).append(",\n");
        json.append("      \"p99\": ").append(percentile(sorted, 99)).append("\n");
        json.append("    }");
    }

    /**
     * Writes flame graph compatible output (collapsed stack format).
     * Can be visualized with async-profiler's flamegraph.pl or speedscope.app
     */
    public synchronized void writeFlameGraph(Path outputPath) throws IOException {
        StringBuilder collapsed = new StringBuilder();

        // Add phase stacks
        for (Map.Entry<String, Long> entry : phaseDurations.entrySet()) {
            collapsed.append("qubit;").append(entry.getKey()).append(" ")
                    .append(TimeUnit.MILLISECONDS.toMicros(entry.getValue())).append("\n");
        }

        // Add granular operation stacks
        addFlameGraphLine(collapsed, "qubit;bytecode_load", bytecodeLoadTimeNanos.get());
        addFlameGraphLine(collapsed, "qubit;asm_parsing", asmParsingTimeNanos.get());
        addFlameGraphLine(collapsed, "qubit;instruction_analysis", instructionAnalysisTimeNanos.get());
        addFlameGraphLine(collapsed, "qubit;code_generation", codeGenerationTimeNanos.get());
        addFlameGraphLine(collapsed, "qubit;deduplication", deduplicationCheckTimeNanos.get());
        addFlameGraphLine(collapsed, "qubit;enhancement", entityEnhancementTimeNanos.get());

        // Add accumulated stacks from processing
        for (Map.Entry<String, LongAdder> entry : flameGraphStacks.entrySet()) {
            collapsed.append(entry.getKey()).append(" ").append(entry.getValue().sum()).append("\n");
        }

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, collapsed.toString());
    }

    private void addFlameGraphLine(StringBuilder sb, String stack, long nanos) {
        long micros = TimeUnit.NANOSECONDS.toMicros(nanos);
        if (micros > 0) {
            sb.append(stack).append(" ").append(micros).append("\n");
        }
    }
}

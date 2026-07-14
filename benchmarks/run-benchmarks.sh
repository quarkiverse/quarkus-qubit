#!/bin/bash
# Runs benchmarks N times and computes statistics (median, stddev)
# Usage: ./run-benchmarks.sh [runs] [profile]
# Example: ./run-benchmarks.sh 5 h2
#          ./run-benchmarks.sh 5 postgresql

RUNS=${1:-5}
PROFILE=${2:-h2}
DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="$DIR/target/multi-run"
mkdir -p "$RESULTS_DIR"

echo "=== Running $RUNS benchmark iterations on $PROFILE ==="

for i in $(seq 1 $RUNS); do
    echo "--- Run $i/$RUNS ---"
    mvn verify -P$PROFILE -f "$DIR/pom.xml" -q 2>/dev/null
    for f in "$DIR/target/"*-results.json; do
        base=$(basename "$f" .json)
        cp "$f" "$RESULTS_DIR/${base}-run${i}.json"
    done
done

echo ""
echo "=== Statistical Summary ==="

python3 -c "
import json, os, sys, math
from collections import defaultdict

results_dir = '$RESULTS_DIR'
runs = $RUNS
metrics = defaultdict(list)

for i in range(1, runs + 1):
    for prefix in ['throughput', 'latency', 'allocation']:
        path = os.path.join(results_dir, f'{prefix}-results-run{i}.json')
        if os.path.exists(path):
            with open(path) as f:
                for entry in json.load(f):
                    key = f\"{entry['testName']}/{entry['metric']}\"
                    metrics[key].append(entry['value'])

print(f\"{'Metric':<35} {'Median':>12} {'StdDev':>12} {'Min':>12} {'Max':>12}  Unit\")
print('-' * 95)

for key in sorted(metrics.keys()):
    values = sorted(metrics[key])
    n = len(values)
    median = values[n // 2]
    mean = sum(values) / n
    stddev = math.sqrt(sum((v - mean) ** 2 for v in values) / n) if n > 1 else 0
    # Determine unit from first file
    unit = ''
    for prefix in ['throughput', 'latency', 'allocation']:
        path = os.path.join(results_dir, f'{prefix}-results-run1.json')
        if os.path.exists(path):
            with open(path) as f:
                for entry in json.load(f):
                    if f\"{entry['testName']}/{entry['metric']}\" == key:
                        unit = entry['unit']
                        break
            if unit:
                break
    print(f'{key:<35} {median:>12,.1f} {stddev:>12,.1f} {values[0]:>12,.1f} {values[-1]:>12,.1f}  {unit}')
" 2>/dev/null || echo "Python3 not available for statistics"

echo ""
echo "Raw results saved to: $RESULTS_DIR/"

#!/usr/bin/env bash
# Builds the project on JDK 28, checks that the test data is reproducible,
# compares the behaviour of both parsers with expected.txt and then, depending
# on the mode, measures start-up, retained memory and runs JMH.
#
#   ./run.sh          # everything, JMH takes about 5 minutes
#   ./run.sh quick    # data and behaviour only, no timings
#   ./run.sh ci       # quick + one start-up run + a short JMH smoke run
#
# Exit code 1 if the data or the behaviour differs from the repository.
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-full}"

JAVA_VERSION="$(java -version 2>&1 | head -1)"
if [[ "$JAVA_VERSION" != *'"28'* ]]; then
  echo "JDK 28 is required, got: $JAVA_VERSION" >&2
  exit 2
fi
if ! mvn -v 2>/dev/null | grep -q 'Java version: 28'; then
  echo "Maven must run on JDK 28: set JAVA_HOME" >&2
  exit 2
fi

mvn -q -B package
# target/jackson holds exactly the runtime dependencies of the demo classes
CP="target/classes:target/jackson/*"
# hide only the "Using incubator modules" warning, keep real errors visible
run() {
  java --add-modules jdk.incubator.json -cp "$CP" "$@" \
    2> >(grep -v 'Using incubator modules' >&2)
}
jmh() {
  if ! java -jar target/benchmarks.jar "$@" > target/jmh.log 2>&1; then
    tail -30 target/jmh.log >&2
    exit 1
  fi
}

echo "== data =="
run demo.DataGen target/regen target/data
for f in forecast.json threaddump-medium.json; do
  cmp "data/$f" "target/regen/$f"
done
echo "data/ is reproducible"

echo "== behaviour =="
{ run demo.Compare; echo; run demo.EdgeCases; } > target/behaviour.txt
if diff -u expected.txt target/behaviour.txt; then
  echo "behaviour matches expected.txt"
else
  echo "behaviour differs from expected.txt" >&2
  exit 1
fi

case "$MODE" in
  quick)
    ;;
  ci)
    echo "== smoke run of start-up and benchmarks, numbers are not checked =="
    run demo.Footprint 1
    jmh -f 1 -wi 0 -i 1 -r 200ms
    echo "benchmarks run"
    ;;
  *)
    echo "== footprint, machine-dependent =="
    run demo.Footprint
    run -Xmx1g demo.Retained
    echo "== JMH, machine-dependent =="
    jmh -prof gc -rf json -rff target/jmh.json
    run demo.Report target/jmh.json
    ;;
esac

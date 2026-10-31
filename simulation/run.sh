#!/usr/bin/env bash
# Run the one-week usage simulation. Compiles the desktop test sources once, then executes the
# simulation main with a plain JVM (no Gradle module, no product-code changes).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
cd "$root"

./gradlew --quiet --no-configuration-cache \
    -I "$here/classpath.init.gradle" \
    :composeApp:simulationClasspath \
    :composeApp:desktopTestClasses

classpath="$(cat composeApp/build/simulation-classpath.txt)"
exec java -cp "$classpath" buddy.app.sim.WeeklySimulationKt "$@"

#!/usr/bin/env bash
set -euo pipefail

GRADLEW="./gradlew"
if [ ! -f "$GRADLEW" ]; then
  echo "gradlew not found in $(pwd)" >&2
  exit 1
fi
if [ ! -x "$GRADLEW" ]; then
  chmod +x "$GRADLEW" || true
fi

TASKS=(${MODCOMPILER_GRADLE_TASKS:-build})
BASE_ARGS=("${TASKS[@]}" "--stacktrace" "--no-daemon" "--build-cache" "-Dorg.gradle.caching=true" "-Dorg.gradle.parallel=true")

EXCLUDES=()
if [ "${MODCOMPILER_SKIP_TESTS:-1}" = "1" ]; then
  EXCLUDES+=("test" "check")
fi
for task in "${EXCLUDES[@]}"; do
  BASE_ARGS+=("-x" "$task")
done

FAST_MODE="${MODCOMPILER_FAST_BUILD:-1}"
FAST_ONLY="${MODCOMPILER_FAST_ONLY:-0}"
SKIP_DOWNLOADS="${MODCOMPILER_SKIP_DOWNLOADS:-1}"

run_gradle() {
  "$GRADLEW" "${BASE_ARGS[@]}" "$@"
}

if [ "$FAST_MODE" = "1" ] && [ "$SKIP_DOWNLOADS" = "1" ]; then
  init_script="$(mktemp "${TMPDIR:-/tmp}/modcompiler-gradle-init.XXXXXX.gradle")"
  cat > "$init_script" <<'EOF'
// Disable heavy download tasks when caches are already populated.
// If required artifacts are missing, the build may fail and should be retried without this init script.
gradle.taskGraph.whenReady { graph ->
  graph.allTasks.each { t ->
    def name = t.name.toLowerCase()
    if (name.contains("download") || name.contains("cacheversion") || name.contains("extractnatives")) {
      t.enabled = false
    }
  }
}
EOF
  set +e
  run_gradle "--offline" "-I" "$init_script"
  status=$?
  set -e
  rm -f "$init_script" || true

  if [ $status -eq 0 ]; then
    exit 0
  fi

  if [ "$FAST_ONLY" = "1" ]; then
    exit $status
  fi

  echo "Fast build failed (exit $status). Retrying full build." >&2
fi

run_gradle

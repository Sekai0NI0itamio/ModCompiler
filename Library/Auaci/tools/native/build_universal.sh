#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="${ROOT_DIR}/tools/native"
OUT_DIR="${ROOT_DIR}/bin"

mkdir -p "${OUT_DIR}"

CLANG_BIN="${CLANG_BIN:-clang}"

build() {
  local name="$1"
  shift
  local src="${SRC_DIR}/${name}.m"
  local out="${OUT_DIR}/${name}"

  if [[ ! -f "${src}" ]]; then
    echo "Missing source: ${src}" >&2
    exit 1
  fi

  echo "Building ${name} -> ${out}"
  "${CLANG_BIN}" \
    -fobjc-arc \
    -arch x86_64 \
    -arch arm64 \
    "${src}" \
    -o "${out}" \
    "$@"
}

# Cocoa covers Foundation + AppKit for most helpers.
build "clipgrab" -framework Cocoa
build "copy_files_to_clipboard" -framework Cocoa
build "filedroplistener" -framework Cocoa -framework UniformTypeIdentifiers
build "open_terminal" -framework Cocoa
build "process_paste_files" -framework Cocoa
build "read_file_content" -framework Foundation
build "reveal_in_finder" -framework Cocoa

echo "Done. Verify with: file ${OUT_DIR}/*"

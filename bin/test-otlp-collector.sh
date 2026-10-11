#!/usr/bin/env bash
set -euo pipefail

_task_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
_collector_version=0.162.0
if [[ -n "${OTELCOL_BIN:-}" ]]; then
  _collector_binary=$OTELCOL_BIN
else
  case $(uname -s) in
    Linux) _collector_os=linux ;;
    Darwin) _collector_os=darwin ;;
    *) echo 'Set OTELCOL_BIN to a local otelcol-contrib executable on this platform.' >&2; exit 1 ;;
  esac
  case $(uname -m) in
    aarch64|arm64) _collector_arch=arm64 ;;
    x86_64) _collector_arch=amd64 ;;
    *) echo 'Set OTELCOL_BIN to a local otelcol-contrib executable on this architecture.' >&2; exit 1 ;;
  esac
  _collector_cache="${XDG_CACHE_HOME:-$HOME/.cache}/rainbowgum/otelcol/$_collector_version/${_collector_os}_${_collector_arch}"
  _collector_binary="$_collector_cache/otelcol-contrib"
  if [[ ! -x "$_collector_binary" ]]; then
    mkdir -p "$_collector_cache"
    _collector_download=$(mktemp -d "$_collector_cache/download.XXXXXX")
    trap 'rm -rf -- "$_collector_download"' EXIT
    _collector_archive="otelcol-contrib_${_collector_version}_${_collector_os}_${_collector_arch}.tar.gz"
    _collector_url="https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v$_collector_version/$_collector_archive"
    curl --fail --location --retry 2 --connect-timeout 15 "$_collector_url" -o "$_collector_download/archive.tar.gz"
    curl --fail --location --retry 2 --connect-timeout 15 "$_collector_url.sha256" -o "$_collector_download/checksum"
    _collector_expected=$(awk '{print $1; exit}' "$_collector_download/checksum")
    if command -v sha256sum >/dev/null; then
      _collector_actual=$(sha256sum "$_collector_download/archive.tar.gz" | awk '{print $1}')
    else
      _collector_actual=$(shasum -a 256 "$_collector_download/archive.tar.gz" | awk '{print $1}')
    fi
    [[ "$_collector_expected" == "$_collector_actual" ]] || { echo 'Collector checksum mismatch.' >&2; exit 1; }
    tar -xzf "$_collector_download/archive.tar.gz" -C "$_collector_download" otelcol-contrib
    mv -- "$_collector_download/otelcol-contrib" "$_collector_binary"
    rm -rf -- "$_collector_download"
    trap - EXIT
  fi
fi
_collector_binary=$(cd -- "$(dirname -- "$_collector_binary")" && pwd)/$(basename -- "$_collector_binary")
cd -- "$_task_root"
"$_task_root/mvnw" -f "$_task_root/test/rainbowgum-test-otlp-collector/pom.xml" \
  -Dotelcol.binary="$_collector_binary" verify "$@"

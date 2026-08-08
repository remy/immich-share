#!/usr/bin/env bash
#
# Installs the Android SDK packages this project builds against, and writes
# local.properties so Gradle can find them.
#
# Idempotent: re-running is cheap and only fetches what is missing.
#
#   ./scripts/setup-android-sdk.sh
#
# Honours ANDROID_HOME / ANDROID_SDK_ROOT if already set.

set -euo pipefail

readonly SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
readonly CMDLINE_TOOLS_VERSION="15859902"
readonly REPO="https://dl.google.com/android/repository"

# Keep in step with gradle/libs.versions.toml.
readonly PACKAGES=(
  "platforms;android-37.1"
  "build-tools;37.0.0"
  "platform-tools"
)

repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

install_cmdline_tools() {
  local sdkmanager="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  if [[ -x "$sdkmanager" ]]; then
    echo "==> cmdline-tools already present"
    return
  fi

  echo "==> Installing Android cmdline-tools into $SDK_ROOT"
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN

  curl -sSLf -o "$tmp/cmdline-tools.zip" \
    "$REPO/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

  mkdir -p "$SDK_ROOT/cmdline-tools"
  unzip -q -o "$tmp/cmdline-tools.zip" -d "$SDK_ROOT/cmdline-tools"

  # The zip unpacks to cmdline-tools/; sdkmanager requires it at latest/.
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
}

install_packages() {
  local sdkmanager="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

  # `yes |` takes SIGPIPE once sdkmanager stops reading, which under `pipefail`
  # would fail the pipeline even on a clean install. Feed a fixed number of
  # acceptances from a file instead, and check sdkmanager's own status.
  local accept
  accept="$(mktemp)"
  printf 'y\n%.0s' {1..100} > "$accept"

  echo "==> Accepting SDK licences"
  "$sdkmanager" --licenses < "$accept" >/dev/null 2>&1 || true

  echo "==> Installing: ${PACKAGES[*]}"
  "$sdkmanager" --install "${PACKAGES[@]}" < "$accept" >/dev/null

  rm -f "$accept"
}

write_local_properties() {
  local file
  file="$(repo_root)/local.properties"
  # Gitignored: this records a machine-specific path, not project config.
  echo "sdk.dir=$SDK_ROOT" > "$file"
  echo "==> Wrote $file"
}

main() {
  install_cmdline_tools
  install_packages
  write_local_properties
  echo "==> Android SDK ready at $SDK_ROOT"
  echo "    Build with: ./gradlew assembleDebug"
}

main "$@"

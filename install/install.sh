#!/bin/sh
# Installs the spool command: curl -fsSL https://spoolframework.dev/install.sh | sh
#
#   SPOOL_HOME            where to install it, ~/.spool by default
#   SPOOL_NO_MODIFY_PATH  set it to leave the PATH alone
#   SPOOL_INSTALL_JRE     yes or no, to answer whether to download a Java runtime without being asked
#   SPOOL_DOWNLOAD_URL    where spool.zip and spool.zip.sha256 are, the latest release by default
set -eu

SPOOL_HOME=${SPOOL_HOME:-"$HOME/.spool"}
SPOOL_DOWNLOAD_URL=${SPOOL_DOWNLOAD_URL:-https://github.com/Spool-FRAMEWORK/cli/releases/latest/download}
REQUIRED_JAVA=21
ADOPTIUM=https://api.adoptium.net/v3/binary/latest/$REQUIRED_JAVA/ga

say() { printf '%s\n' "$*"; }
fail() { printf 'spool: %s\n' "$*" >&2; exit 1; }
needs() { command -v "$1" >/dev/null 2>&1 || fail "$1 is needed to install spool: install it and run this again"; }

download() {
    curl -fsSL "$1" -o "$2" || fail "could not download $1"
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1
    else shasum -a 256 "$1" | cut -d ' ' -f 1
    fi
}

check_sha256() {
    expected=$(cut -d ' ' -f 1 < "$2")
    [ "$(sha256_of "$1")" = "$expected" ] || fail "spool.zip does not match its SHA-256: nothing was installed"
}

unpack() {
    rm -rf "$1/unpacked"
    unzip -q "$1/spool.zip" -d "$1/unpacked"
    mkdir -p "$SPOOL_HOME"
    rm -rf "$SPOOL_HOME/bin" "$SPOOL_HOME/lib"
    mv "$1/unpacked/bin" "$1/unpacked/lib" "$SPOOL_HOME/"
    chmod +x "$SPOOL_HOME/bin/spool"
}

path_line() {
    printf 'export PATH="%s/bin:$PATH"' "$SPOOL_HOME"
}

add_to_profile() {
    grep -qxF "$(path_line)" "$1" 2>/dev/null || path_line >> "$1"
    [ -n "$(tail -c 1 "$1")" ] && printf '\n' >> "$1" || true
}

put_on_path() {
    if [ -n "${SPOOL_NO_MODIFY_PATH:-}" ]; then
        say "Add $SPOOL_HOME/bin to your PATH to run spool: $(path_line)"
        return
    fi
    add_to_profile "$HOME/.profile"
    for profile in "$HOME/.bashrc" "$HOME/.zshrc"; do
        [ -f "$profile" ] && add_to_profile "$profile"
    done
    say "Added $SPOOL_HOME/bin to your PATH. Open a new terminal, or run: $(path_line)"
}

java_major() {
    "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}

has_java() {
    for java in "${JAVA_HOME:-/nonexistent}/bin/java" "$(command -v java 2>/dev/null || true)"; do
        [ -x "$java" ] || continue
        major=$(java_major "$java")
        [ -n "$major" ] && [ "$major" -ge "$REQUIRED_JAVA" ] && return 0
    done
    return 1
}

wants_jre() {
    case "${SPOOL_INSTALL_JRE:-}" in
        yes) return 0 ;;
        no) return 1 ;;
    esac
    [ -r /dev/tty ] || return 1
    printf 'Download a Temurin Java %s runtime into %s/jre? [y/N] ' "$REQUIRED_JAVA" "$SPOOL_HOME"
    read -r answer < /dev/tty || return 1
    [ "$answer" = y ] || [ "$answer" = yes ]
}

adoptium_platform() {
    case "$(uname -s)" in
        Linux) os=linux ;;
        Darwin) os=mac ;;
        *) fail "there is no Temurin runtime for $(uname -s) here: install Java $REQUIRED_JAVA yourself" ;;
    esac
    case "$(uname -m)" in
        x86_64 | amd64) arch=x64 ;;
        arm64 | aarch64) arch=aarch64 ;;
        *) fail "there is no Temurin runtime for $(uname -m) here: install Java $REQUIRED_JAVA yourself" ;;
    esac
    printf '%s/%s' "$os" "$arch"
}

install_jre() {
    download "$ADOPTIUM/$(adoptium_platform)/jre/hotspot/normal/eclipse" "$1/jre.tar.gz"
    rm -rf "$1/jre" "$SPOOL_HOME/jre"
    mkdir -p "$1/jre"
    tar -xzf "$1/jre.tar.gz" -C "$1/jre"
    java=$(find "$1/jre" -type f -path '*/bin/java' | head -n 1)
    mv "$(dirname "$(dirname "$java")")" "$SPOOL_HOME/jre"
    say "Installed a Temurin Java $REQUIRED_JAVA runtime in $SPOOL_HOME/jre"
}

check_java() {
    has_java && return
    say "spool needs Java $REQUIRED_JAVA or newer, and none was found."
    say "It can download a Temurin Java $REQUIRED_JAVA runtime into $SPOOL_HOME/jre, only for spool."
    if wants_jre; then install_jre "$1"
    else say "Install Java $REQUIRED_JAVA, or run this again with SPOOL_INSTALL_JRE=yes, before running spool."
    fi
}

main() {
    needs curl
    needs unzip
    work=$(mktemp -d)
    trap 'rm -rf "$work"' EXIT
    download "$SPOOL_DOWNLOAD_URL/spool.zip" "$work/spool.zip"
    download "$SPOOL_DOWNLOAD_URL/spool.zip.sha256" "$work/spool.zip.sha256"
    check_sha256 "$work/spool.zip" "$work/spool.zip.sha256"
    unpack "$work"
    say "Installed spool in $SPOOL_HOME"
    put_on_path
    check_java "$work"
}

main

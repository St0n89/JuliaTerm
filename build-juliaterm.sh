#!/usr/bin/env bash
# JuliaTerm build driver.
#
# Builds a full Termux fork named com.st0n89.juliaterm ("JuliaTerm"): single APK
# with an embedded minimal bootstrap (bash, coreutils, curl, proot, proot-distro)
# plus the juliaterm-scripts package (julia / julia-update wrappers that set up
# a Debian proot with juliaup on first run).
#
# Usage: build-juliaterm.sh [generator-clone-dir]
#   The termux-generator-fork must be cloned first (CI clones it at a pinned
#   commit). All work happens inside that clone.
#
# SPDX-License-Identifier: GPL-3.0-or-later
# The generated APK is a GPLv3 derivative of termux-app.
# Build orchestration adapted from https://github.com/Netsnake-TN/termux-generator-fork
# (itself a fork of robertkirkman/termux-generator).

set -e -u -o pipefail

JULIATERM_HOME="$(cd "$(dirname "$0")" && pwd)"
GENERATOR_HOME="${1:-$JULIATERM_HOME/generator}"
cd "$GENERATOR_HOME"

export TERMUX_GENERATOR_HOME="$GENERATOR_HOME"
export CI=true

source "$GENERATOR_HOME/scripts/termux_generator_utils.sh"
source "$GENERATOR_HOME/scripts/termux_generator_steps.sh"

export TERMUX_APP__PACKAGE_NAME="com.st0n89.juliaterm"
export TERMUX_APP_TYPE="f-droid"
export TERMUX_GENERATOR_PLUGIN=""
# The generator's step functions reference these; its own build-termux.sh
# defines them, but we source the functions directly, so define them here
# (empty = SSH bundling disabled) or set -u kills the build.
export ENABLE_SSH_SERVER=""
export DEFAULT_PASSWORD="changeme"
ADDITIONAL_PACKAGES="juliaterm-scripts,less,ca-certificates"
BOOTSTRAP_ARCHITECTURES="aarch64"
export DISABLE_BOOTSTRAP_SECOND_STAGE=1

# Only the main terminal app; no add-on apps.
DISABLE_TERMINAL=""
export DISABLE_TASKER=1 DISABLE_FLOAT=1 DISABLE_WIDGET=1 DISABLE_API=1
export DISABLE_BOOT=1 DISABLE_STYLING=1 DISABLE_GUI=1 DISABLE_X11=1

# Trimmed download: termux-packages + termux-app (+ termux-am-library, which
# termux-app needs as a local project via the local-termux-am-library patch).
download() {
    git clone --depth 1 https://github.com/termux/termux-packages.git termux-packages-main
    git clone --depth 1 https://github.com/termux/termux-app.git termux-apps-main/termux-app
    git clone --depth 1 https://github.com/termux/termux-am-library.git termux-apps-main/termux-am-library
    mv termux-apps-main/termux-am-library/termux-am-library/ termux-apps-main/termux-app/termux-am-library
    rm -rf termux-apps-main/termux-am-library/
}

# App patches that are skipped:
# - local-termux-gui.patch, disable-termux-x11-signature-check.patch,
#   local-maven.patch: target repos we do not clone or do not need.
# - multi-window.patch: its TerminalView.java hunks have drifted from current
#   upstream termux-app (Nov 2026). Pure UX feature (split-screen); skipped
#   for now, can be re-ported onto current upstream later.
JULIATERM_SKIP_APP_PATCHES="local-termux-gui.patch disable-termux-x11-signature-check.patch local-maven.patch multi-window.patch"
apply_patches_filtered() {
    local srcdir targetdir skiplist patchfile base
    srcdir="$(realpath "$1")"
    targetdir="$(realpath "$2")"
    skiplist="$3"
    pushd "$targetdir"
    find "$srcdir" -type f | sort | while read -r patchfile; do
        base="$(basename "$patchfile")"
        case " $skiplist " in
            *" $base "*) echo "[juliaterm] skipping app patch: $base"; continue ;;
        esac
        echo "[juliaterm] applying app patch: $base"
        patch -p1 < "$patchfile"
    done
    popd
}

# JuliaTerm UI customization: anchored rewrites instead of a static diff.
# Upstream termux-app master drifts within hours, so any hand-written
# unified-diff context is doomed (run #20: hunk #1 FAILED at 245 + a
# malformed colors.xml hunk). Each rewrite anchors on a single stable
# line, is applied with awk/sed, and is verified afterwards; a missing
# anchor fails the build loudly instead of silently skipping.
juliaterm_ui_customize() {
    local act=termux-apps-main/termux-app/app/src/main/java/com/termux/app/TermuxActivity.java
    local colors=termux-apps-main/termux-app/app/src/main/res/values/colors.xml
    local themes=termux-apps-main/termux-app/app/src/main/res/values/themes.xml
    local anchor='getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);'
    local insert
    local hits

    if [ "$(grep -c -F "$anchor" "$act" || true)" != "1" ]; then
        echo "[juliaterm] ERROR: fullscreen anchor not found exactly once in TermuxActivity.java (upstream changed?)" >&2
        return 1
    fi

    # Fully qualified android.view.View.* so no import assumptions are needed.
    insert='            // JuliaTerm: lean-back immersive; system bars stay hidden until
            // an edge swipe, then auto-hide again (sticky).
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN);'

    awk -v ins="$insert" '
        /getWindow\(\)\.addFlags\(WindowManager\.LayoutParams\.FLAG_FULLSCREEN\);/ { print; print ins; next }
        { print }' "$act" > "$act.jt" && mv "$act.jt" "$act"

    grep -q 'SYSTEM_UI_FLAG_IMMERSIVE_STICKY' "$act" || {
        echo "[juliaterm] ERROR: immersive insert did not land in TermuxActivity.java" >&2
        return 1
    }

    # colors.xml is an empty stub upstream; write our known-good content.
    cat > "$colors" <<'EOC'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- JuliaTerm branding: deep aubergine launch/window background. -->
    <color name="juliaterm_launch_background">#FF150E24</color>
</resources>
EOC

    # Swap every black window background for the branding color.
    sed -i 's|<item name="android:windowBackground">@color/black</item>|<item name="android:windowBackground">@color/juliaterm_launch_background</item>|g' "$themes"
    grep -q 'juliaterm_launch_background' "$themes" || {
        echo "[juliaterm] ERROR: windowBackground rewrite did not land in themes.xml" >&2
        return 1
    }

    echo "[juliaterm] UI customization applied (immersive fullscreen + launch background)."
}

patch_apps() {
    apply_patches_filtered "f-droid-patches/app-patches" termux-apps-main "$JULIATERM_SKIP_APP_PATCHES"
    # JuliaTerm UI customization, BEFORE the name replacement (our inserted
    # code contains no com.termux references either way).
    juliaterm_ui_customize
    if [[ "$TERMUX_APP__PACKAGE_NAME" == "com.termux" ]]; then
        return
    fi
    replace_termux_name termux-apps-main "$TERMUX_APP__PACKAGE_NAME"
    migrate_termux_folder_tree termux-apps-main "$TERMUX_APP__PACKAGE_NAME"
}

check_names
clean_docker
clean_artifacts
download

# Inject juliaterm-scripts BEFORE the name replacement so its
# /data/data/com.termux shebangs and paths are rewritten automatically.
mkdir -p termux-packages-main/packages/
cp -r "$JULIATERM_HOME/packages/juliaterm-scripts" termux-packages-main/packages/

# Generator patch drift fix: the pinned generator's builder-name-change.patch
# no longer fits current termux-packages (upstream changed
# scripts/free-space.sh; the generator's hunk #1 there was a no-op and is
# dropped in our copy, which is adapted to current upstream context).
cp "$JULIATERM_HOME/patches/builder-name-change.patch" \
    "f-droid-patches/bootstrap-patches/builder-name-change.patch"

patch_bootstraps

# First-run hint + default UI config, appended after the generator bashrc
# patches.
cat >> termux-packages-main/packages/bash/etc-bash.bashrc <<'EOF'

# JuliaTerm: hint until the first successful 'julia' run.
if [ -t 1 ] && [ ! -e "${HOME}/.juliaterm/installed" ]; then
    printf '%s\n' "JuliaTerm: type 'julia' to set up and start the Julia REPL."
    printf '%s\n' "           (first run downloads a Debian proot rootfs and juliaup, ~350 MB)"
fi

# JuliaTerm: plant default app UI config (fullscreen, dark, color scheme) on
# the first interactive shell if the user has none yet. The app reads
# ~/.termux/termux.properties and ~/.termux/colors.properties at activity
# start; restart the app once to apply.
if [ -t 1 ] && [ -d "${PREFIX}/etc/juliaterm" ]; then
    if [ ! -e "${HOME}/.termux/termux.properties" ] || [ ! -e "${HOME}/.termux/colors.properties" ]; then
        mkdir -p "${HOME}/.termux"
        [ -e "${HOME}/.termux/termux.properties" ] || cp "${PREFIX}/etc/juliaterm/termux.properties" "${HOME}/.termux/termux.properties"
        [ -e "${HOME}/.termux/colors.properties" ] || cp "${PREFIX}/etc/juliaterm/colors.properties" "${HOME}/.termux/colors.properties"
        printf '%s\n' "JuliaTerm: wrote default UI config to ~/.termux (fullscreen, dark, Julia colors)."
        printf '%s\n' "           Restart the app once to apply it."
    fi
fi
EOF

patch_apps

# Branding: launcher label "JuliaTerm" instead of the raw package id.
portable_sed_i 's|manifestPlaceholders.TERMUX_APP_NAME = ".*"|manifestPlaceholders.TERMUX_APP_NAME = "JuliaTerm"|' \
    termux-apps-main/termux-app/app/build.gradle

# termux-packages' scripts/free-space.sh runs on the HOST at the start of
# build_bootstraps (the generator calls it when CI=true) and deletes
# /opt/hostedtoolcache, /usr/share/java and the temurin apt packages: the JDK
# installed by actions/setup-java is gone by the time build_apps starts
# gradle ("JAVA_HOME is set to an invalid directory", run #14).
# Mirror the JDK into the generator clone (free-space.sh never touches
# /home/runner/work) and restore JAVA_HOME for gradle afterwards.
JULIATERM_JDK_MIRROR="$GENERATOR_HOME/.juliaterm-jdk"
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    echo "[juliaterm] mirroring JDK ${JAVA_HOME} -> ${JULIATERM_JDK_MIRROR}"
    rm -rf "$JULIATERM_JDK_MIRROR"
    cp -a "$JAVA_HOME" "$JULIATERM_JDK_MIRROR"
fi

build_bootstraps

if [ ! -x "${JAVA_HOME:-}/bin/java" ] && [ -x "$JULIATERM_JDK_MIRROR/bin/java" ]; then
    echo "[juliaterm] JAVA_HOME was removed by free-space.sh; restoring mirrored JDK"
    export JAVA_HOME="$JULIATERM_JDK_MIRROR"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[juliaterm] JAVA_HOME=$JAVA_HOME"
fi

move_bootstraps
build_apps
move_apks

echo
echo "[juliaterm] done. Artifacts:"
ls -lah ./*.apk ./*.zip 2>/dev/null || true

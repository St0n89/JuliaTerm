# JuliaTerm

Full Julia REPL on Android in a single APK. No Termux install, no RUN_COMMAND
permission, no adb: JuliaTerm is its own terminal app (a fork of
[termux-app](https://github.com/termux/termux-app) under the package id
`com.st0n89.juliaterm`, still targeting SDK 28 so executables can run).

## How it works

- The CI clones [termux-app](https://github.com/termux/termux-app) and
  [termux-packages](https://github.com/termux/termux-packages), renames every
  `com.termux` reference to `com.st0n89.juliaterm`, and builds a minimal
  bootstrap containing bash, coreutils, curl, ca-certificates, less, proot,
  proot-distro and the `juliaterm-scripts` package.
- The bootstrap is embedded in the APK and extracted on first launch.
- `julia` (first run) installs a Debian proot rootfs via proot-distro, then
  juliaup inside it, then execs the Julia REPL. All later runs start instantly.
- `julia-update` updates Julia through juliaup.

## Install

1. Sideload `JuliaTerm-universal.apk` from the
   [releases](../../releases) (debug-signed with the Termux test key).
2. Open the app. You land in a bash shell.
3. Type `julia`. First run downloads Debian + juliaup (~350 MB total);
   after that it is the full Julia REPL including Pkg.

## Build

The workflow runs automatically on changes to `build-juliaterm.sh`,
`packages/**` or the workflow itself, and can also be started manually
(Actions tab -> Build JuliaTerm APK -> Run workflow). A build takes roughly
1.5 to 3 hours.

Output: release `v0.1.0` with `JuliaTerm-universal.apk` plus the raw
per-ABI APKs as workflow artifact.

## Notes

- **License**: the generated APK is a GPLv3 derivative of termux-app. This
  public repository contains the full build system (build-juliaterm.sh +
  package sources + pinned generator ref), which constitutes the complete
  corresponding source for the APK.
- The generator pinned in the workflow is
  [Netsnake-TN/termux-generator-fork](https://github.com/Netsnake-TN/termux-generator-fork)
  (fork of robertkirkman/termux-generator); its scripts do the heavy
  lifting: name replacement, bootstrap build in Docker, APK assembly.

## Credits

- [Termux](https://termux.dev/) and its contributors (GPLv3).
- [Netsnake-TN/termux-generator-fork](https://github.com/Netsnake-TN/termux-generator-fork)
  and [robertkirkman/termux-generator](https://github.com/robertkirkman/termux-generator).

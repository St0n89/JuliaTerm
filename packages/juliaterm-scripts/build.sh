# juliaterm-scripts: Julia REPL wrappers for JuliaTerm.
# Builder-dir-only package (empty SRCURL is supported by termux-packages:
# termux_step_get_source just creates the source dir). The wrapper scripts are
# installed straight from this directory, so nothing is fetched at build time
# and a private JuliaTerm repo works fine.
TERMUX_PKG_HOMEPAGE="https://github.com/St0n89/JuliaTerm"
TERMUX_PKG_DESCRIPTION="Julia REPL wrappers for JuliaTerm (proot Debian + juliaup)"
TERMUX_PKG_LICENSE="MIT"
TERMUX_PKG_MAINTAINER="Dennis Kunde @St0n89"
TERMUX_PKG_VERSION="0.1.0"
TERMUX_PKG_DEPENDS="proot-distro, curl, ca-certificates"
TERMUX_PKG_PLATFORM_INDEPENDENT=true

termux_step_make_install() {
	install -Dm755 "${TERMUX_PKG_BUILDER_DIR}/juliaterm-julia" "${TERMUX_PREFIX}/bin/julia"
	install -Dm755 "${TERMUX_PKG_BUILDER_DIR}/juliaterm-julia-update" "${TERMUX_PREFIX}/bin/julia-update"
}

# juliaterm-scripts: Julia REPL wrappers for JuliaTerm.
# Builder-dir-only package (empty SRCURL is supported by termux-packages:
# termux_step_get_source just creates the source dir). The wrapper scripts are
# installed straight from this directory, so nothing is fetched at build time
# and a private JuliaTerm repo works fine.
TERMUX_PKG_HOMEPAGE="https://github.com/St0n89/JuliaTerm"
TERMUX_PKG_DESCRIPTION="Julia REPL wrappers for JuliaTerm (proot Debian + juliaup)"
TERMUX_PKG_LICENSE="MIT"
TERMUX_PKG_MAINTAINER="Dennis Kunde @St0n89"
TERMUX_PKG_VERSION="0.3.1"
TERMUX_PKG_DEPENDS="proot-distro, curl, ca-certificates"
TERMUX_PKG_PLATFORM_INDEPENDENT=true

# termux_step_install_license (MIT is a copyright-bearing license) searches
# ONLY $TERMUX_PKG_SRCDIR, which for an empty-SRCURL package is a freshly
# created, empty dir -> "Could not find a license file for MIT". Stage ours.
termux_step_post_get_source() {
	install -m 644 "${TERMUX_PKG_BUILDER_DIR}/LICENSE" "${TERMUX_PKG_SRCDIR}/LICENSE"
}

termux_step_make_install() {
	install -Dm755 "${TERMUX_PKG_BUILDER_DIR}/juliaterm-julia" "${TERMUX_PREFIX}/bin/julia"
	install -Dm755 "${TERMUX_PKG_BUILDER_DIR}/juliaterm-julia-update" "${TERMUX_PREFIX}/bin/julia-update"
	install -Dm755 "${TERMUX_PKG_BUILDER_DIR}/juliaterm-notebook" "${TERMUX_PREFIX}/bin/notebook"
	# Default UI runtime config, planted into ~/.termux by the bashrc hook.
	install -Dm644 "${TERMUX_PKG_BUILDER_DIR}/termux.properties.default" "${TERMUX_PREFIX}/etc/juliaterm/termux.properties"
	install -Dm644 "${TERMUX_PKG_BUILDER_DIR}/colors.properties.default" "${TERMUX_PREFIX}/etc/juliaterm/colors.properties"
}

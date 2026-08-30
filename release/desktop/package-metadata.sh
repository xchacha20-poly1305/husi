# Sourced by the packaging scripts, never executed, so it carries a shell
# directive instead of a shebang.
# shellcheck shell=bash

APP_NAME="Husi"
APP_NAME_ZH_CN="虎兕"
APP_NAME_ZH_TW="虎兕"
APP_DESCRIPTION="Husi is a non-professional proxy-set-based multiplatform proxy tool set."
APP_DESCRIPTION_ZH_CN="虎兕 是一个非专业的基于代理集的跨平台代理工具集。"
APP_DESCRIPTION_ZH_TW="虎兕 是一個非專業的基於代理集的跨平台代理工具集。"
APP_FULL_DESCRIPTION="Husi is a non-professional proxy-set-based multiplatform proxy tool set. With graphical config editor, network tools and proxy status observation."
APP_FULL_DESCRIPTION_ZH_CN="虎兕 是一个非专业的基于代理集的跨平台代理工具集。有着图形化编辑配置、网络工具以及代理状态观测的功能。"
APP_FULL_DESCRIPTION_ZH_TW="虎兕 是一個非專業的基於代理集的跨平台代理工具集。有著圖形化編輯配置、網路工具以及代理狀態觀測的功能。"
APP_URL="https://github.com/xchacha20-poly1305/husi"
MAINTAINER="Husi contributors"
DESKTOP_URL_TYPE_NAME="Import URL"
DESKTOP_URL_SCHEMES=(
    husi
    sing-box
    ss
    socks
    socks4
    socks4a
    sock5
    vmess
    vless
    trojan
    trojan-go
    naive+https
    naive+quic
    hysteria
    hysteria2
    hy2
    tuic
    juicity
    sq
    shadowquic
    mieru
    anytls
    tt
)

# "owner/repo" of the GitHub project APP_URL points at, empty for a fork that
# publishes somewhere else. The single source of truth for the repository slug:
# whatever rename.sh writes into APP_URL is what the packaging follows.
github_repository_slug() {
    local github_prefix="https://github.com/"
    local path="${APP_URL#"$github_prefix"}"

    if [[ "$path" == "$APP_URL" ]]; then
        return
    fi

    # Nothing but "<owner>/<repo>" qualifies: a deeper path is not a project page.
    if [[ "$path" =~ ^[^/]+/[^/]+$ ]]; then
        printf '%s' "${path%.git}"
    fi
}

desktop_url_scheme_mime_types() {
    local scheme
    for scheme in "${DESKTOP_URL_SCHEMES[@]}"; do
        printf 'x-scheme-handler/%s;' "$scheme"
    done
}

desktop_url_scheme_entries_plist() {
    local scheme
    for scheme in "${DESKTOP_URL_SCHEMES[@]}"; do
        printf '<string>%s</string>' "$scheme"
    done
}

# Sourced by the packaging scripts, never executed, so it carries a shell
# directive instead of a shebang.
# shellcheck shell=bash

APP_NAME="Husi"
APP_NAME_ZH_CN="虎兕"
APP_NAME_ZH_TW="虎兕"
APP_DESCRIPTION="A non-professional and recreational proxy tool integration."
APP_DESCRIPTION_ZH_CN="一个非专业和娱乐性的代理工具集。"
APP_DESCRIPTION_ZH_TW="一個非專業和娛樂性的代理工具集。"
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

#!/usr/bin/env bash
set -euo pipefail

launcher_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
launcher_bin="$launcher_dir/__HUSI_WRAPPED_LAUNCHER__"
java_opts_template="$launcher_dir/desktop-java-opts.conf.template"
app_args_template="$launcher_dir/desktop-app-args.conf.template"

config_home="${XDG_CONFIG_HOME:-$HOME/.config}"
config_dir="$config_home/husi"
java_opts_file="$config_dir/desktop-java-opts.conf"
app_args_file="$config_dir/desktop-app-args.conf"

mkdir -p "$config_dir"

if [ ! -f "$java_opts_file" ]; then
    if [ -f "$java_opts_template" ]; then
        cp "$java_opts_template" "$java_opts_file"
    else
        : > "$java_opts_file"
    fi
fi

if [ ! -f "$app_args_file" ]; then
    if [ -f "$app_args_template" ]; then
        cp "$app_args_template" "$app_args_file"
    else
        : > "$app_args_file"
    fi
fi

append_args_from_file() {
    local file="$1"
    local -n output="$2"

    if [ ! -f "$file" ]; then
        return 0
    fi

    local line trimmed
    while IFS= read -r line || [ -n "$line" ]; do
        trimmed="${line#"${line%%[![:space:]]*}"}"
        trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
        if [ -z "$trimmed" ] || [[ "$trimmed" == \#* ]]; then
            continue
        fi
        output+=("$trimmed")
    done < "$file"
}

join_escaped_args() {
    local escaped=""
    local part

    for part in "$@"; do
        if [ -n "$escaped" ]; then
            escaped+=" "
        fi
        printf -v part '%q' "$part"
        escaped+="$part"
    done

    printf '%s' "$escaped"
}

java_opts=()
app_args=()
append_args_from_file "$java_opts_file" java_opts
append_args_from_file "$app_args_file" app_args

if [ "${#java_opts[@]}" -gt 0 ]; then
    java_opts_joined="$(join_escaped_args "${java_opts[@]}")"
    if [ -n "${JDK_JAVA_OPTIONS:-}" ]; then
        export JDK_JAVA_OPTIONS="$java_opts_joined $JDK_JAVA_OPTIONS"
    else
        export JDK_JAVA_OPTIONS="$java_opts_joined"
    fi
fi

exec "$launcher_bin" "${app_args[@]}" "$@"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef HUSI_PACKAGE_NAME
#define HUSI_PACKAGE_NAME "husi"
#endif

#define HUSI_CONFIG_DIR_NAME "husi"
#define HUSI_EXIT_RESTART 50
#define U32_BITS 32U

// To not include <linux/capability.h> and break on older kernels, we define only the necessary constants and structures here.
#define HUSI_CAP_VERSION_3 0x20080522U
#define HUSI_CAP_DAC_READ_SEARCH 2
#define HUSI_CAP_NET_BIND_SERVICE 10
#define HUSI_CAP_NET_ADMIN 12
#define HUSI_CAP_NET_RAW 13
#define HUSI_CAP_SETPCAP 8
#define HUSI_CAP_SYS_PTRACE 19

#ifndef PR_CAP_AMBIENT
#define PR_CAP_AMBIENT 47
#endif

#ifndef PR_CAP_AMBIENT_RAISE
#define PR_CAP_AMBIENT_RAISE 2
#endif

struct husi_cap_header {
    uint32_t version;
    int pid;
};

struct husi_cap_data {
    uint32_t effective;
    uint32_t permitted;
    uint32_t inheritable;
};

struct string_list {
    char **items;
    size_t size;
    size_t capacity;
};

static int capget_raw(struct husi_cap_header *header, struct husi_cap_data *data) {
    return (int)syscall(SYS_capget, header, data);
}

static int capset_raw(const struct husi_cap_header *header, const struct husi_cap_data *data) {
    return (int)syscall(SYS_capset, header, data);
}

static bool set_inheritable_caps(const int *caps, size_t size) {
    struct husi_cap_header header = {
        .version = HUSI_CAP_VERSION_3,
        .pid = 0,
    };
    struct husi_cap_data data[2] = {0};

    if (capget_raw(&header, data) != 0) {
        perror("capget");
        return false;
    }

    for (size_t i = 0; i < size; i++) {
        const int cap = caps[i];
        const uint32_t index = (uint32_t)cap / U32_BITS;
        const uint32_t bit = 1U << ((uint32_t)cap % U32_BITS);

        if (index >= 2U) {
            fprintf(stderr, "unsupported capability index: %d\n", cap);
            return false;
        }
        if ((data[index].permitted & bit) == 0U) {
            fprintf(stderr, "missing permitted capability: %d\n", cap);
            return false;
        }
        data[index].inheritable |= bit;
    }

    if (capset_raw(&header, data) != 0) {
        perror("capset(inheritable)");
        return false;
    }

    return true;
}

static bool raise_ambient_caps(const int *caps, size_t size) {
    for (size_t i = 0; i < size; i++) {
        if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, caps[i], 0, 0) != 0) {
            perror("prctl(PR_CAP_AMBIENT_RAISE)");
            return false;
        }
    }
    return true;
}

static bool drop_setpcap(void) {
    struct husi_cap_header header = {
        .version = HUSI_CAP_VERSION_3,
        .pid = 0,
    };
    struct husi_cap_data data[2] = {0};
    const uint32_t index = (uint32_t)HUSI_CAP_SETPCAP / U32_BITS;
    const uint32_t bit = 1U << ((uint32_t)HUSI_CAP_SETPCAP % U32_BITS);

    if (capget_raw(&header, data) != 0) {
        perror("capget(drop setpcap)");
        return false;
    }

    data[index].effective &= ~bit;
    data[index].permitted &= ~bit;
    data[index].inheritable &= ~bit;

    if (capset_raw(&header, data) != 0) {
        perror("capset(drop setpcap)");
        return false;
    }

    return true;
}

static bool read_exe_path(char *buffer, size_t size) {
    const ssize_t len = readlink("/proc/self/exe", buffer, size - 1U);
    if (len <= 0) {
        perror("readlink(/proc/self/exe)");
        return false;
    }
    buffer[(size_t)len] = '\0';
    return true;
}

static bool remove_last_path_component(char *path) {
    char *slash = strrchr(path, '/');
    if (slash == NULL || slash == path) {
        return false;
    }
    *slash = '\0';
    return true;
}

static char *duplicate_string(const char *value) {
    const size_t len = strlen(value);
    char *copy = (char *)malloc(len + 1U);
    if (copy == NULL) {
        perror("malloc(copy)");
        return NULL;
    }
    memcpy(copy, value, len + 1U);
    return copy;
}

static char *format_path(const char *format, const char *a, const char *b) {
    const int need = snprintf(NULL, 0, format, a, b);
    if (need <= 0) {
        fprintf(stderr, "failed to calculate path length\n");
        return NULL;
    }

    char *path = (char *)malloc((size_t)need + 1U);
    if (path == NULL) {
        perror("malloc(path)");
        return NULL;
    }

    snprintf(path, (size_t)need + 1U, format, a, b);
    return path;
}

static bool string_list_push(struct string_list *list, const char *value) {
    if (list->size == list->capacity) {
        const size_t next_capacity = (list->capacity == 0U) ? 8U : list->capacity * 2U;
        char **next_items = (char **)realloc(list->items, next_capacity * sizeof(char *));
        if (next_items == NULL) {
            perror("realloc(args)");
            return false;
        }
        list->items = next_items;
        list->capacity = next_capacity;
    }

    char *copy = duplicate_string(value);
    if (copy == NULL) {
        return false;
    }

    list->items[list->size++] = copy;
    return true;
}

static void string_list_free(struct string_list *list) {
    for (size_t i = 0; i < list->size; i++) {
        free(list->items[i]);
    }
    free(list->items);
    list->items = NULL;
    list->size = 0U;
    list->capacity = 0U;
}

static char *trim_whitespace(char *line) {
    char *start = line;
    while (*start != '\0' && isspace((unsigned char)*start) != 0) {
        start++;
    }

    char *end = start + strlen(start);
    while (end > start && isspace((unsigned char)end[-1]) != 0) {
        end--;
    }
    *end = '\0';
    return start;
}

static bool read_args_file(const char *path, struct string_list *list) {
    FILE *file = fopen(path, "r");
    if (file == NULL) {
        perror(path);
        return false;
    }

    char *line = NULL;
    size_t capacity = 0U;
    ssize_t length;
    while ((length = getline(&line, &capacity, file)) >= 0) {
        (void)length;
        char *trimmed = trim_whitespace(line);
        if (trimmed[0] == '\0' || trimmed[0] == '#') {
            continue;
        }
        if (!string_list_push(list, trimmed)) {
            free(line);
            fclose(file);
            return false;
        }
    }

    free(line);
    fclose(file);
    return true;
}

static bool file_exists(const char *path) {
    return access(path, F_OK) == 0;
}

static bool ensure_directory_recursive(const char *path) {
    char buffer[PATH_MAX] = {0};
    const size_t len = strlen(path);

    if (len == 0U || len >= sizeof(buffer)) {
        fprintf(stderr, "invalid directory path: %s\n", path);
        return false;
    }

    memcpy(buffer, path, len + 1U);

    for (char *cursor = buffer + 1; *cursor != '\0'; cursor++) {
        if (*cursor != '/') {
            continue;
        }
        *cursor = '\0';
        if (mkdir(buffer, 0755) != 0 && errno != EEXIST) {
            perror(buffer);
            return false;
        }
        *cursor = '/';
    }

    if (mkdir(buffer, 0755) != 0 && errno != EEXIST) {
        perror(buffer);
        return false;
    }
    return true;
}

static bool copy_file(const char *source, const char *target) {
    FILE *src = fopen(source, "rb");
    if (src == NULL) {
        perror(source);
        return false;
    }

    FILE *dst = fopen(target, "wb");
    if (dst == NULL) {
        perror(target);
        fclose(src);
        return false;
    }

    char buffer[8192];
    size_t read_size;
    while ((read_size = fread(buffer, 1U, sizeof(buffer), src)) > 0U) {
        if (fwrite(buffer, 1U, read_size, dst) != read_size) {
            perror("fwrite(config)");
            fclose(src);
            fclose(dst);
            return false;
        }
    }

    if (ferror(src) != 0) {
        perror("fread(config)");
        fclose(src);
        fclose(dst);
        return false;
    }

    if (fclose(src) != 0 || fclose(dst) != 0) {
        perror("fclose(config)");
        return false;
    }
    return true;
}

static bool touch_file(const char *path) {
    FILE *file = fopen(path, "a");
    if (file == NULL) {
        perror(path);
        return false;
    }
    if (fclose(file) != 0) {
        perror("fclose(touch)");
        return false;
    }
    return true;
}

static bool ensure_user_config_file(const char *config_path, const char *template_path) {
    if (file_exists(config_path)) {
        return true;
    }
    if (file_exists(template_path)) {
        return copy_file(template_path, config_path);
    }
    return touch_file(config_path);
}

static bool resolve_runtime_paths(char **launcher_dir, char **app_root, char **jar_path) {
    char exe_path[PATH_MAX] = {0};
    if (!read_exe_path(exe_path, sizeof(exe_path))) {
        return false;
    }

    if (!remove_last_path_component(exe_path)) {
        fprintf(stderr, "failed to resolve launcher directory\n");
        return false;
    }
    *launcher_dir = duplicate_string(exe_path);
    if (*launcher_dir == NULL) {
        return false;
    }

    if (!remove_last_path_component(exe_path)) {
        fprintf(stderr, "failed to resolve app root\n");
        free(*launcher_dir);
        *launcher_dir = NULL;
        return false;
    }
    *app_root = duplicate_string(exe_path);
    if (*app_root == NULL) {
        free(*launcher_dir);
        *launcher_dir = NULL;
        return false;
    }

    *jar_path = format_path("%s/app/%s.jar", *app_root, HUSI_PACKAGE_NAME);
    if (*jar_path == NULL) {
        free(*launcher_dir);
        free(*app_root);
        *launcher_dir = NULL;
        *app_root = NULL;
        return false;
    }

    return true;
}

static bool resolve_user_config_paths(char **java_opts_path, char **app_args_path) {
    const char *xdg_config_home = getenv("XDG_CONFIG_HOME");
    const char *config_base = xdg_config_home;
    char *config_home_alloc = NULL;
    char *config_dir = NULL;

    if (config_base == NULL || config_base[0] == '\0') {
        const char *home = getenv("HOME");
        if (home == NULL || home[0] == '\0') {
            fprintf(stderr, "HOME or XDG_CONFIG_HOME is required\n");
            return false;
        }
        config_home_alloc = format_path("%s/%s", home, ".config");
        if (config_home_alloc == NULL) {
            return false;
        }
        config_base = config_home_alloc;
    }

    config_dir = format_path("%s/%s", config_base, HUSI_CONFIG_DIR_NAME);
    if (config_dir == NULL) {
        free(config_home_alloc);
        return false;
    }

    if (!ensure_directory_recursive(config_dir)) {
        free(config_home_alloc);
        free(config_dir);
        return false;
    }

    *java_opts_path = format_path("%s/%s", config_dir, "desktop-java-opts.conf");
    *app_args_path = format_path("%s/%s", config_dir, "desktop-app-args.conf");

    free(config_home_alloc);
    free(config_dir);

    if (*java_opts_path == NULL || *app_args_path == NULL) {
        free(*java_opts_path);
        free(*app_args_path);
        *java_opts_path = NULL;
        *app_args_path = NULL;
        return false;
    }
    return true;
}

static const char *select_java_command(char *java_home_path, size_t java_home_path_size) {
    const char *java_home = getenv("JAVA_HOME");
    if (java_home != NULL && java_home[0] != '\0') {
        const int need = snprintf(java_home_path, java_home_path_size, "%s/bin/java", java_home);
        if (need > 0 && (size_t)need < java_home_path_size && access(java_home_path, X_OK) == 0) {
            return java_home_path;
        }
    }

    const char *java_env = getenv("JAVA");
    if (java_env != NULL && java_env[0] != '\0') {
        return java_env;
    }

    return "java";
}

int main(int argc, char **argv) {
    static const int kAmbientCaps[] = {
        HUSI_CAP_NET_ADMIN,
        HUSI_CAP_NET_RAW,
        HUSI_CAP_NET_BIND_SERVICE,
        HUSI_CAP_SYS_PTRACE,
        HUSI_CAP_DAC_READ_SEARCH,
    };

    struct string_list java_opts = {0};
    struct string_list app_args = {0};
    char *launcher_dir = NULL;
    char *app_root = NULL;
    char *jar_path = NULL;
    char *java_opts_template = NULL;
    char *app_args_template = NULL;
    char *java_opts_file = NULL;
    char *app_args_file = NULL;
    char **child_argv = NULL;
    int result = 1;

    if (!set_inheritable_caps(kAmbientCaps, sizeof(kAmbientCaps) / sizeof(kAmbientCaps[0]))) {
        goto cleanup;
    }
    if (!raise_ambient_caps(kAmbientCaps, sizeof(kAmbientCaps) / sizeof(kAmbientCaps[0]))) {
        goto cleanup;
    }
    if (!drop_setpcap()) {
        goto cleanup;
    }

    if (!resolve_runtime_paths(&launcher_dir, &app_root, &jar_path)) {
        goto cleanup;
    }

    java_opts_template = format_path("%s/%s", launcher_dir, "desktop-java-opts.conf.template");
    app_args_template = format_path("%s/%s", launcher_dir, "desktop-app-args.conf.template");
    if (java_opts_template == NULL || app_args_template == NULL) {
        goto cleanup;
    }

    if (!resolve_user_config_paths(&java_opts_file, &app_args_file)) {
        goto cleanup;
    }

    if (!ensure_user_config_file(java_opts_file, java_opts_template)) {
        goto cleanup;
    }
    if (!ensure_user_config_file(app_args_file, app_args_template)) {
        goto cleanup;
    }
    if (!read_args_file(java_opts_file, &java_opts)) {
        goto cleanup;
    }
    if (!read_args_file(app_args_file, &app_args)) {
        goto cleanup;
    }

    char java_home_path[PATH_MAX] = {0};
    const char *java_command = select_java_command(java_home_path, sizeof(java_home_path));
    const size_t child_argc =
        1U + java_opts.size + 2U + 1U + app_args.size + (size_t)(argc - 1) + 1U;
    child_argv = (char **)calloc(child_argc, sizeof(char *));
    if (child_argv == NULL) {
        perror("calloc(argv)");
        goto cleanup;
    }

    size_t index = 0U;
    child_argv[index++] = (char *)java_command;
    for (size_t i = 0; i < java_opts.size; i++) {
        child_argv[index++] = java_opts.items[i];
    }
    child_argv[index++] = "-jar";
    child_argv[index++] = jar_path;
    for (size_t i = 0; i < app_args.size; i++) {
        child_argv[index++] = app_args.items[i];
    }
    for (int i = 1; i < argc; i++) {
        child_argv[index++] = argv[i];
    }
    child_argv[index] = NULL;

    for (;;) {
        const pid_t pid = fork();
        if (pid < 0) {
            perror("fork");
            goto cleanup;
        }

        if (pid == 0) {
            execvp(java_command, child_argv);
            perror("execvp(java)");
            _exit((errno == ENOENT) ? 127 : 1);
        }

        int status;
        if (waitpid(pid, &status, 0) < 0) {
            perror("waitpid");
            goto cleanup;
        }

        if (!WIFEXITED(status)) {
            result = 1;
            goto cleanup;
        }

        const int exit_code = WEXITSTATUS(status);
        if (exit_code != HUSI_EXIT_RESTART) {
            result = exit_code;
            goto cleanup;
        }
    }

cleanup:
    free(child_argv);
    free(java_opts_file);
    free(app_args_file);
    free(java_opts_template);
    free(app_args_template);
    free(jar_path);
    free(app_root);
    free(launcher_dir);
    string_list_free(&app_args);
    string_list_free(&java_opts);
    return result;
}

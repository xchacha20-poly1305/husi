const std = @import("std");
const builtin = @import("builtin");
const fs = std.fs;
const Io = std.Io;
const mem = std.mem;
const process = std.process;
const ArrayList = std.ArrayList;
const native_os = builtin.os.tag;

comptime {
    switch (native_os) {
        .linux, .macos, .windows => {},
        else => @compileError("This launcher only supports Linux, macOS and Windows."),
    }
}

const config = @import("config");
const husi_package_name = config.package_name;
const husi_config_dir_name = "husi";
const husi_exit_restart = 50;

var self_exe_buf: [Io.Dir.max_path_bytes]u8 = undefined;
var self_path: ?[]const u8 = null;

fn findSelfExePath(io: Io) ![]const u8 {
    if (self_path) |path| {
        return path;
    }
    const size = try process.executablePath(io, &self_exe_buf);
    self_path = self_exe_buf[0..size];
    return self_path.?;
}

fn readArgsFile(io: Io, allocator: mem.Allocator, path: []const u8, list: *ArrayList([]u8)) !void {
    const file = try Io.Dir.openFileAbsolute(io, path, .{});
    defer file.close(io);

    // will not too big...
    const read_buf_size = 512;
    var file_buf: [read_buf_size]u8 = undefined;
    var reader = file.reader(io, &file_buf);

    var ioAllocating: Io.Writer.Allocating = .init(allocator);
    defer ioAllocating.deinit();

    while (true) {
        ioAllocating.clearRetainingCapacity();
        _ = reader.interface.streamDelimiter(&ioAllocating.writer, '\n') catch |err| switch (err) {
            error.EndOfStream => {
                const trimmed = mem.trim(u8, ioAllocating.written(), &std.ascii.whitespace);
                if (trimmed.len > 0 and trimmed[0] != '#')
                    try list.append(allocator, try allocator.dupe(u8, trimmed));
                return;
            },
            else => |e| return e,
        };
        _ = try reader.interface.discardDelimiterInclusive('\n');
        const trimmed = mem.trim(u8, ioAllocating.written(), &std.ascii.whitespace);
        if (trimmed.len == 0 or trimmed[0] == '#') continue;
        try list.append(allocator, try allocator.dupe(u8, trimmed));
    }
}

fn fileExists(io: Io, path: []const u8) bool {
    Io.Dir.accessAbsolute(io, path, .{}) catch return false;
    return true;
}

fn resolveArgsFile(io: Io, user_path: []const u8, template_path: []const u8) ?[]const u8 {
    if (fileExists(io, user_path)) return user_path;
    if (fileExists(io, template_path)) return template_path;
    return null;
}

const RuntimePaths = struct {
    launcher_dir: []u8,
    app_root: []u8,
    jar_path: []u8,
};

const MacOSAppBundleOptions = struct {
    dock_name: ?[]const u8,
    dock_icon_path: ?[]const u8,
};

fn resolveRuntimePaths(io: Io, allocator: mem.Allocator) !RuntimePaths {
    const exe_path = try findSelfExePath(io);

    const launcher_dir_slice = Io.Dir.path.dirname(exe_path) orelse return error.BadExePath;
    const launcher_dir = try allocator.dupe(u8, launcher_dir_slice);
    errdefer allocator.free(launcher_dir);

    const direct_jar_path = try std.fmt.allocPrint(allocator, "{s}/app/{s}.jar", .{ launcher_dir_slice, husi_package_name });
    if (fileExists(io, direct_jar_path)) {
        return RuntimePaths{
            .launcher_dir = launcher_dir,
            .app_root = launcher_dir,
            .jar_path = direct_jar_path,
        };
    }
    allocator.free(direct_jar_path);

    const app_root_slice = Io.Dir.path.dirname(launcher_dir_slice) orelse return error.BadExePath;
    const app_root = try allocator.dupe(u8, app_root_slice);
    errdefer allocator.free(app_root);

    const jar_path = try std.fmt.allocPrint(allocator, "{s}/app/{s}.jar", .{ app_root, husi_package_name });

    return RuntimePaths{
        .launcher_dir = launcher_dir,
        .app_root = app_root,
        .jar_path = jar_path,
    };
}

fn resolveMacOSAppBundleOptions(io: Io, allocator: mem.Allocator, runtime: RuntimePaths) !?MacOSAppBundleOptions {
    if (native_os != .macos) return null;

    const bundle_root_slice = Io.Dir.path.dirname(runtime.app_root) orelse return null;
    const bundle_name = Io.Dir.path.basename(bundle_root_slice);

    var dock_name: ?[]const u8 = null;
    if (mem.endsWith(u8, bundle_name, ".app")) {
        const trimmed = bundle_name[0 .. bundle_name.len - 4];
        if (trimmed.len > 0) {
            dock_name = try allocator.dupe(u8, trimmed);
        }
    } else if (bundle_name.len > 0) {
        dock_name = try allocator.dupe(u8, bundle_name);
    }

    const dock_icon_candidate = try std.fmt.allocPrint(allocator, "{s}/Resources/{s}.icns", .{ runtime.app_root, husi_package_name });
    const dock_icon_path = if (fileExists(io, dock_icon_candidate)) dock_icon_candidate else blk: {
        allocator.free(dock_icon_candidate);
        break :blk null;
    };

    if (dock_name == null and dock_icon_path == null) return null;

    return MacOSAppBundleOptions{
        .dock_name = dock_name,
        .dock_icon_path = dock_icon_path,
    };
}

const UserConfigPaths = struct {
    config_dir: []u8,
    java_opts_path: []u8,
    app_args_path: []u8,
};

fn resolveUserHome(allocator: mem.Allocator, env_map: *process.Environ.Map) ![]u8 {
    switch (native_os) {
        .linux, .macos => {
            if (env_map.get("HOME")) |home| {
                if (home.len > 0) return allocator.dupe(u8, home);
            }
        },
        .windows => {
            if (env_map.get("USERPROFILE")) |user_profile| {
                if (user_profile.len > 0) return allocator.dupe(u8, user_profile);
            }
            if (env_map.get("HOME")) |home| {
                if (home.len > 0) return allocator.dupe(u8, home);
            }
        },
        else => unreachable,
    }
    return error.MissingHome;
}

fn resolveConfigBase(allocator: mem.Allocator, env_map: *process.Environ.Map) ![]u8 {
    switch (native_os) {
        .linux => {
            if (env_map.get("XDG_CONFIG_HOME")) |xdg| {
                if (xdg.len > 0) {
                    return allocator.dupe(u8, xdg);
                }
            }
            const home = try resolveUserHome(allocator, env_map);
            defer allocator.free(home);
            return std.fmt.allocPrint(allocator, "{s}/.config", .{home});
        },
        .macos => {
            const home = try resolveUserHome(allocator, env_map);
            defer allocator.free(home);
            return std.fmt.allocPrint(allocator, "{s}/Library/Application Support", .{home});
        },
        .windows => {
            if (env_map.get("APPDATA")) |app_data| {
                if (app_data.len > 0) return allocator.dupe(u8, app_data);
            }
            const home = try resolveUserHome(allocator, env_map);
            defer allocator.free(home);
            return std.fmt.allocPrint(allocator, "{s}/AppData/Roaming", .{home});
        },
        else => unreachable,
    }
}

fn resolveUserConfigPaths(allocator: mem.Allocator, env_map: *process.Environ.Map) !UserConfigPaths {
    const config_base = try resolveConfigBase(allocator, env_map);
    defer allocator.free(config_base);

    const config_dir = try std.fmt.allocPrint(allocator, "{s}/{s}", .{ config_base, husi_config_dir_name });
    errdefer allocator.free(config_dir);

    const java_opts_path = try std.fmt.allocPrint(allocator, "{s}/desktop-java-opts.conf", .{config_dir});
    errdefer allocator.free(java_opts_path);
    const app_args_path = try std.fmt.allocPrint(allocator, "{s}/desktop-app-args.conf", .{config_dir});

    return UserConfigPaths{
        .config_dir = config_dir,
        .java_opts_path = java_opts_path,
        .app_args_path = app_args_path,
    };
}

fn resolveMacOSJavaHome(io: Io, allocator: mem.Allocator) !?[]const u8 {
    if (native_os != .macos) return null;

    const java_version_requirement = "21+";
    const result = process.run(allocator, io, .{
        .argv = &.{ "/usr/libexec/java_home", "-v", java_version_requirement },
    }) catch |err| switch (err) {
        error.FileNotFound => return null,
        else => |e| return e,
    };
    defer allocator.free(result.stdout);
    defer allocator.free(result.stderr);

    switch (result.term) {
        .exited => |code| {
            if (code != 0) return null;
        },
        else => return null,
    }

    const java_home = mem.trim(u8, result.stdout, &std.ascii.whitespace);
    if (java_home.len == 0) return null;

    const java_bin = try std.fmt.allocPrint(allocator, "{s}/bin/java", .{java_home});
    if (fileExists(io, java_bin)) return java_bin;

    allocator.free(java_bin);
    return null;
}

fn resolveJavaHomeCommand(io: Io, allocator: mem.Allocator, java_home: []const u8) !?[]const u8 {
    if (java_home.len == 0) return null;

    const candidate_names = if (native_os == .windows)
        [_][]const u8{ "javaw.exe", "java.exe" }
    else
        [_][]const u8{"java"};

    for (candidate_names) |candidate_name| {
        const candidate = try Io.Dir.path.join(allocator, &.{ java_home, "bin", candidate_name });
        if (Io.Dir.accessAbsolute(io, candidate, .{})) |_| {
            return candidate;
        } else |_| {
            allocator.free(candidate);
        }
    }

    return null;
}

/// $java --version
/// openjdk 21.0.12 2026-07-21
/// OpenJDK Runtime Environment (build 21.0.12+8)
/// OpenJDK 64-Bit Server VM (build 21.0.12+8, mixed mode, sharing)
fn parseJavaMajorVersion(output: []const u8) ?u32 {
    const marker = "version \"";
    const indexOfMarker = mem.indexOf(u8, output, marker) orelse return null;
    const start = indexOfMarker + marker.len;
    var end = start;
    while (end < output.len and std.ascii.isDigit(output[end])) {
        end += 1;
    }
    if (end == start) return null;
    return std.fmt.parseInt(u32, output[start..end], 10) catch null;
}

fn probeJavaVersionLine(io: Io, allocator: mem.Allocator, java_command: []const u8) !?[]const u8 {
    // `javaw.exe -version` opens a message box instead of printing; probe with
    // the sibling `java.exe`.
    const probe_command = if (native_os == .windows and mem.endsWith(u8, java_command, "javaw.exe"))
        try std.fmt.allocPrint(allocator, "{s}java.exe", .{java_command[0 .. java_command.len - "javaw.exe".len]})
    else
        java_command;

    const result = process.run(allocator, io, .{
        .argv = &.{ probe_command, "-version" },
    }) catch return null;
    defer allocator.free(result.stdout);

    switch (result.term) {
        .exited => |code| {
            if (code != 0) {
                allocator.free(result.stderr);
                return null;
            }
        },
        else => {
            allocator.free(result.stderr);
            return null;
        },
    }

    // `java -version` traditionally prints to stderr.
    return result.stderr;
}

fn readSmallFile(io: Io, path: []const u8, buf: []u8) ?[]const u8 {
    const file = Io.Dir.openFileAbsolute(io, path, .{}) catch return null;
    defer file.close(io);
    const len = file.readPositionalAll(io, buf, 0) catch return null;
    return buf[0..len];
}

fn writeSmallFile(io: Io, path: []const u8, content: []const u8) !void {
    const file = try Io.Dir.createFileAbsolute(io, path, .{ .truncate = true });
    defer file.close(io);
    var buf: [512]u8 = undefined;
    var writer = file.writer(io, &buf);
    try writer.interface.writeAll(content);
    try writer.interface.flush();
}

/// Appends JDK 25+ AOT cache options (JEP 514): reuses a cache trained on a
/// previous run when it matches the current JVM and jar, otherwise trains one.
/// Skipped when HUSI_DISABLE_AOT_CACHE is set or the user configured any
/// -XX:AOT option themselves. Never fails the launch.
fn appendAotCacheOptions(
    io: Io,
    allocator: mem.Allocator,
    env_map: *process.Environ.Map,
    java_command: []const u8,
    jar_path: []const u8,
    config_dir: []const u8,
    java_opts: *ArrayList([]u8),
) !void {
    if (env_map.get("HUSI_DISABLE_AOT_CACHE") != null) return;

    for (java_opts.items) |opt| {
        if (mem.startsWith(u8, opt, "-XX:AOT")) return;
    }
    for ([_][]const u8{ "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS" }) |env_name| {
        if (env_map.get(env_name)) |value| {
            if (mem.indexOf(u8, value, "-XX:AOT") != null) return;
        }
    }

    const version_line_raw = (try probeJavaVersionLine(io, allocator, java_command)) orelse return;
    defer allocator.free(version_line_raw);
    const major = parseJavaMajorVersion(version_line_raw) orelse return;
    if (major < 25) return;

    const jar_file = Io.Dir.openFileAbsolute(io, jar_path, .{}) catch return;
    const jar_stat = stat_block: {
        defer jar_file.close(io);
        break :stat_block jar_file.stat(io) catch return;
    };

    // The cache is only valid for the exact JVM build and jar it was trained
    // on; a mismatched key triggers retraining.
    const version_line = mem.trim(u8, version_line_raw, &std.ascii.whitespace);
    const key = try std.fmt.allocPrint(allocator, "{s}|{d}|{d}", .{
        version_line,
        jar_stat.size,
        jar_stat.mtime.nanoseconds,
    });

    const aot_dir = try std.fmt.allocPrint(allocator, "{s}/aot", .{config_dir});
    defer allocator.free(aot_dir);
    const cache_path = try std.fmt.allocPrint(allocator, "{s}/{s}.aot", .{ aot_dir, husi_package_name });
    const key_path = try std.fmt.allocPrint(allocator, "{s}.key", .{cache_path});
    defer allocator.free(key_path);

    var key_buf: [1024]u8 = undefined;
    const stored_key = readSmallFile(io, key_path, &key_buf);
    if (stored_key != null and mem.eql(u8, stored_key.?, key) and fileExists(io, cache_path)) {
        try java_opts.append(allocator, try std.fmt.allocPrint(allocator, "-XX:AOTCache={s}", .{cache_path}));
        return;
    }

    // On a fresh install the config dir may not exist yet either.
    for ([_][]const u8{ config_dir, aot_dir }) |dir| {
        Io.Dir.createDirAbsolute(io, dir, .default_dir) catch |err| switch (err) {
            error.PathAlreadyExists => {},
            else => return,
        };
    }
    Io.Dir.deleteFileAbsolute(io, cache_path) catch {};
    writeSmallFile(io, key_path, key) catch return;
    // This run doubles as the training run; the JVM writes the cache on exit.
    try java_opts.append(allocator, try std.fmt.allocPrint(allocator, "-XX:AOTCacheOutput={s}", .{cache_path}));
}

fn selectJavaCommand(io: Io, allocator: mem.Allocator, env_map: *process.Environ.Map) ![]const u8 {
    if (env_map.get("JAVA_HOME")) |java_home| {
        if (try resolveJavaHomeCommand(io, allocator, java_home)) |bin| {
            return bin;
        }
    }
    if (env_map.get("JAVA")) |java_env| {
        if (java_env.len > 0) {
            return allocator.dupe(u8, java_env);
        }
    }
    if (try resolveMacOSJavaHome(io, allocator)) |java_bin| {
        return java_bin;
    }
    return allocator.dupe(u8, "java");
}

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena;
    const allocator = arena.allocator();

    const runtime = resolveRuntimePaths(io, allocator) catch |err| {
        std.debug.print("resolve_runtime_paths failed: {}\n", .{err});
        return 1;
    };

    const java_opts_template = try std.fmt.allocPrint(allocator, "{s}/desktop-java-opts.conf.template", .{runtime.launcher_dir});
    const app_args_template = try std.fmt.allocPrint(allocator, "{s}/desktop-app-args.conf.template", .{runtime.launcher_dir});

    const user_config = resolveUserConfigPaths(allocator, init.environ_map) catch |err| {
        std.debug.print("resolve_user_config_paths failed: {}\n", .{err});
        return 1;
    };

    var java_opts: ArrayList([]u8) = .empty;
    var app_args: ArrayList([]u8) = .empty;

    if (resolveArgsFile(io, user_config.java_opts_path, java_opts_template)) |path| {
        readArgsFile(io, allocator, path, &java_opts) catch |err| {
            std.debug.print("read java opts file failed: {}\n", .{err});
            return 1;
        };
    }
    if (resolveArgsFile(io, user_config.app_args_path, app_args_template)) |path| {
        readArgsFile(io, allocator, path, &app_args) catch |err| {
            std.debug.print("read app args file failed: {}\n", .{err});
            return 1;
        };
    }

    const java_command = selectJavaCommand(io, allocator, init.environ_map) catch |err| {
        std.debug.print("select_java_command failed: {}\n", .{err});
        return 1;
    };

    appendAotCacheOptions(io, allocator, init.environ_map, java_command, runtime.jar_path, user_config.config_dir, &java_opts) catch |err| {
        std.debug.print("WARN: append_aot_cache_options failed: {}\n", .{err});
    };

    // java [java_opts...] -jar <jar> [app_args...] [passthrough args...]
    var child_argv: ArrayList([]const u8) = .empty;

    try child_argv.append(allocator, java_command);
    if (try resolveMacOSAppBundleOptions(io, allocator, runtime)) |bundle_options| {
        if (bundle_options.dock_name) |dock_name| {
            try child_argv.append(allocator, try std.fmt.allocPrint(allocator, "-Xdock:name={s}", .{dock_name}));
        }
        if (bundle_options.dock_icon_path) |dock_icon_path| {
            try child_argv.append(allocator, try std.fmt.allocPrint(allocator, "-Xdock:icon={s}", .{dock_icon_path}));
        }
    }
    for (java_opts.items) |opt| try child_argv.append(allocator, opt);
    try child_argv.append(allocator, "-jar");
    try child_argv.append(allocator, runtime.jar_path);
    for (app_args.items) |arg| try child_argv.append(allocator, arg);

    var args_iterator = try process.Args.iterateAllocator(init.minimal.args, allocator);
    defer args_iterator.deinit();
    _ = args_iterator.skip();
    while (args_iterator.next()) |arg| {
        try child_argv.append(allocator, arg);
    }

    while (true) {
        var child = try process.spawn(io, .{
            .argv = child_argv.items,
            .create_no_window = native_os == .windows,
        });

        const term = child.wait(io) catch |err| {
            std.debug.print("spawn and wait failed: {}\n", .{err});
            return 1;
        };

        switch (term) {
            .exited => |code| {
                if (code == husi_exit_restart) continue;
                return code;
            },
            else => {
                std.debug.print("unexpected term {}", .{term});
                return 1;
            },
        }
    }
}

const testing = std.testing;
const TmpDir = testing.TmpDir;

fn writeTempFile(io: Io, tmp_dir: TmpDir, sub_path: []const u8, content: []const u8) !void {
    const file = try tmp_dir.dir.createFile(io, sub_path, .{});
    defer file.close(io);
    var buf: [4096]u8 = undefined;
    var writer = file.writer(io, &buf);
    try writer.interface.writeAll(content);
    try writer.interface.flush();
}

fn tmpDirPath(io: Io, tmp_dir: TmpDir, allocator: mem.Allocator, sub_path: []const u8) ![]u8 {
    var path_buf: [Io.Dir.max_path_bytes]u8 = undefined;
    const len = tmp_dir.dir.realPath(io, &path_buf) catch return error.BadPath;
    return std.fmt.allocPrint(allocator, "{s}/{s}", .{ path_buf[0..len], sub_path });
}

test "readArgsFile: basic lines" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "test.conf", "-Xmx512m\n-Dfoo=bar\n");

    const path = try tmpDirPath(io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(io, allocator, path, &list);
    try testing.expectEqual(@as(usize, 2), list.items.len);
    try testing.expectEqualStrings("-Xmx512m", list.items[0]);
    try testing.expectEqualStrings("-Dfoo=bar", list.items[1]);
}

test "readArgsFile: skips comments and blank lines" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "test.conf", "# comment\n\n-Xmx256m\n  # indented comment\n-Dfoo=1\n");

    const path = try tmpDirPath(io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(io, allocator, path, &list);
    try testing.expectEqual(@as(usize, 2), list.items.len);
    try testing.expectEqualStrings("-Xmx256m", list.items[0]);
    try testing.expectEqualStrings("-Dfoo=1", list.items[1]);
}

test "readArgsFile: no trailing newline" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "test.conf", "-Xmx128m");

    const path = try tmpDirPath(io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(io, allocator, path, &list);
    try testing.expectEqual(@as(usize, 1), list.items.len);
    try testing.expectEqualStrings("-Xmx128m", list.items[0]);
}

test "readArgsFile: empty file" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "test.conf", "");

    const path = try tmpDirPath(io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(io, allocator, path, &list);
    try testing.expectEqual(@as(usize, 0), list.items.len);
}

test "readArgsFile: only comments" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "test.conf", "# comment 1\n# comment 2\n");

    const path = try tmpDirPath(io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(io, allocator, path, &list);
    try testing.expectEqual(@as(usize, 0), list.items.len);
}

test "readArgsFile: trims whitespace" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "test.conf", "  -Xmx512m  \n\t-Dfoo=bar\t\n");

    const path = try tmpDirPath(io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(testing.io, allocator, path, &list);
    try testing.expectEqual(@as(usize, 2), list.items.len);
    try testing.expectEqualStrings("-Xmx512m", list.items[0]);
    try testing.expectEqualStrings("-Dfoo=bar", list.items[1]);
}

test "resolveArgsFile: prefers user path" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "user.conf", "a");
    try writeTempFile(io, tmp_dir, "template.conf", "b");

    const user_path = try tmpDirPath(io, tmp_dir, allocator, "user.conf");
    defer allocator.free(user_path);
    const template_path = try tmpDirPath(io, tmp_dir, allocator, "template.conf");
    defer allocator.free(template_path);

    const result = resolveArgsFile(io, user_path, template_path);
    try testing.expect(result != null);
    try testing.expectEqualStrings(user_path, result.?);
}

test "resolveArgsFile: falls back to template" {
    const allocator = testing.allocator;
    const io = testing.io;
    var tmp_dir = testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(io, tmp_dir, "template.conf", "b");

    const user_path = try tmpDirPath(io, tmp_dir, allocator, "user.conf");
    defer allocator.free(user_path);
    const template_path = try tmpDirPath(io, tmp_dir, allocator, "template.conf");
    defer allocator.free(template_path);

    const result = resolveArgsFile(io, user_path, template_path);
    try testing.expect(result != null);
    try testing.expectEqualStrings(template_path, result.?);
}

test "resolveArgsFile: returns null when neither exists" {
    const io = testing.io;
    const result = resolveArgsFile(io, "/nonexistent/user.conf", "/nonexistent/template.conf");
    try testing.expect(result == null);
}

test "resolveConfigBase: linux uses XDG_CONFIG_HOME" {
    if (native_os != .linux) return error.SkipZigTest;

    const allocator = testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();
    try env_map.put("XDG_CONFIG_HOME", "/custom/config");

    const result = try resolveConfigBase(allocator, &env_map);
    defer allocator.free(result);
    try testing.expectEqualStrings("/custom/config", result);
}

test "resolveConfigBase: linux falls back to HOME/.config" {
    if (native_os != .linux) return error.SkipZigTest;

    const allocator = testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();
    try env_map.put("HOME", "/home/testuser");

    const result = try resolveConfigBase(allocator, &env_map);
    defer allocator.free(result);
    try testing.expectEqualStrings("/home/testuser/.config", result);
}

test "resolveUserHome: missing HOME returns error" {
    const allocator = testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();

    const result = resolveUserHome(allocator, &env_map);
    try testing.expectError(error.MissingHome, result);
}

test "resolveUserConfigPaths: produces expected paths" {
    if (native_os != .linux) return error.SkipZigTest;

    const allocator = testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();
    try env_map.put("XDG_CONFIG_HOME", "/tmp/testconfig");

    const paths = try resolveUserConfigPaths(allocator, &env_map);
    defer allocator.free(paths.config_dir);
    defer allocator.free(paths.java_opts_path);
    defer allocator.free(paths.app_args_path);

    try testing.expectEqualStrings("/tmp/testconfig/husi", paths.config_dir);
    try testing.expectEqualStrings("/tmp/testconfig/husi/desktop-java-opts.conf", paths.java_opts_path);
    try testing.expectEqualStrings("/tmp/testconfig/husi/desktop-app-args.conf", paths.app_args_path);
}

test "parseJavaMajorVersion: modern openjdk output" {
    try testing.expectEqual(@as(?u32, 25), parseJavaMajorVersion("openjdk version \"25.0.4\" 2026-07-21\nOpenJDK Runtime Environment"));
}

test "parseJavaMajorVersion: single-component version" {
    try testing.expectEqual(@as(?u32, 21), parseJavaMajorVersion("openjdk version \"21\" 2023-09-19"));
}

test "parseJavaMajorVersion: legacy 1.8 style" {
    try testing.expectEqual(@as(?u32, 1), parseJavaMajorVersion("java version \"1.8.0_392\""));
}

test "parseJavaMajorVersion: garbage returns null" {
    try testing.expectEqual(@as(?u32, null), parseJavaMajorVersion("command not found"));
    try testing.expectEqual(@as(?u32, null), parseJavaMajorVersion("version \"\""));
}

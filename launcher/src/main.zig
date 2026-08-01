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

/// `prepare` returns a boolean about wheather restart or not.
const PlatformPrivilege = switch (native_os) {
    .linux => struct {
        const posix = std.posix;
        const linux = std.os.linux;

        const CAP_VERSION_3: u32 = 0x20080522;
        const CAP_DAC_READ_SEARCH = 2;
        const CAP_NET_BIND_SERVICE = 10;
        const CAP_NET_ADMIN = 12;
        const CAP_NET_RAW = 13;
        const CAP_SETPCAP = 8;
        const CAP_SYS_PTRACE = 19;

        const PR_CAP_AMBIENT = 47;
        const PR_CAP_AMBIENT_RAISE = 2;

        const CapHeader = extern struct {
            version: u32,
            pid: c_int,
        };

        const CapData = extern struct {
            effective: u32,
            permitted: u32,
            inheritable: u32,
        };

        fn parseResult(result: u64) !void {
            switch (posix.errno(result)) {
                .SUCCESS => {},
                else => |err| return posix.unexpectedErrno(err),
            }
        }

        fn capget(header: *CapHeader, data: *[2]CapData) !void {
            const result = linux.syscall2(.capget, @intFromPtr(header), @intFromPtr(data));
            try parseResult(result);
        }

        fn capset(header: *const CapHeader, data: *const [2]CapData) !void {
            const result = linux.syscall2(.capset, @intFromPtr(header), @intFromPtr(data));
            try parseResult(result);
        }

        fn setInheritableCaps(caps: []const c_int) !void {
            var header = CapHeader{
                .version = CAP_VERSION_3,
                .pid = 0,
            };
            var data = [2]CapData{
                .{ .effective = 0, .permitted = 0, .inheritable = 0 },
                .{ .effective = 0, .permitted = 0, .inheritable = 0 },
            };

            try capget(&header, &data);

            for (caps) |cap| {
                const index: u32 = @intCast(@as(u32, @bitCast(cap)) / 32);
                const bit: u32 = @as(u32, 1) << @intCast(@as(u32, @bitCast(cap)) % 32);

                if (index >= 2) {
                    std.debug.print("unsupported capability index: {d}\n", .{cap});
                    return error.UnsupportedCap;
                }
                if ((data[index].permitted & bit) == 0) {
                    std.debug.print("missing permitted capability: {d}\n", .{cap});
                    return error.MissingPermittedCap;
                }
                data[index].inheritable |= bit;
            }

            try capset(&header, &data);
        }

        fn raiseAmbientCaps(caps: []const c_int) !void {
            for (caps) |cap| {
                const result = linux.prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, @intCast(cap), 0, 0);
                try parseResult(result);
            }
        }

        fn dropSetpcap() !void {
            var header = CapHeader{ .version = CAP_VERSION_3, .pid = 0 };
            var data = [2]CapData{
                .{ .effective = 0, .permitted = 0, .inheritable = 0 },
                .{ .effective = 0, .permitted = 0, .inheritable = 0 },
            };

            try capget(&header, &data);

            const index: u32 = @as(u32, CAP_SETPCAP) / 32;
            const bit: u32 = @as(u32, 1) << @intCast(@as(u32, CAP_SETPCAP) % 32);

            data[index].effective &= ~bit;
            data[index].permitted &= ~bit;
            data[index].inheritable &= ~bit;

            try capset(&header, &data);
        }

        fn prepare(io: Io, allocator: mem.Allocator) !bool {
            _ = io;
            _ = allocator;

            const ambient_caps = [_]c_int{
                CAP_NET_ADMIN,
                CAP_NET_RAW,
                CAP_NET_BIND_SERVICE,
                CAP_SYS_PTRACE,
                CAP_DAC_READ_SEARCH,
            };

            try setInheritableCaps(&ambient_caps);
            try raiseAmbientCaps(&ambient_caps);
            try dropSetpcap();
            return false;
        }
    },
    .macos => struct {
        const c = std.c;

        fn isPrivileged(io: Io, exe_path: []const u8) bool {
            const file = Io.Dir.openFileAbsolute(io, exe_path, .{}) catch return false;
            defer file.close(io);
            var stat: c.Stat = undefined;
            if (c.fstat(file.handle, &stat) != 0) return false;
            const S_ISUID = 0o4000;
            return stat.uid == 0 and stat.gid == 0 and (stat.mode & S_ISUID) != 0;
        }

        fn runElevated(io: Io, allocator: mem.Allocator, command: []const u8) !void {
            const script = try std.fmt.allocPrint(allocator, "do shell script \"{s}\" with administrator privileges", .{command});
            defer allocator.free(script);

            var child = try process.spawn(io, .{
                .argv = &.{ "osascript", "-e", script },
                .stdin = .ignore,
                .stdout = .ignore,
                .stderr = .ignore,
            });

            const term = try child.wait(io);
            switch (term) {
                .exited => |code| {
                    if (code == 0) return;
                },
                else => {},
            }
            return error.ElevationFailed;
        }

        fn prepare(io: Io, allocator: mem.Allocator) !bool {
            const exe_path = try findSelfExePath(io);
            if (isPrivileged(io, exe_path)) return false;

            const command = try std.fmt.allocPrint(allocator, "chown root:wheel {s} && chmod u+s {s}", .{ exe_path, exe_path });
            defer allocator.free(command);
            try runElevated(io, allocator, command);
            return true;
        }
    },
    .windows => struct {
        // Already got via mainfest
        fn prepare(io: Io, allocator: mem.Allocator) !bool {
            _ = io;
            _ = allocator;
            return false;
        }
    },
    else => unreachable,
};

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
    defer allocator.free(config_dir);

    const java_opts_path = try std.fmt.allocPrint(allocator, "{s}/desktop-java-opts.conf", .{config_dir});
    errdefer allocator.free(java_opts_path);
    const app_args_path = try std.fmt.allocPrint(allocator, "{s}/desktop-app-args.conf", .{config_dir});

    return UserConfigPaths{
        .java_opts_path = java_opts_path,
        .app_args_path = app_args_path,
    };
}

fn resolveMacOSJavaHome(io: Io, allocator: mem.Allocator) !?[]const u8 {
    if (native_os != .macos) return null;

    const java_version = "21";
    const result = process.run(allocator, io, .{
        .argv = &.{ "/usr/libexec/java_home", "-v", java_version },
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

fn writeTempFile(io: Io, tmp_dir: std.testing.TmpDir, sub_path: []const u8, content: []const u8) !void {
    const file = try tmp_dir.dir.createFile(io, sub_path, .{});
    defer file.close(io);
    var buf: [4096]u8 = undefined;
    var w = file.writer(io, &buf);
    try w.interface.writeAll(content);
    try w.interface.flush();
}

fn tmpDirPath(io: Io, tmp_dir: std.testing.TmpDir, allocator: mem.Allocator, sub_path: []const u8) ![]u8 {
    var path_buf: [Io.Dir.max_path_bytes]u8 = undefined;
    const len = tmp_dir.dir.realPath(io, &path_buf) catch return error.BadPath;
    return std.fmt.allocPrint(allocator, "{s}/{s}", .{ path_buf[0..len], sub_path });
}

test "readArgsFile: basic lines" {
    const allocator = std.testing.allocator;
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "test.conf", "-Xmx512m\n-Dfoo=bar\n");

    const path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(std.testing.io, allocator, path, &list);
    try std.testing.expectEqual(@as(usize, 2), list.items.len);
    try std.testing.expectEqualStrings("-Xmx512m", list.items[0]);
    try std.testing.expectEqualStrings("-Dfoo=bar", list.items[1]);
}

test "readArgsFile: skips comments and blank lines" {
    const allocator = std.testing.allocator;
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "test.conf", "# comment\n\n-Xmx256m\n  # indented comment\n-Dfoo=1\n");

    const path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(std.testing.io, allocator, path, &list);
    try std.testing.expectEqual(@as(usize, 2), list.items.len);
    try std.testing.expectEqualStrings("-Xmx256m", list.items[0]);
    try std.testing.expectEqualStrings("-Dfoo=1", list.items[1]);
}

test "readArgsFile: no trailing newline" {
    const allocator = std.testing.allocator;
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "test.conf", "-Xmx128m");

    const path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(std.testing.io, allocator, path, &list);
    try std.testing.expectEqual(@as(usize, 1), list.items.len);
    try std.testing.expectEqualStrings("-Xmx128m", list.items[0]);
}

test "readArgsFile: empty file" {
    const allocator = std.testing.allocator;
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "test.conf", "");

    const path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(std.testing.io, allocator, path, &list);
    try std.testing.expectEqual(@as(usize, 0), list.items.len);
}

test "readArgsFile: only comments" {
    const allocator = std.testing.allocator;
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "test.conf", "# comment 1\n# comment 2\n");

    const path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(std.testing.io, allocator, path, &list);
    try std.testing.expectEqual(@as(usize, 0), list.items.len);
}

test "readArgsFile: trims whitespace" {
    const allocator = std.testing.allocator;
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "test.conf", "  -Xmx512m  \n\t-Dfoo=bar\t\n");

    const path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "test.conf");
    defer allocator.free(path);

    var list: ArrayList([]u8) = .empty;
    defer {
        for (list.items) |item| allocator.free(item);
        list.deinit(allocator);
    }

    try readArgsFile(std.testing.io, allocator, path, &list);
    try std.testing.expectEqual(@as(usize, 2), list.items.len);
    try std.testing.expectEqualStrings("-Xmx512m", list.items[0]);
    try std.testing.expectEqualStrings("-Dfoo=bar", list.items[1]);
}

test "resolveArgsFile: prefers user path" {
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "user.conf", "a");
    try writeTempFile(std.testing.io, tmp_dir, "template.conf", "b");

    const allocator = std.testing.allocator;
    const user_path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "user.conf");
    defer allocator.free(user_path);
    const template_path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "template.conf");
    defer allocator.free(template_path);

    const result = resolveArgsFile(std.testing.io, user_path, template_path);
    try std.testing.expect(result != null);
    try std.testing.expectEqualStrings(user_path, result.?);
}

test "resolveArgsFile: falls back to template" {
    var tmp_dir = std.testing.tmpDir(.{});
    defer tmp_dir.cleanup();

    try writeTempFile(std.testing.io, tmp_dir, "template.conf", "b");

    const allocator = std.testing.allocator;
    const user_path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "user.conf");
    defer allocator.free(user_path);
    const template_path = try tmpDirPath(std.testing.io, tmp_dir, allocator, "template.conf");
    defer allocator.free(template_path);

    const result = resolveArgsFile(std.testing.io, user_path, template_path);
    try std.testing.expect(result != null);
    try std.testing.expectEqualStrings(template_path, result.?);
}

test "resolveArgsFile: returns null when neither exists" {
    const result = resolveArgsFile(std.testing.io, "/nonexistent/user.conf", "/nonexistent/template.conf");
    try std.testing.expect(result == null);
}

test "resolveConfigBase: linux uses XDG_CONFIG_HOME" {
    if (native_os != .linux) return error.SkipZigTest;

    const allocator = std.testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();
    try env_map.put("XDG_CONFIG_HOME", "/custom/config");

    const result = try resolveConfigBase(allocator, &env_map);
    defer allocator.free(result);
    try std.testing.expectEqualStrings("/custom/config", result);
}

test "resolveConfigBase: linux falls back to HOME/.config" {
    if (native_os != .linux) return error.SkipZigTest;

    const allocator = std.testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();
    try env_map.put("HOME", "/home/testuser");

    const result = try resolveConfigBase(allocator, &env_map);
    defer allocator.free(result);
    try std.testing.expectEqualStrings("/home/testuser/.config", result);
}

test "resolveUserHome: missing HOME returns error" {
    const allocator = std.testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();

    const result = resolveUserHome(allocator, &env_map);
    try std.testing.expectError(error.MissingHome, result);
}

test "resolveUserConfigPaths: produces expected paths" {
    if (native_os != .linux) return error.SkipZigTest;

    const allocator = std.testing.allocator;
    var env_map: process.Environ.Map = .init(allocator);
    defer env_map.deinit();
    try env_map.put("XDG_CONFIG_HOME", "/tmp/testconfig");

    const paths = try resolveUserConfigPaths(allocator, &env_map);
    defer allocator.free(paths.java_opts_path);
    defer allocator.free(paths.app_args_path);

    try std.testing.expectEqualStrings("/tmp/testconfig/husi/desktop-java-opts.conf", paths.java_opts_path);
    try std.testing.expectEqualStrings("/tmp/testconfig/husi/desktop-app-args.conf", paths.app_args_path);
}

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena;
    const allocator = arena.allocator();

    const relaunch_required = PlatformPrivilege.prepare(io, allocator) catch |err| onFailed: {
        std.debug.print("WARN: prepare_launch_environment failed: {}\n", .{err});
        break :onFailed false;
    };
    if (relaunch_required) {
        const args = try init.minimal.args.toSlice(allocator);
        return process.replace(io, .{
            .argv = args,
        });
    }

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

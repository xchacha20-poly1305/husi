const std = @import("std");
const builtin = @import("builtin");
const fs = std.fs;
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

        fn capget(header: *CapHeader, data: *[2]CapData) !void {
            const result = linux.syscall2(.capget, @intFromPtr(header), @intFromPtr(data));
            switch (posix.errno(result)) {
                .SUCCESS => {},
                else => |err| return posix.unexpectedErrno(err),
            }
        }

        fn capset(header: *const CapHeader, data: *const [2]CapData) !void {
            const result = linux.syscall2(.capset, @intFromPtr(header), @intFromPtr(data));
            switch (posix.errno(result)) {
                .SUCCESS => {},
                else => |err| return posix.unexpectedErrno(err),
            }
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
                switch (posix.errno(result)) {
                    .SUCCESS => {},
                    else => |err| return posix.unexpectedErrno(err),
                }
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

        fn prepare(allocator: mem.Allocator) !bool {
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

        fn isPrivileged(exe_path: []const u8) bool {
            const file = fs.openFileAbsolute(exe_path, .{}) catch return false;
            defer file.close();
            var stat: c.Stat = undefined;
            if (c.fstat(file.handle, &stat) != 0) return false;
            const S_ISUID = 0o4000;
            return stat.uid == 0 and stat.gid == 0 and (stat.mode & S_ISUID) != 0;
        }

        fn runElevated(allocator: mem.Allocator, command: []const u8) !void {
            const script = try std.fmt.allocPrint(allocator, "do shell script \"{s}\" with administrator privileges", .{command});
            defer allocator.free(script);

            var child = process.Child.init(&.{ "osascript", "-e", script }, allocator);
            child.stdin_behavior = .Ignore;
            child.stdout_behavior = .Ignore;
            child.stderr_behavior = .Ignore;

            const term = try child.spawnAndWait();
            switch (term) {
                .Exited => |code| {
                    if (code == 0) return;
                },
                else => {},
            }
            return error.ElevationFailed;
        }

        fn prepare(allocator: mem.Allocator) !bool {
            const exe_path = try findSelfExePath();
            if (isPrivileged(exe_path)) return false;

            const command = try std.fmt.allocPrint(allocator, "chown root:wheel {s} && chmod u+s {s}", .{ exe_path, exe_path });
            defer allocator.free(command);
            try runElevated(allocator, command);
            return true;
        }
    },
    .windows => struct {
        // Already got via mainfest
        fn prepare(allocator: mem.Allocator) !bool {
            _ = allocator;
            return false;
        }
    },
    else => unreachable,
};

var self_exe_buf: [fs.max_path_bytes]u8 = undefined;
var self_path: ?[]const u8 = null;

fn findSelfExePath() ![]const u8 {
    if (self_path) |path| {
        return path;
    }
    self_path = try fs.selfExePath(&self_exe_buf);
    return self_path.?;
}

fn restartSelf(allocator: mem.Allocator) !u8 {
    const proc_args = try process.argsAlloc(allocator);
    var child = process.Child.init(proc_args, allocator);
    child.stdin_behavior = .Inherit;
    child.stdout_behavior = .Inherit;
    child.stderr_behavior = .Inherit;
    try child.spawn();
    return 0;
}

fn readArgsFile(allocator: mem.Allocator, path: []const u8, list: *ArrayList([]u8)) !void {
    const file = try fs.openFileAbsolute(path, .{});
    defer file.close();

    // will not too big...
    const read_buf_size = 512;
    var file_buf: [read_buf_size]u8 = undefined;
    var reader = file.reader(&file_buf);

    var ioAllocating: std.Io.Writer.Allocating = .init(allocator);
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

fn fileExists(path: []const u8) bool {
    fs.accessAbsolute(path, .{}) catch return false;
    return true;
}

fn resolveArgsFile(user_path: []const u8, template_path: []const u8) ?[]const u8 {
    if (fileExists(user_path)) return user_path;
    if (fileExists(template_path)) return template_path;
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

fn resolveRuntimePaths(allocator: mem.Allocator) !RuntimePaths {
    const exe_path = try findSelfExePath();

    const launcher_dir_slice = fs.path.dirname(exe_path) orelse return error.BadExePath;
    const launcher_dir = try allocator.dupe(u8, launcher_dir_slice);
    errdefer allocator.free(launcher_dir);

    const direct_jar_path = try std.fmt.allocPrint(allocator, "{s}/app/{s}.jar", .{ launcher_dir_slice, husi_package_name });
    if (fileExists(direct_jar_path)) {
        return RuntimePaths{
            .launcher_dir = launcher_dir,
            .app_root = launcher_dir,
            .jar_path = direct_jar_path,
        };
    }
    allocator.free(direct_jar_path);

    const app_root_slice = fs.path.dirname(launcher_dir_slice) orelse return error.BadExePath;
    const app_root = try allocator.dupe(u8, app_root_slice);
    errdefer allocator.free(app_root);

    const jar_path = try std.fmt.allocPrint(allocator, "{s}/app/{s}.jar", .{ app_root, husi_package_name });

    return RuntimePaths{
        .launcher_dir = launcher_dir,
        .app_root = app_root,
        .jar_path = jar_path,
    };
}

fn resolveMacOSAppBundleOptions(allocator: mem.Allocator, runtime: RuntimePaths) !?MacOSAppBundleOptions {
    if (native_os != .macos) return null;

    const bundle_root_slice = fs.path.dirname(runtime.app_root) orelse return null;
    const bundle_name = fs.path.basename(bundle_root_slice);

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
    const dock_icon_path = if (fileExists(dock_icon_candidate)) dock_icon_candidate else blk: {
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

fn resolveConfigBase(allocator: mem.Allocator) ![]u8 {
    switch (native_os) {
        .linux => {
            if (process.getEnvVarOwned(allocator, "XDG_CONFIG_HOME") catch null) |xdg| {
                if (xdg.len > 0) return xdg;
                allocator.free(xdg);
            }
            const home = try process.getEnvVarOwned(allocator, "HOME");
            defer allocator.free(home);
            return std.fmt.allocPrint(allocator, "{s}/.config", .{home});
        },
        .macos => {
            const home = try process.getEnvVarOwned(allocator, "HOME");
            defer allocator.free(home);
            return std.fmt.allocPrint(allocator, "{s}/Library/Application Support", .{home});
        },
        .windows => {
            return try process.getEnvVarOwned(allocator, "APPDATA");
        },
        else => unreachable,
    }
}

fn resolveUserConfigPaths(allocator: mem.Allocator) !UserConfigPaths {
    const config_base = try resolveConfigBase(allocator);
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

fn resolveMacOSJavaHome(allocator: mem.Allocator) !?[]const u8 {
    if (native_os != .macos) return null;

    const java_version = "21";
    const result = process.Child.run(.{
        .allocator = allocator,
        .argv = &.{ "/usr/libexec/java_home", "-v", java_version },
    }) catch |err| switch (err) {
        error.FileNotFound => return null,
        else => |e| return e,
    };
    defer allocator.free(result.stdout);
    defer allocator.free(result.stderr);

    switch (result.term) {
        .Exited => |code| {
            if (code != 0) return null;
        },
        else => return null,
    }

    const java_home = mem.trim(u8, result.stdout, &std.ascii.whitespace);
    if (java_home.len == 0) return null;

    const java_bin = try std.fmt.allocPrint(allocator, "{s}/bin/java", .{java_home});
    if (fileExists(java_bin)) return java_bin;

    allocator.free(java_bin);
    return null;
}

fn resolveJavaHomeCommand(allocator: mem.Allocator, java_home: []const u8) !?[]const u8 {
    if (java_home.len == 0) return null;

    const candidate_names = if (native_os == .windows)
        [_][]const u8{ "javaw.exe", "java.exe" }
    else
        [_][]const u8{"java"};

    for (candidate_names) |candidate_name| {
        const candidate = try fs.path.join(allocator, &.{ java_home, "bin", candidate_name });
        if (fs.accessAbsolute(candidate, .{})) |_| {
            return candidate;
        } else |_| {
            allocator.free(candidate);
        }
    }

    return null;
}

fn selectJavaCommand(allocator: mem.Allocator) ![]const u8 {
    if (process.getEnvVarOwned(allocator, "JAVA_HOME") catch null) |java_home| {
        defer allocator.free(java_home);
        if (try resolveJavaHomeCommand(allocator, java_home)) |bin| return bin;
    }
    if (process.getEnvVarOwned(allocator, "JAVA") catch null) |java_env| {
        if (java_env.len > 0) return java_env;
        allocator.free(java_env);
    }
    if (try resolveMacOSJavaHome(allocator)) |java_bin| {
        return java_bin;
    }
    return allocator.dupe(u8, "java");
}

pub fn main() !u8 {
    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();
    const allocator = arena.allocator();

    const relaunch_required = PlatformPrivilege.prepare(allocator) catch |err| onFailed: {
        std.debug.print("WARN: prepare_launch_environment failed: {}\n", .{err});
        break :onFailed false;
    };
    if (relaunch_required) {
        return restartSelf(allocator);
    }

    const runtime = resolveRuntimePaths(allocator) catch |err| {
        std.debug.print("resolve_runtime_paths failed: {}\n", .{err});
        return 1;
    };

    const java_opts_template = try std.fmt.allocPrint(allocator, "{s}/desktop-java-opts.conf.template", .{runtime.launcher_dir});
    const app_args_template = try std.fmt.allocPrint(allocator, "{s}/desktop-app-args.conf.template", .{runtime.launcher_dir});

    const user_config = resolveUserConfigPaths(allocator) catch |err| {
        std.debug.print("resolve_user_config_paths failed: {}\n", .{err});
        return 1;
    };

    var java_opts: ArrayList([]u8) = .empty;
    var app_args: ArrayList([]u8) = .empty;

    if (resolveArgsFile(user_config.java_opts_path, java_opts_template)) |path| {
        readArgsFile(allocator, path, &java_opts) catch |err| {
            std.debug.print("read java opts file failed: {}\n", .{err});
            return 1;
        };
    }
    if (resolveArgsFile(user_config.app_args_path, app_args_template)) |path| {
        readArgsFile(allocator, path, &app_args) catch |err| {
            std.debug.print("read app args file failed: {}\n", .{err});
            return 1;
        };
    }

    const java_command = selectJavaCommand(allocator) catch |err| {
        std.debug.print("select_java_command failed: {}\n", .{err});
        return 1;
    };

    const proc_args = try process.argsAlloc(allocator);

    // java [java_opts...] -jar <jar> [app_args...] [passthrough args...]
    var child_argv: ArrayList([]const u8) = .empty;

    try child_argv.append(allocator, java_command);
    if (try resolveMacOSAppBundleOptions(allocator, runtime)) |bundle_options| {
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
    for (proc_args[1..]) |arg| try child_argv.append(allocator, arg);

    while (true) {
        var child = process.Child.init(child_argv.items, allocator);
        child.stdin_behavior = .Inherit;
        child.stdout_behavior = .Inherit;
        child.stderr_behavior = .Inherit;
        if (native_os == .windows) {
            child.create_no_window = true;
        }

        const term = child.spawnAndWait() catch |err| {
            std.debug.print("spawn and wait failed: {}\n", .{err});
            return 1;
        };

        switch (term) {
            .Exited => |code| {
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

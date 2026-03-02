const std = @import("std");
const builtin = @import("builtin");
const linux = std.os.linux;
const posix = std.posix;
const fs = std.fs;
const mem = std.mem;
const process = std.process;
const ArrayList = std.ArrayList;

comptime {
    if (builtin.os.tag != .linux) @compileError("This launcher requires Linux.");
}

const config = @import("config");
const husi_package_name = config.package_name;
const husi_config_dir_name = "husi";
const husi_exit_restart = 50;

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
        const rc = linux.prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, @intCast(cap), 0, 0);
        switch (posix.errno(rc)) {
            .SUCCESS => {},
            else => |err| return posix.unexpectedErrno(err),
        }
    }
}

fn dropSetpcap() !void {
    var header = CapHeader{ .version = CAP_VERSION_3, .pid = 0 };
    var data = [2]CapData{ .{ .effective = 0, .permitted = 0, .inheritable = 0 }, .{ .effective = 0, .permitted = 0, .inheritable = 0 } };

    try capget(&header, &data);

    const index: u32 = @as(u32, CAP_SETPCAP) / 32;
    const bit: u32 = @as(u32, 1) << @intCast(@as(u32, CAP_SETPCAP) % 32);

    data[index].effective &= ~bit;
    data[index].permitted &= ~bit;
    data[index].inheritable &= ~bit;

    try capset(&header, &data);
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

fn touchFile(path: []const u8) !void {
    const file = try fs.createFileAbsolute(path, .{ .exclusive = false });
    file.close();
}

fn ensureUserConfigFile(config_path: []const u8, template_path: []const u8) !void {
    if (fileExists(config_path)) return;
    if (fileExists(template_path)) {
        try fs.cwd().copyFile(template_path, fs.cwd(), config_path, .{});
    } else {
        try touchFile(config_path);
    }
}

const RuntimePaths = struct {
    launcher_dir: []u8,
    app_root: []u8,
    jar_path: []u8,
};

fn resolveRuntimePaths(allocator: mem.Allocator) !RuntimePaths {
    const exe_path = try fs.selfExePathAlloc(allocator);
    defer allocator.free(exe_path);

    const launcher_dir_slice = fs.path.dirname(exe_path) orelse return error.BadExePath;
    const launcher_dir = try allocator.dupe(u8, launcher_dir_slice);
    errdefer allocator.free(launcher_dir);

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

const UserConfigPaths = struct {
    java_opts_path: []u8,
    app_args_path: []u8,
};

fn resolveConfigBase(allocator: mem.Allocator) ![]u8 {
    if (process.getEnvVarOwned(allocator, "XDG_CONFIG_HOME") catch null) |xdg| {
        if (xdg.len > 0) return xdg;
        allocator.free(xdg);
    }
    const home = try process.getEnvVarOwned(allocator, "HOME");
    defer allocator.free(home);
    return std.fmt.allocPrint(allocator, "{s}/.config", .{home});
}

fn resolveUserConfigPaths(allocator: mem.Allocator) !UserConfigPaths {
    const config_base = try resolveConfigBase(allocator);
    defer allocator.free(config_base);

    const config_dir = try std.fmt.allocPrint(allocator, "{s}/{s}", .{ config_base, husi_config_dir_name });
    defer allocator.free(config_dir);

    try fs.cwd().makePath(config_dir);

    const java_opts_path = try std.fmt.allocPrint(allocator, "{s}/desktop-java-opts.conf", .{config_dir});
    errdefer allocator.free(java_opts_path);
    const app_args_path = try std.fmt.allocPrint(allocator, "{s}/desktop-app-args.conf", .{config_dir});

    return UserConfigPaths{
        .java_opts_path = java_opts_path,
        .app_args_path = app_args_path,
    };
}

fn selectJavaCommand(allocator: mem.Allocator) ![]const u8 {
    if (process.getEnvVarOwned(allocator, "JAVA_HOME") catch null) |java_home| {
        defer allocator.free(java_home);
        if (java_home.len > 0) {
            const bin = try std.fmt.allocPrint(allocator, "{s}/bin/java", .{java_home});
            if (fs.accessAbsolute(bin, .{})) |_| {
                return bin;
            } else |_| {
                allocator.free(bin);
            }
        }
    }
    if (process.getEnvVarOwned(allocator, "JAVA") catch null) |java_env| {
        if (java_env.len > 0) return java_env;
        allocator.free(java_env);
    }
    return allocator.dupe(u8, "java");
}

pub fn main() !u8 {
    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();
    const allocator = arena.allocator();

    const ambient_caps = [_]c_int{
        CAP_NET_ADMIN,
        CAP_NET_RAW,
        CAP_NET_BIND_SERVICE,
        CAP_SYS_PTRACE,
        CAP_DAC_READ_SEARCH,
    };

    setInheritableCaps(&ambient_caps) catch |err| {
        std.debug.print("set_inheritable_caps failed: {}\n", .{err});
        return 1;
    };
    raiseAmbientCaps(&ambient_caps) catch |err| {
        std.debug.print("raise_ambient_caps failed: {}\n", .{err});
        return 1;
    };
    dropSetpcap() catch |err| {
        std.debug.print("drop_setpcap failed: {}\n", .{err});
        return 1;
    };

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

    ensureUserConfigFile(user_config.java_opts_path, java_opts_template) catch |err| {
        std.debug.print("ensure java opts config failed: {}\n", .{err});
        return 1;
    };
    ensureUserConfigFile(user_config.app_args_path, app_args_template) catch |err| {
        std.debug.print("ensure app args config failed: {}\n", .{err});
        return 1;
    };

    var java_opts: ArrayList([]u8) = .empty;
    var app_args: ArrayList([]u8) = .empty;

    readArgsFile(allocator, user_config.java_opts_path, &java_opts) catch |err| {
        std.debug.print("read java opts file failed: {}\n", .{err});
        return 1;
    };
    readArgsFile(allocator, user_config.app_args_path, &app_args) catch |err| {
        std.debug.print("read app args file failed: {}\n", .{err});
        return 1;
    };

    const java_command = selectJavaCommand(allocator) catch |err| {
        std.debug.print("select_java_command failed: {}\n", .{err});
        return 1;
    };

    const proc_args = try process.argsAlloc(allocator);

    // java [java_opts...] -jar <jar> [app_args...] [passthrough args...]
    var child_argv: ArrayList([]const u8) = .empty;

    try child_argv.append(allocator, java_command);
    for (java_opts.items) |opt| try child_argv.append(allocator, opt);
    try child_argv.append(allocator, "-jar");
    try child_argv.append(allocator, runtime.jar_path);
    for (app_args.items) |arg| try child_argv.append(allocator, arg);
    for (proc_args[1..]) |arg| try child_argv.append(allocator, arg);

    while (true) {
        var child = std.process.Child.init(child_argv.items, allocator);
        child.stdin_behavior = .Inherit;
        child.stdout_behavior = .Inherit;
        child.stderr_behavior = .Inherit;

        child.spawn() catch |err| {
            std.debug.print("spawn failed: {}\n", .{err});
            return 1;
        };

        const term = child.wait() catch |err| {
            std.debug.print("wait failed: {}\n", .{err});
            return 1;
        };

        switch (term) {
            .Exited => |code| {
                if (code == husi_exit_restart) continue;
                return code;
            },
            else => return 1,
        }
    }
}

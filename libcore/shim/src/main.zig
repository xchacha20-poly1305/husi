//! husi-core process host: a dumb loader for the sibling anja library.
//! Resolves its own realpath, loads libhusicore by absolute path only, calls
//! HusiCoreMain(argc, argv), and exits with the returned code.

const std = @import("std");
const builtin = @import("builtin");
const Io = std.Io;
const mem = std.mem;
const process = std.process;
const posix = std.posix;
const native_os = builtin.os.tag;

comptime {
    switch (native_os) {
        .linux, .macos, .windows => {},
        else => @compileError("husi-core shim only supports Linux, macOS and Windows."),
    }
}

const HusiCoreMainFn = *const fn (argc: c_int, argv: [*][*:0]u8) callconv(.c) c_int;

/// Library basename next to the shim (matches anja -libname=husicore).
const sibling_library_name = switch (native_os) {
    .linux => "libhusicore.so",
    .macos => "libhusicore.dylib",
    .windows => "husicore.dll",
    else => unreachable,
};

var self_exe_buf: [Io.Dir.max_path_bytes]u8 = undefined;

fn findSelfExePath(io: Io) ![]const u8 {
    const size = try process.executablePath(io, &self_exe_buf);
    return self_exe_buf[0..size];
}

fn loadAndRun(lib_path: []const u8, argc: c_int, argv: [*][*:0]u8) !c_int {
    return switch (native_os) {
        .linux, .macos => loadAndRunUnix(lib_path, argc, argv),
        .windows => loadAndRunWindows(lib_path, argc, argv),
        else => unreachable,
    };
}

fn loadAndRunUnix(lib_path: []const u8, argc: c_int, argv: [*][*:0]u8) !c_int {
    // Absolute-path dlopen only — never a search path, never env override.
    const path_z = try posix.toPosixPath(lib_path);
    // RTLD_NOW: fail loud if the library or its deps cannot be resolved.
    const handle = std.c.dlopen(&path_z, .{ .NOW = true }) orelse {
        if (std.c.dlerror()) |msg| {
            std.debug.print("husi-core: dlopen {s}: {s}\n", .{ lib_path, mem.span(msg) });
        } else {
            std.debug.print("husi-core: dlopen failed: {s}\n", .{lib_path});
        }
        return error.LoadFailed;
    };
    defer _ = std.c.dlclose(handle);

    const sym = std.c.dlsym(handle, "HusiCoreMain") orelse {
        std.debug.print("husi-core: symbol HusiCoreMain not found in {s}\n", .{lib_path});
        return error.SymbolNotFound;
    };
    const entry: HusiCoreMainFn = @ptrCast(@alignCast(sym));
    return entry(argc, argv);
}

fn loadAndRunWindows(lib_path: []const u8, argc: c_int, argv: [*][*:0]u8) !c_int {
    var path_w_buf: [std.os.windows.PATH_MAX_WIDE]u16 = undefined;
    const path_w_len = try std.unicode.wtf8ToWtf16Le(path_w_buf[0..], lib_path);
    var path_z: [std.os.windows.PATH_MAX_WIDE + 1]u16 = undefined;
    @memcpy(path_z[0..path_w_len], path_w_buf[0..path_w_len]);
    path_z[path_w_len] = 0;

    // Absolute path only. Flags 0: no DLL search path alteration.
    const handle = LoadLibraryExW(@ptrCast(path_z[0 .. path_w_len + 1].ptr), null, 0) orelse {
        std.debug.print("husi-core: LoadLibraryExW failed: {s}\n", .{lib_path});
        return error.LoadFailed;
    };
    defer _ = FreeLibrary(handle);

    const sym = GetProcAddress(handle, "HusiCoreMain") orelse {
        std.debug.print("husi-core: GetProcAddress HusiCoreMain failed: {s}\n", .{lib_path});
        return error.SymbolNotFound;
    };
    const entry: HusiCoreMainFn = @ptrCast(@alignCast(sym));
    return entry(argc, argv);
}

// Minimal kernel32 imports — Zig 0.16 std does not re-export these.
const LoadLibraryExW = if (native_os == .windows)
    struct {
        extern "kernel32" fn LoadLibraryExW(
            lpLibFileName: [*:0]const u16,
            hFile: ?*anyopaque,
            dwFlags: std.os.windows.DWORD,
        ) callconv(.winapi) ?*anyopaque;
    }.LoadLibraryExW
else
    undefined;

const FreeLibrary = if (native_os == .windows)
    struct {
        extern "kernel32" fn FreeLibrary(hModule: *anyopaque) callconv(.winapi) std.os.windows.BOOL;
    }.FreeLibrary
else
    undefined;

const GetProcAddress = if (native_os == .windows)
    struct {
        extern "kernel32" fn GetProcAddress(
            hModule: *anyopaque,
            lpProcName: [*:0]const u8,
        ) callconv(.winapi) ?*anyopaque;
    }.GetProcAddress
else
    undefined;

pub fn main(init: process.Init) !u8 {
    const io = init.io;
    const allocator = init.arena.allocator();

    const exe_path = findSelfExePath(io) catch |err| {
        std.debug.print("husi-core: resolve executable path: {s}\n", .{@errorName(err)});
        return 1;
    };
    const exe_dir = std.fs.path.dirname(exe_path) orelse {
        std.debug.print("husi-core: cannot resolve executable directory\n", .{});
        return 1;
    };
    const lib_path = try std.fmt.allocPrint(allocator, "{s}/{s}", .{ exe_dir, sibling_library_name });

    // Collect argv as NUL-terminated pointers for the C entry.
    var argv_list: std.ArrayList([*:0]u8) = .empty;
    var args_iterator = try process.Args.iterateAllocator(init.minimal.args, allocator);
    while (args_iterator.next()) |arg| {
        const arg_z = try allocator.dupeZ(u8, arg);
        try argv_list.append(allocator, arg_z.ptr);
    }
    if (argv_list.items.len == 0) {
        const arg_z = try allocator.dupeZ(u8, "husi-core");
        try argv_list.append(allocator, arg_z.ptr);
    }

    const argc: c_int = @intCast(argv_list.items.len);
    const argv: [*][*:0]u8 = argv_list.items.ptr;

    const code = loadAndRun(lib_path, argc, argv) catch return 1;
    if (code < 0) return 1;
    return @intCast(code);
}

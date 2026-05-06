const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});
    const package_name = b.option([]const u8, "package-name", "Package name") orelse "fr.husi";

    const options = b.addOptions();
    options.addOption([]const u8, "package_name", package_name);

    const exe = b.addExecutable(.{
        .name = b.fmt("launcher-{s}-{s}", .{ @tagName(target.result.os.tag), @tagName(target.result.cpu.arch) }),
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/main.zig"),
            .target = target,
            .optimize = optimize,
            .strip = if (optimize != .Debug) true else null,
            .unwind_tables = if (optimize != .Debug) .none else null, // Decrease 10 kB for zig 0.16 boom
            .imports = &.{
                .{ .name = "config", .module = options.createModule() },
            },
        }),
    });
    if (target.result.os.tag == .windows) {
        exe.win32_manifest = b.path("launcher.manifest");
        exe.subsystem = .Windows;
        exe.root_module.addWin32ResourceFile(.{ .file = b.path("icon.rc") });
    }

    b.installArtifact(exe);

    const run_step = b.step("run", "Run the app");
    const run_cmd = b.addRunArtifact(exe);
    run_step.dependOn(&run_cmd.step);
    run_cmd.step.dependOn(b.getInstallStep());
    if (b.args) |args| {
        run_cmd.addArgs(args);
    }

    const test_step = b.step("test", "Run tests");
    const exe_tests = b.addTest(.{
        .root_module = exe.root_module,
    });
    test_step.dependOn(&b.addRunArtifact(exe_tests).step);
}

const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // Core process host: loads the sibling libhusicore and calls HusiCoreMain.
    // Console subsystem on Windows so service/session logs go to the console.
    const exe_module = b.createModule(.{
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
        .strip = if (optimize != .Debug) true else null,
        .unwind_tables = if (optimize != .Debug) .none else null,
        // libc for dlopen on Unix; Windows uses kernel32 externs.
        .link_libc = target.result.os.tag != .windows,
    });
    const exe = b.addExecutable(.{
        .name = b.fmt("husi-core-{s}-{s}", .{ @tagName(target.result.os.tag), @tagName(target.result.cpu.arch) }),
        .root_module = exe_module,
    });
    if (target.result.os.tag == .windows) {
        exe.subsystem = .Console;
    }
    b.installArtifact(exe);

    const test_step = b.step("test", "Run tests");
    const exe_tests = b.addTest(.{
        .root_module = exe_module,
    });
    test_step.dependOn(&b.addRunArtifact(exe_tests).step);
}

GOROOT = $(shell go env GOROOT)
GO_PATCH_1230 = "https://github.com/golang/go/commit/76a8409eb81eda553363783dcdd9d6224368ae0e.patch"
GO_PATCH_1234 = "https://github.com/golang/go/commit/59b7d40774b29bd1da1aa624f13233111aff4ad2.patch"
CLIP = sh -c 'if [ -n "$$WAYLAND_DISPLAY" ]; then exec wl-copy; \
              elif [ -n "$$DISPLAY" ]; then exec xclip -selection clipboard; \
              else echo "No display detected (WAYLAND_DISPLAY/DISPLAY missing)"; exit 1; fi'
DESKTOP_TARGETS_COMMON = linux/amd64,linux/arm64,darwin/amd64,darwin/arm64,windows/amd64,windows/arm64
DESKTOP_TARGETS_LINUX = linux/amd64 linux/arm64
DESKTOP_TARGETS_WINDOWS = windows/amd64 windows/arm64
LINUX_PACKAGE_FORMATS ?= deb,rpm,pacman
HOST_OS = $(shell uname -s)
DESKTOP_TARGET_GRADLE_ARG = $(if $(DESKTOP_TARGET),-PdesktopTarget=$(DESKTOP_TARGET),)
DESKTOP_TARGET_SCRIPT_ARG = $(if $(DESKTOP_TARGET),--target $(DESKTOP_TARGET),)
JNI_INCLUDE_SCRIPT_ARG = $(if $(JNI_INCLUDE),--jniinclude "$(JNI_INCLUDE)",)
DARWIN_SDK_SCRIPT_ARG = $(if $(DARWIN_SDK),--darwinsdk "$(DARWIN_SDK)",)
NO_NAIVE_SCRIPT_ARG = $(if $(filter 1,$(NO_NAIVE)),--no-naive,)
WINDOWS_NO_SIGN_SCRIPT_ARG = $(if $(filter 1,$(WINDOWS_NO_SIGN)),--no-sign,)
LAUNCHER_ZIG_TARGET = $(subst linux/amd64,x86_64-linux-musl,$(subst linux/arm64,aarch64-linux-musl,$(subst darwin/amd64,x86_64-macos,$(subst darwin/arm64,aarch64-macos,$(subst windows/amd64,x86_64-windows,$(subst windows/arm64,aarch64-windows,$(DESKTOP_TARGET)))))))
LAUNCHER_ZIG_TARGET_ARG = $(if $(LAUNCHER_ZIG_TARGET),-Dtarget=$(LAUNCHER_ZIG_TARGET),)

# make expands DESKTOP_TARGETS into a single literal word, so the shell's IFS
# never gets to split it — commas have to become spaces here instead.
COMMA = ,

# ABI floors for the husi-core shim, pinned to match the libhusicore.* sidecar it
# loads: the Go library imports nothing newer than glibc 2.17, and libcore/build.sh
# targets macOS 12.0. Left unpinned, zig picks up statx/copy_file_range/getrandom
# and would make the shim refuse to start on systems the core itself supports.
CORE_SHIM_GLIBC_VERSION = 2.17
CORE_SHIM_MACOS_VERSION = 12.0

.PHONY: libcore libcore_android libcore_desktop_common libcore_desktop core_desktop aboutlibraries aboutlibraries_go aboutlibraries_android aboutlibraries_desktop apk apk_debug assets desktop desktop_release desktop_package desktop_package_linux desktop_package_linux_all desktop_package_macos desktop_package_windows desktop_package_windows_all desktop_uberjar launcher lint_go proto proto_install test_go test_no_go_core_binary test_zig plugin generate_option lint_go_linux lint_go_android lint_go_windows

build: libcore_android assets apk

libcore:
	./run lib core --desktop $(JNI_INCLUDE_SCRIPT_ARG) $(DARWIN_SDK_SCRIPT_ARG) $(NO_NAIVE_SCRIPT_ARG)

libcore_android:
	./run lib core --android

libcore_desktop_common:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=$(DESKTOP_TARGETS_COMMON)

libcore_desktop:
	@if [ -z "$(DESKTOP_TARGETS)" ]; then \
		echo "DESKTOP_TARGETS is required, e.g. make libcore_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64"; \
		exit 1; \
	fi
	./run lib core --desktop --desktoptargets $(DESKTOP_TARGETS) $(JNI_INCLUDE_SCRIPT_ARG) $(DARWIN_SDK_SCRIPT_ARG) $(NO_NAIVE_SCRIPT_ARG)

# Build the Zig husi-core shim and install it next to the anja sidecar in
# libcore/build/<os>_<arch>/ (N2/N7). Sidecar comes from `make libcore`.
core_desktop:
	@if [ -z "$(DESKTOP_TARGETS)" ]; then \
		echo "DESKTOP_TARGETS is required, e.g. make core_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64"; \
		exit 1; \
	fi
	@for target in $(subst $(COMMA), ,$(DESKTOP_TARGETS)); do \
		target=$$(echo "$$target" | tr -d '[:space:]'); \
		[ -z "$$target" ] && continue; \
		if [ "$$target" = "host" ]; then \
			target="$$(cd libcore && go env GOOS)/$$(cd libcore && go env GOARCH)"; \
		fi; \
		platform="$${target%%/*}"; \
		arch="$${target#*/}"; \
		case "$$platform/$$arch" in \
			linux/amd64) zig_target=x86_64-linux-gnu.$(CORE_SHIM_GLIBC_VERSION); zig_os=linux; zig_arch=x86_64; bin_name=husi-core; lib_name=libhusicore.so ;; \
			linux/arm64) zig_target=aarch64-linux-gnu.$(CORE_SHIM_GLIBC_VERSION); zig_os=linux; zig_arch=aarch64; bin_name=husi-core; lib_name=libhusicore.so ;; \
			darwin/amd64) zig_target=x86_64-macos.$(CORE_SHIM_MACOS_VERSION); zig_os=macos; zig_arch=x86_64; bin_name=husi-core; lib_name=libhusicore.dylib ;; \
			darwin/arm64) zig_target=aarch64-macos.$(CORE_SHIM_MACOS_VERSION); zig_os=macos; zig_arch=aarch64; bin_name=husi-core; lib_name=libhusicore.dylib ;; \
			windows/amd64) zig_target=x86_64-windows; zig_os=windows; zig_arch=x86_64; bin_name=husi-core.exe; lib_name=husicore.dll ;; \
			windows/arm64) zig_target=aarch64-windows; zig_os=windows; zig_arch=aarch64; bin_name=husi-core.exe; lib_name=husicore.dll ;; \
			*) echo "Unsupported DESKTOP_TARGETS entry: $$target"; exit 1 ;; \
		esac; \
		echo ">> Building husi-core shim for $$target ($$zig_target)"; \
		(cd libcore/shim && zig build -Doptimize=ReleaseSmall -Dtarget=$$zig_target) || exit $$?; \
		out_dir="libcore/build/$${platform}_$${arch}"; \
		mkdir -p "$$out_dir"; \
		shim_src="libcore/shim/zig-out/bin/husi-core-$${zig_os}-$${zig_arch}"; \
		if [ "$$platform" = "windows" ]; then shim_src="$${shim_src}.exe"; fi; \
		cp -f "$$shim_src" "$$out_dir/$$bin_name"; \
		chmod 755 "$$out_dir/$$bin_name" 2>/dev/null || true; \
		if [ -f "$$out_dir/$$lib_name" ]; then \
			echo ">> Sidecar present: $$out_dir/$$lib_name"; \
		else \
			echo ">> Warning: sidecar $$out_dir/$$lib_name missing (build with make libcore / libcore_desktop first)"; \
		fi; \
		echo ">> Installed $$out_dir/$$bin_name"; \
	done

desktop:
	BUILD_PLUGIN=none ./gradlew -p composeApp run

desktop_release:
	BUILD_PLUGIN=none ./gradlew -p composeApp runRelease

desktop_package:
ifeq ($(HOST_OS),Linux)
	$(MAKE) desktop_package_linux $(if $(DESKTOP_TARGET),DESKTOP_TARGET=$(DESKTOP_TARGET),) LINUX_PACKAGE_FORMATS=$(LINUX_PACKAGE_FORMATS)
else ifeq ($(HOST_OS),Darwin)
	$(MAKE) desktop_package_macos $(if $(DESKTOP_TARGET),DESKTOP_TARGET=$(DESKTOP_TARGET),)
else ifneq (,$(filter MINGW% MSYS% CYGWIN%,$(HOST_OS)))
	$(MAKE) desktop_package_windows $(if $(DESKTOP_TARGET),DESKTOP_TARGET=$(DESKTOP_TARGET),)
else
	@echo "desktop_package only supports Linux, macOS or Windows/MSYS hosts."
	@exit 1
endif

desktop_package_linux:
	BUILD_PLUGIN=none ./gradlew -p composeApp packageUberJarForCurrentOS $(DESKTOP_TARGET_GRADLE_ARG)
	$(MAKE) launcher
	$(MAKE) core_desktop DESKTOP_TARGETS=$(if $(DESKTOP_TARGET),$(DESKTOP_TARGET),host)
	./release/linux/package.sh --formats $(LINUX_PACKAGE_FORMATS) $(DESKTOP_TARGET_SCRIPT_ARG)

desktop_package_linux_all:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=linux/amd64,linux/arm64
	$(MAKE) core_desktop DESKTOP_TARGETS=linux/amd64,linux/arm64
	@for desktop_target in $(DESKTOP_TARGETS_LINUX); do \
		$(MAKE) desktop_package_linux DESKTOP_TARGET=$$desktop_target LINUX_PACKAGE_FORMATS=$(LINUX_PACKAGE_FORMATS) || exit $$?; \
	done

desktop_package_macos:
	@if [ "$(HOST_OS)" != "Darwin" ] && [ -z "$(DESKTOP_TARGET)" ]; then \
		echo "desktop_package_macos on non-macOS hosts requires DESKTOP_TARGET, e.g. make desktop_package_macos DESKTOP_TARGET=darwin/arm64"; \
		exit 1; \
	fi
	BUILD_PLUGIN=none ./gradlew -p composeApp packageUberJarForCurrentOS $(DESKTOP_TARGET_GRADLE_ARG)
	$(MAKE) launcher
	$(MAKE) core_desktop DESKTOP_TARGETS=$(if $(DESKTOP_TARGET),$(DESKTOP_TARGET),host)
	./release/macos/package.sh $(DESKTOP_TARGET_SCRIPT_ARG)

desktop_package_windows:
	@if [ -z "$(DESKTOP_TARGET)" ]; then \
		echo "desktop_package_windows requires DESKTOP_TARGET, e.g. make desktop_package_windows DESKTOP_TARGET=windows/amd64"; \
		exit 1; \
	fi
	BUILD_PLUGIN=none ./gradlew -p composeApp packageUberJarForCurrentOS $(DESKTOP_TARGET_GRADLE_ARG)
	$(MAKE) launcher
	$(MAKE) core_desktop DESKTOP_TARGETS=$(DESKTOP_TARGET)
	./release/windows/package.sh $(DESKTOP_TARGET_SCRIPT_ARG) $(WINDOWS_NO_SIGN_SCRIPT_ARG)

desktop_package_windows_all:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=windows/amd64,windows/arm64
	$(MAKE) core_desktop DESKTOP_TARGETS=windows/amd64,windows/arm64
	@for desktop_target in $(DESKTOP_TARGETS_WINDOWS); do \
		$(MAKE) desktop_package_windows DESKTOP_TARGET=$$desktop_target WINDOWS_NO_SIGN=$(WINDOWS_NO_SIGN) || exit $$?; \
	done

# Thin release jar (natives/** excluded). Place husi-core + libhusicore.* next to the
# jar (or put husi-core on PATH with the library beside that binary) before running.
desktop_uberjar:
	BUILD_PLUGIN=none ./gradlew packageUberJarForCurrentOS $(DESKTOP_TARGET_GRADLE_ARG)

aboutlibraries: aboutlibraries_go aboutlibraries_android aboutlibraries_desktop

aboutlibraries_go:
	cd libcore && go run ./cmd/licencecollect -d ../composeApp/src/commonMain/aboutlibraries/libraries -clean

aboutlibraries_android:
	./gradlew :composeApp:exportLibraryDefinitions

aboutlibraries_desktop:
	./gradlew :composeApp:exportLibraryDefinitionsDesktop $(DESKTOP_TARGET_GRADLE_ARG)

launcher:
	cd launcher && zig build -Doptimize=ReleaseSmall $(LAUNCHER_ZIG_TARGET_ARG)

apk:
	BUILD_PLUGIN=none ./gradlew androidApp:assembleFossRelease

apk_debug:
	BUILD_PLUGIN=none ./gradlew androidApp:assembleFossDebug

assets:
	./run lib assets

proto:
	./run proto

proto_install:
	@echo "protoc: install via your package manager (e.g. pacman -S protobuf)."
	@echo "Go plugins are built from libcore/go.mod pins automatically."

lint_go: lint_go_linux lint_go_android lint_go_windows

lint_go_linux:
	cd libcore/ && GOOS=linux golangci-lint run ./...

lint_go_android:
	cd libcore/ && GOOS=android GOARCH=arm64 golangci-lint run ./...

lint_go_windows:
	cd libcore/ && GOOS=windows GOARCH=amd64 CGO_ENABLED=1 \
		CC="zig cc -target x86_64-windows-gnu" \
		CXX="zig c++ -target x86_64-windows-gnu" \
		golangci-lint run ./...

lint_go_install:
	go install -v github.com/golangci/golangci-lint/v2/cmd/golangci-lint@latest

fmt_go:
	cd libcore/ && gofumpt -l -w .
	cd libcore/ && gofmt -s -w .
	cd libcore/ && gci write --custom-order -s standard -s localmodule -s "prefix(github.com/sagernet/)" -s "default" --skip-generated .

fmt_go_install:
	go install -v mvdan.cc/gofumpt@latest
	go install -v github.com/daixiang0/gci@latest

test: test_gradle test_go test_no_go_core_binary test_zig

test_gradle:
	./gradlew :composeApp:allTests

test_go:
	cd libcore/ && go test -v -count=1 ./...

# Guard: desktop core is the Zig shim + anja sidecar; no second Go link of cmd/husi-core.
test_no_go_core_binary:
	@if grep -nE 'go[[:space:]]+build.*cmd/husi-core|\./cmd/husi-core' libcore/build.sh; then \
		echo "libcore/build.sh must not go-build a desktop husi-core binary (use the Zig shim)"; \
		exit 1; \
	fi
	@echo ">> OK: no go build of cmd/husi-core in libcore/build.sh"

# Two independent Zig projects: the UI launcher and the husi-core process host.
test_zig:
	cd launcher && zig build test
	cd libcore/shim && zig build test

plugin:
	BUILD_PLUGIN=$(PLUGIN) ./gradlew :plugin:$(PLUGIN):assembleFossRelease

generate_option:
	cd ./libcore/cmd/boxoption && go run . | $(CLIP)

patch_go1230:
	curl $(GO_PATCH_1230) | sudo patch --verbose -p 1 -d $(GOROOT)

patch_go1234:
	curl $(GO_PATCH_1234) | sudo patch --verbose -p 1 -d $(GOROOT)

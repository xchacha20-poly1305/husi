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
LAUNCHER_ZIG_TARGET = $(subst linux/amd64,x86_64-linux-musl,$(subst linux/arm64,aarch64-linux-musl,$(subst darwin/amd64,x86_64-macos,$(subst darwin/arm64,aarch64-macos,$(subst windows/amd64,x86_64-windows,$(subst windows/arm64,aarch64-windows,$(DESKTOP_TARGET)))))))
LAUNCHER_ZIG_TARGET_ARG = $(if $(LAUNCHER_ZIG_TARGET),-Dtarget=$(LAUNCHER_ZIG_TARGET),)

.PHONY: update libcore libcore_android libcore_desktop_common libcore_desktop aboutlibraries aboutlibraries_go aboutlibraries_android aboutlibraries_desktop apk apk_debug assets desktop desktop_release desktop_package desktop_package_linux desktop_package_linux_all desktop_package_macos desktop_package_windows desktop_package_windows_all desktop_uberjar launcher lint_go test_go plugin generate_option

build: libcore_android assets apk

libcore:
	./run lib core --desktop $(JNI_INCLUDE_SCRIPT_ARG) $(DARWIN_SDK_SCRIPT_ARG)

libcore_android:
	./run lib core --android

libcore_desktop_common:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=$(DESKTOP_TARGETS_COMMON)

libcore_desktop:
	@if [ -z "$(DESKTOP_TARGETS)" ]; then \
		echo "DESKTOP_TARGETS is required, e.g. make libcore_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64"; \
		exit 1; \
	fi
	./run lib core --desktop --desktoptargets $(DESKTOP_TARGETS) $(JNI_INCLUDE_SCRIPT_ARG) $(DARWIN_SDK_SCRIPT_ARG)

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
	./release/linux/package.sh --formats $(LINUX_PACKAGE_FORMATS) $(DESKTOP_TARGET_SCRIPT_ARG)

desktop_package_linux_all:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=linux/amd64,linux/arm64
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
	./release/macos/package.sh $(DESKTOP_TARGET_SCRIPT_ARG)

desktop_package_windows:
	@if [ -z "$(DESKTOP_TARGET)" ]; then \
		echo "desktop_package_windows requires DESKTOP_TARGET, e.g. make desktop_package_windows DESKTOP_TARGET=windows/amd64"; \
		exit 1; \
	fi
	BUILD_PLUGIN=none ./gradlew -p composeApp packageUberJarForCurrentOS $(DESKTOP_TARGET_GRADLE_ARG)
	$(MAKE) launcher
	./release/windows/package.sh $(DESKTOP_TARGET_SCRIPT_ARG)

desktop_package_windows_all:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=windows/amd64,windows/arm64
	@for desktop_target in $(DESKTOP_TARGETS_WINDOWS); do \
		$(MAKE) desktop_package_windows DESKTOP_TARGET=$$desktop_target || exit $$?; \
	done

desktop_uberjar:
	BUILD_PLUGIN=none ./gradlew packageUberJarForCurrentOS $(DESKTOP_TARGET_GRADLE_ARG)

aboutlibraries: aboutlibraries_go aboutlibraries_android aboutlibraries_desktop

aboutlibraries_go:
	cd libcore && go run -tags=with_gvisor,with_quic,with_wireguard,with_utls ./cmd/licencecollect -d ../composeApp/src/commonMain/aboutlibraries/libraries -clean

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

update:
	./run lib update

lint_go:
	cd libcore/ && GOOS=android golangci-lint run ./...

lint_go_install:
	go install -v github.com/golangci/golangci-lint/cmd/golangci-lint@latest

fmt_go:
	cd libcore/ && gofumpt -l -w .
	cd libcore/ && gofmt -s -w .
	cd libcore/ && gci write --custom-order -s standard -s localmodule -s "prefix(github.com/sagernet/)" -s "default" --skip-generated .

fmt_go_install:
	go install -v mvdan.cc/gofumpt@latest
	go install -v github.com/daixiang0/gci@latest

test: test_gradle test_go

test_gradle:
	./gradlew :composeApp:allTests

test_go:
	cd libcore/ && go test -v -count=1 ./...

plugin:
	BUILD_PLUGIN=$(PLUGIN) ./gradlew :plugin:$(PLUGIN):assembleFossRelease --configuration-cache

generate_option:
	cd ./libcore/cmd/boxoption && go run . | $(CLIP)

patch_go1230:
	curl $(GO_PATCH_1230) | sudo patch --verbose -p 1 -d $(GOROOT)

patch_go1234:
	curl $(GO_PATCH_1234) | sudo patch --verbose -p 1 -d $(GOROOT)

GOROOT = $(shell go env GOROOT)
GO_PATCH_1230 = "https://github.com/golang/go/commit/76a8409eb81eda553363783dcdd9d6224368ae0e.patch"
GO_PATCH_1234 = "https://github.com/golang/go/commit/59b7d40774b29bd1da1aa624f13233111aff4ad2.patch"
CLIP = sh -c 'if [ -n "$$WAYLAND_DISPLAY" ]; then exec wl-copy; \
              elif [ -n "$$DISPLAY" ]; then exec xclip -selection clipboard; \
              else echo "No display detected (WAYLAND_DISPLAY/DISPLAY missing)"; exit 1; fi'
DESKTOP_TARGETS_COMMON = linux/amd64,linux/arm64,darwin/amd64,darwin/arm64,windows/amd64,windows/arm64
LINUX_PACKAGE_FORMATS ?= deb,rpm,pacman

.PHONY: update libcore libcore_android libcore_desktop_host libcore_desktop_common libcore_desktop apk apk_debug assets desktop desktop_release desktop_package desktop_package_linux desktop_uberjar lint_go test_go plugin generate_option

build: libcore_android assets apk

libcore:
	./run lib core --desktop

libcore_android:
	./run lib core --android

libcore_desktop_host:
	$(MAKE) libcore

libcore_desktop_common:
	$(MAKE) libcore_desktop DESKTOP_TARGETS=$(DESKTOP_TARGETS_COMMON)

libcore_desktop:
	@if [ -z "$(DESKTOP_TARGETS)" ]; then \
		echo "DESKTOP_TARGETS is required, e.g. make libcore_desktop DESKTOP_TARGETS=linux/amd64,darwin/arm64"; \
		exit 1; \
	fi
	./run lib core --desktop --desktoptargets $(DESKTOP_TARGETS)

desktop:
	BUILD_PLUGIN=none ./gradlew -p composeApp run

desktop_release:
	BUILD_PLUGIN=none ./gradlew -p composeApp runRelease

desktop_package:
	BUILD_PLUGIN=none ./gradlew packageUberJarForCurrentOS

desktop_package_linux:
	BUILD_PLUGIN=none ./gradlew -p composeApp packageUberJarForCurrentOS
	./release/linux/package-native.sh --formats $(LINUX_PACKAGE_FORMATS)

desktop_uberjar:
	$(MAKE) desktop_package

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
	cd libcore/ && gci write --custom-order -s standard -s "prefix(github.com/sagernet/)" -s "default" .

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

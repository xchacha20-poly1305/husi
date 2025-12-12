GOROOT = $(shell go env GOROOT)
GO_PATCH_1230 = "https://github.com/golang/go/commit/76a8409eb81eda553363783dcdd9d6224368ae0e.patch"
GO_PATCH_1234 = "https://github.com/golang/go/commit/59b7d40774b29bd1da1aa624f13233111aff4ad2.patch"
CLIP = sh -c 'if [ -n "$$WAYLAND_DISPLAY" ]; then exec wl-copy; \
              elif [ -n "$$DISPLAY" ]; then exec xclip -selection clipboard; \
              else echo "No display detected (WAYLAND_DISPLAY/DISPLAY missing)"; exit 1; fi'

.PHONY: update libcore libcore_legacy apk apk_foss apk_legacy apk_debug assets lint_go test_go plugin generate_option

build: libcore assets apk

libcore:
	./run lib core

libcore_legacy:
	DISABLE_NAIVE=1 ./run lib core

apk_foss:
	BUILD_PLUGIN=none ./gradlew app:assembleFossRelease

apk_legacy:
	BUILD_PLUGIN=none ./gradlew app:assembleFossLegacyRelease

apk:
	BUILD_PLUGIN=none ./gradlew clean app:assembleFossRelease app:assembleFossLegacyRelease

apk_debug:
	BUILD_PLUGIN=none ./gradlew app:assembleFossDebug

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
	./gradlew testFossDebugUnitTest

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

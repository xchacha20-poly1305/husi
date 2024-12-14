GOROOT = $(shell go env GOROOT)
GO_PATCH_1234 := $(shell realpath ./libcore/patches/cgo_go1234.diff)

.PHONY: update libcore apk apk_debug assets lint_go test_go plugin generate_option

build: libcore assets apk

libcore:
	./run lib core

apk:
	BUILD_PLUGIN=none ./gradlew app:assembleFossRelease

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

test_go:
	cd libcore/ && go test -v -count=1 ./...

plugin:
	./gradlew :plugin:$(PLUGIN):assembleFossRelease

generate_option:
	cd ./libcore/cmd/boxoption && go run . | xclip -selection clipboard

patch_go1230:
	curl "https://github.com/golang/go/commit/76a8409eb81eda553363783dcdd9d6224368ae0e.patch" | sudo patch --verbose -p 1 -d $(GOROOT)

patch_go1234:
	sudo patch --verbose -p 1 -d $(GOROOT) -i $(GO_PATCH_1234)

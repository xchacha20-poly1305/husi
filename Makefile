.PHONY: update libcore dashboard apk apk_debug assets lint_go test_go

libcore:
	./run lib core

dashboard:
	./run lib dashboard

apk:
	BUILD_PLUGIN ./gradlew app:assembleFossRelease

apk_debug:
	BUILD_PLUGIN=none ./gradlew app:assembleFossDebug

assets:
	./run lib assets

build: libcore dashboard assets apk

update:
	./run lib update

hysteria2:
	./gradlew :plugin:hysteria2:assembleFossRelease

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
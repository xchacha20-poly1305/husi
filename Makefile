.PHONY: update libcore dashboard apk apk_debug assets lint

libcore:
	./run lib core

dashboard:
	./run lib dashboard

apk:
	./gradlew app:assembleFossRelease

apk_debug:
	./gradlew app:assembleFossDebug

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
	@gofumpt -l -w ./libcore
	@gofmt -s -w ./libcore
	@gci write --custom-order -s standard -s "prefix(github.com/sagernet/)" -s "default" ./libcore

fmt_go_install:
	go install -v mvdan.cc/gofumpt@latest
	go install -v github.com/daixiang0/gci@latest

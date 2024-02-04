.PHONY: update libcore dashboard apk apk_debug assets

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
	npm update -g
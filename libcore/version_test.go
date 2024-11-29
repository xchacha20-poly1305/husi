package libcore

import "testing"

func Test_VersionBox(t *testing.T) {
	version := VersionBox()
	t.Logf("Box version: %s", version)
}

func Test_Version(t *testing.T) {
	version := Version()
	t.Logf("Detail version: %s", version)
}

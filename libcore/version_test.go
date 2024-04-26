package libcore

import (
	"testing"
)

func TestVersionBox(t *testing.T) {
	version := VersionBox()
	t.Logf("Box version: %s", version)
}

func TestVersion(t *testing.T) {
	version := Version()
	t.Logf("Detail version: %s", version)
}

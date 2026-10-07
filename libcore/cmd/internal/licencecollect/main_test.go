package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestWriteLibrariesCleansGeneratedFilesOnly(t *testing.T) {
	dir := t.TempDir()
	err := os.WriteFile(filepath.Join(dir, "go_old.json"), []byte("{}"), 0o644)
	require.NoError(t, err)
	err = os.WriteFile(filepath.Join(dir, "husi.json"), []byte("{}"), 0o644)
	require.NoError(t, err)

	err = writeToDir(dir, []Library{{
		UniqueID:        "github.com/example/module",
		ArtifactVersion: "v1.0.0",
		Name:            "github.com/example/module",
		Licenses:        []string{"MIT"},
	}}, true)
	require.NoError(t, err)

	if _, err = os.Stat(filepath.Join(dir, "go_old.json")); !os.IsNotExist(err) {
		require.NoFileExists(t, filepath.Join(dir, "go_old.json"))
	}
	if _, err = os.Stat(filepath.Join(dir, "husi.json")); err != nil {
		require.NoError(t, err)
	}
	outputFile := filepath.Join(dir, "go_github.com_example_module.json")
	content, err := os.ReadFile(outputFile)
	require.NoError(t, err)
	var library Library
	err = json.Unmarshal(content, &library)
	require.NoError(t, err)
	assert.Equal(t, "github.com/example/module", library.UniqueID)
}

func TestModuleWebsite(t *testing.T) {
	tests := []struct {
		path string
		want string
	}{
		{"github.com/sagernet/sing", "https://github.com/sagernet/sing"},
		{"github.com/example/module/v2", "https://github.com/example/module"},
		{"golang.org/x/net", "https://golang.org/x/net"},
		{"gopkg.in/yaml.v3", "https://gopkg.in/yaml.v3"},
	}
	for _, tt := range tests {
		assert.Equal(t, tt.want, moduleWebsite(tt.path), tt.path)
	}
}

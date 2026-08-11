package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime/debug"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/pkgsite-go"
)

func TestResolveLibraryFallbacksToLatestModuleForUnindexedVersion(t *testing.T) {
	const (
		modulePath = "github.com/sagernet/netlink"
		version    = "v0.0.0-20240612041022-b9a21c07ac6a"
		repoURL    = "https://github.com/sagernet/netlink"
	)

	var requests []string
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requests = append(requests, request.URL.String())
		if request.URL.Path == "/fetch/"+modulePath+"@"+version {
			writer.WriteHeader(http.StatusInternalServerError)
			return
		}
		if request.URL.Path != "/v1beta/module/"+modulePath {
			assert.Equal(t, "/v1beta/module/"+modulePath, request.URL.Path)
			writer.WriteHeader(http.StatusNotFound)
			return
		}
		if !assert.Equal(t, "true", request.URL.Query().Get("licenses")) {
			writer.WriteHeader(http.StatusBadRequest)
			return
		}
		switch request.URL.Query().Get("version") {
		case version:
			writer.WriteHeader(http.StatusNotFound)
			_ = json.NewEncoder(writer).Encode(pkgsite.Error{
				Code:    http.StatusNotFound,
				Message: "not found",
			})
		case "":
			_ = json.NewEncoder(writer).Encode(pkgsite.Module{
				Path:    modulePath,
				RepoURL: repoURL,
				Licenses: []pkgsite.License{{
					Types: []string{"Apache-2.0"},
				}},
			})
		default:
			assert.Failf(t, "unexpected version", "version = %q, want %q or empty", request.URL.Query().Get("version"), version)
			writer.WriteHeader(http.StatusBadRequest)
		}
	}))
	defer server.Close()

	library, err := resolveLibrary(context.Background(), pkgsite.NewClient(pkgsite.WithServer(server.URL)), &debug.Module{
		Path:    modulePath,
		Version: version,
	})
	require.NoError(t, err)
	assert.Equal(t, modulePath, library.UniqueID)
	assert.Equal(t, version, library.ArtifactVersion)
	assert.Equal(t, modulePath, library.Name)
	assert.Equal(t, repoURL, library.Website)
	assert.Equal(t, []string{"Apache-2.0"}, library.Licenses)
	assert.Equal(t, []string{
		"/v1beta/module/github.com/sagernet/netlink?licenses=true&version=v0.0.0-20240612041022-b9a21c07ac6a",
		"/fetch/github.com/sagernet/netlink@v0.0.0-20240612041022-b9a21c07ac6a",
		"/v1beta/module/github.com/sagernet/netlink?licenses=true",
	}, requests)
}

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

func TestCollectModulesKeepsOriginalPathOfReplacedModule(t *testing.T) {
	goModPath := filepath.Join(t.TempDir(), "go.mod")
	err := os.WriteFile(goModPath, []byte(`module example.com/main

go 1.26

require (
	example.com/plain v1.0.0
	example.com/replaced v1.2.0
)

replace example.com/replaced => example.com/fork v1.2.1
`), 0o644)
	require.NoError(t, err)

	modules, err := collectRequiredModules(goModPath)
	require.NoError(t, err)
	require.Len(t, modules, 2)

	assert.Equal(t, "example.com/plain", modules[0].Path)
	assert.Equal(t, "v1.0.0", modules[0].Version)

	assert.Equal(t, "example.com/replaced", modules[1].Path)
	assert.Equal(t, "v1.2.0", modules[1].Version)
}

func Test_isSingModule(t *testing.T) {
	tests := []struct {
		path string
		want bool
	}{
		{"github.com/sagernet/netlink", false},
		{"golang.org/x/net", false},
		{"lukechampine.com/blake3", false},
		{"github.com/sagernet/sing", true},
		{"github.com/sagernet/sing-vmess", true},
		{"github.com/dyhkwong/sing-juicity", true},
		{"github.com/anytls/sing-anytls", true},
		{"github.com/xchacha20-poly1305/sing-trusttunnel", true},
		{"github.com/sagernet/cronet-go", true},
		{"github.com/sagernet/cronet-go/lib/android_arm64", true},
		{"github.com/xchacha20-poly1305/anchor", true},
	}
	for _, tt := range tests {
		assert.Equal(t, tt.want, inGPLv3OrLaterWhiteList(tt.path), tt.path)
	}
}

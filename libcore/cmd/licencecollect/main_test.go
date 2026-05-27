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

	"codeberg.org/xchacha20-poly1305/pkgsite-go"
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
		if request.URL.Path != "/v1beta/module/"+modulePath {
			t.Fatalf("path = %q, want /v1beta/module/%s", request.URL.Path, modulePath)
		}
		if request.URL.Query().Get("licenses") != "true" {
			t.Fatalf("licenses = %q, want true", request.URL.Query().Get("licenses"))
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
			t.Fatalf("version = %q, want %q or empty", request.URL.Query().Get("version"), version)
		}
	}))
	defer server.Close()

	library, err := resolveLibrary(context.Background(), pkgsite.NewClient(pkgsite.WithServer(server.URL)), &debug.Module{
		Path:    modulePath,
		Version: version,
	})
	if err != nil {
		t.Fatal(err)
	}
	if library.UniqueID != modulePath {
		t.Errorf("UniqueID = %q, want %q", library.UniqueID, modulePath)
	}
	if library.ArtifactVersion != version {
		t.Errorf("ArtifactVersion = %q, want %q", library.ArtifactVersion, version)
	}
	if library.Name != modulePath {
		t.Errorf("Name = %q, want %q", library.Name, modulePath)
	}
	if library.Website != repoURL {
		t.Errorf("Website = %q, want %q", library.Website, repoURL)
	}
	if len(library.Licenses) != 1 || library.Licenses[0] != "Apache-2.0" {
		t.Errorf("Licenses = %v, want [Apache-2.0]", library.Licenses)
	}
	if len(requests) != 2 {
		t.Fatalf("len(requests) = %d, want 2: %v", len(requests), requests)
	}
}

func TestWriteLibrariesCleansGeneratedFilesOnly(t *testing.T) {
	dir := t.TempDir()
	err := os.WriteFile(filepath.Join(dir, "go_old.json"), []byte("{}"), 0o644)
	if err != nil {
		t.Fatal(err)
	}
	err = os.WriteFile(filepath.Join(dir, "husi.json"), []byte("{}"), 0o644)
	if err != nil {
		t.Fatal(err)
	}

	err = writeToDir(dir, []Library{{
		UniqueID:        "github.com/example/module",
		ArtifactVersion: "v1.0.0",
		Name:            "github.com/example/module",
		Licenses:        []string{"MIT"},
	}}, true)
	if err != nil {
		t.Fatal(err)
	}

	if _, err = os.Stat(filepath.Join(dir, "go_old.json")); !os.IsNotExist(err) {
		t.Fatalf("go_old.json still exists")
	}
	if _, err = os.Stat(filepath.Join(dir, "husi.json")); err != nil {
		t.Fatal(err)
	}
	outputFile := filepath.Join(dir, "go_github.com_example_module.json")
	content, err := os.ReadFile(outputFile)
	if err != nil {
		t.Fatal(err)
	}
	var library Library
	err = json.Unmarshal(content, &library)
	if err != nil {
		t.Fatal(err)
	}
	if library.UniqueID != "github.com/example/module" {
		t.Errorf("UniqueID = %q, want github.com/example/module", library.UniqueID)
	}
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
		if got := inGPLv3OrLaterWhiteList(tt.path); got != tt.want {
			t.Errorf("isSingModule(%q) = %v, want %v", tt.path, got, tt.want)
		}
	}
}

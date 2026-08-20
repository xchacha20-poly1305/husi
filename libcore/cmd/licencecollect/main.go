package main

import (
	"cmp"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/mod/module"
)

const (
	generatedLibraryFilePrefix = "go_"

	defaultGoModPath = "go.mod"
)

func main() {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	goModPath := flag.String("mod", defaultGoModPath, "path to the go.mod listing the modules to collect")
	outputName := flag.String("o", "", "output")
	outputDir := flag.String("d", "", "output directory for AboutLibraries library definitions")
	cleanOutputDir := flag.Bool("clean", false, "remove generated library definitions from output directory before writing")
	flag.Parse()

	if *outputName != "" && *outputDir != "" {
		log.FatalContext(ctx, "only one of -o or -d can be set")
		return
	}

	var output io.Writer
	switch *outputName {
	case "", "stdout":
		output = os.Stdout
	case "stderr":
		output = os.Stderr
	default:
		file, err := os.Create(*outputName)
		if err != nil {
			log.FatalContext(ctx, "create output file", err)
			return
		}
		defer file.Close()
		output = file
	}

	versions, err := collectRequiredModules(*goModPath)
	if err != nil {
		log.PanicContext(ctx, err)
		return
	}

	cacheDir, err := moduleCacheDir()
	if err != nil {
		log.PanicContext(ctx, err)
		return
	}
	mainModuleDir := filepath.Dir(*goModPath)

	libraries := make([]Library, 0, len(versions))
	for _, dependency := range versions {
		library, err := resolveLibrary(ctx, cacheDir, mainModuleDir, dependency)
		if err != nil {
			log.PanicContext(ctx, err)
			return
		}
		log.InfoContext(ctx, "found ", library.Name, " with license: ", fmt.Sprint(library.Licenses))
		libraries = append(libraries, library)
	}
	slices.SortFunc(libraries, func(a, b Library) int {
		return cmp.Compare(a.UniqueID, b.UniqueID)
	})

	if *outputDir != "" {
		err := writeToDir(*outputDir, libraries, *cleanOutputDir)
		if err != nil {
			log.PanicContext(ctx, "write libraries", err)
			return
		}
		return
	}

	err = json.NewEncoder(output).Encode(libraries)
	if err != nil {
		log.PanicContext(ctx, "encode json ", err)
		return
	}
}

func writeToDir(outputDir string, libraries []Library, clean bool) error {
	err := os.MkdirAll(outputDir, 0o755)
	if err != nil {
		return err
	}
	if clean {
		err = cleanGeneratedLibraries(outputDir)
		if err != nil {
			return err
		}
	}
	for _, library := range libraries {
		file, err := os.Create(libraryFileName(outputDir, library.UniqueID))
		if err != nil {
			return err
		}
		err = json.NewEncoder(file).Encode(library)
		_ = file.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

func cleanGeneratedLibraries(outputDir string) error {
	matches, err := filepath.Glob(filepath.Join(outputDir, generatedLibraryFilePrefix+"*.json"))
	if err != nil {
		return E.Cause(err, "glob ", outputDir)
	}
	for _, match := range matches {
		err = os.Remove(match)
		if err != nil {
			return err
		}
	}
	return nil
}

var libraryNameReplacer = strings.NewReplacer("/", "_", "\\", "_", ":", "_")

func libraryFileName(outputDir string, uniqueID string) string {
	return filepath.Join(outputDir, generatedLibraryFilePrefix+libraryNameReplacer.Replace(uniqueID)+".json")
}

func resolveLibrary(ctx context.Context, cacheDir, mainModuleDir string, dependency module.Version) (Library, error) {
	library := Library{
		UniqueID:        dependency.Path,
		ArtifactVersion: dependency.Version,
		Name:            dependency.Path,
		Website:         moduleWebsite(dependency.Path),
	}
	dir, err := sourceDir(cacheDir, mainModuleDir, dependency)
	if err != nil {
		return Library{}, err
	}
	licenses, err := scanModuleLicenses(ctx, dir)
	if err != nil {
		return Library{}, E.Cause(err, "resolve ", dependency.Path)
	}
	library.Licenses = overrideLicenses(ctx, dependency.Path, licenses)
	return library, nil
}

func moduleWebsite(modulePath string) string {
	const scheme = "https://"
	prefix, majorVersion, versioned := module.SplitPathVersion(modulePath)
	// A "/v2" element is not part of the repository path, so it is dropped.
	// gopkg.in instead encodes the major version into the last element itself
	// ("gopkg.in/yaml.v3"), where dropping it leaves no reachable page.
	if versioned && strings.HasPrefix(majorVersion, "/") {
		return scheme + prefix
	}
	return scheme + modulePath
}

package main

import (
	"cmp"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"runtime/debug"
	"slices"
	"strings"
	"time"

	_ "libcore" // Import dependencies

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"codeberg.org/xchacha20-poly1305/pkgsite-go"
)

const (
	DefaultTimeout             = 15 * time.Second
	DefaultRateLimitRetryDelay = 1 * time.Minute // 60 rpm

	generatedLibraryFilePrefix = "go_"
)

func main() {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

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

	buildInfo, loaded := debug.ReadBuildInfo()
	if !loaded {
		log.PanicContext(ctx, "failed to read build info")
		return
	}

	client := pkgsite.NewClient()
	libraries := make([]Library, 0, len(buildInfo.Deps)+1)
	for _, dependency := range buildInfo.Deps {
		library, err := resolveLibrary(ctx, client, dependency)
		if err != nil {
			log.PanicContext(ctx, err)
			return
		}
		log.InfoContext(ctx, "found ", library.Name, " with license: ", fmt.Sprint(library.Licenses))
		libraries = append(libraries, library)
	}
	libraries = append(libraries, Library{
		UniqueID: "github.com/sagernet/cronet-go",
		Name:     "github.com/sagernet/cronet-go",
		Website:  "https://github.com/sagernet/cronet-go",
		Licenses: []string{LicenseGPL3OrLatter},
	})
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

	err := json.NewEncoder(output).Encode(libraries)
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

func resolveLibrary(ctx context.Context, client *pkgsite.Client, module *debug.Module) (library Library, err error) {
	library = Library{
		UniqueID:        module.Path,
		ArtifactVersion: module.Version,
		Name:            module.Path,
	}
	if inGPLv3OrLaterWhiteList(module.Path) {
		library.Website = "https://" + module.Path
		library.Licenses = []string{LicenseGPL3OrLatter}
	} else {
		// Currently, we don't need to resolve replaced module's real path.
		// Because replaced module usually not changed its path to another unique one.
		pkgModule, err := resolveModule(ctx, client, module.Path, &pkgsite.ModuleOptions{
			Version:  module.Version,
			Licenses: true,
		})
		// For the pseudo versions that unrecorded
		if err != nil && module.Version != "" && isHTTPErrorCode(err, http.StatusNotFound) {
			pkgModule, err = resolveModule(ctx, client, module.Path, &pkgsite.ModuleOptions{
				Licenses: true,
			})
		}
		if err != nil {
			return common.DefaultValue[Library](), E.Cause(err, "resolve ", module.Path)
		}
		library.Website = pkgModule.RepoURL
		licenses := common.FlatMap(pkgModule.Licenses, func(it pkgsite.License) []string {
			return it.Types
		})
		slices.Sort(licenses)
		licenses = slices.Clip(slices.Compact(licenses))
		library.Licenses = licenses
	}
	return
}

// Due to the blurred result of license scanner, some module's GPL 3.0 or later
// licenses will be treated as GPL 2.0.
func inGPLv3OrLaterWhiteList(modulePath string) bool {
	base := path.Base(modulePath)
	if strings.HasPrefix(base, "sing") {
		return true
	}
	if strings.Contains(modulePath, "cronet-go") {
		return true
	}
	switch modulePath {
	case "github.com/sagernet/fswatch":
		return true
	case "github.com/xchacha20-poly1305/anchor", "github.com/xchacha20-poly1305/libping":
		return true
	}
	return false
}

func resolveModule(ctx context.Context, client *pkgsite.Client, modulePath string, options *pkgsite.ModuleOptions) (*pkgsite.Module, error) {
	pkgModule, err := requestModuleWithRetry(ctx, client, modulePath, options)
	// The module may not be recorded by pkgsite yet, ask it to fetch and retry once.
	if isHTTPErrorCode(err, http.StatusNotFound) {
		log.InfoContext(ctx, "not found, try to fetch ", modulePath)
		if fetchError := fetchModule(ctx, client, modulePath, options.Version); fetchError != nil {
			log.ErrorContext(ctx, "also failed to fetch: ", fetchError)
			return nil, err
		}
		return requestModuleWithRetry(ctx, client, modulePath, options)
	}
	return pkgModule, err
}

// requestModuleWithRetry requests a module, retrying only while pkgsite rate limits us.
func requestModuleWithRetry(ctx context.Context, client *pkgsite.Client, modulePath string, options *pkgsite.ModuleOptions) (*pkgsite.Module, error) {
	for {
		pkgModule, err := requestModule(ctx, client, modulePath, options)
		if !isHTTPErrorCode(err, http.StatusTooManyRequests) {
			return pkgModule, err
		}
		log.WarnContext(ctx, "pkgsite rate limited while resolving ", modulePath, ", retrying in ", DefaultRateLimitRetryDelay)
		select {
		case <-time.After(DefaultRateLimitRetryDelay):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
}

func contextWithDefaultTimeout(ctx context.Context) (context.Context, context.CancelFunc) {
	return context.WithTimeout(ctx, DefaultTimeout)
}

func requestModule(ctx context.Context, client *pkgsite.Client, modulePath string, options *pkgsite.ModuleOptions) (*pkgsite.Module, error) {
	ctx, cancel := contextWithDefaultTimeout(ctx)
	defer cancel()
	return client.Module(ctx, modulePath, options)
}

func isHTTPErrorCode(err error, target int) bool {
	if code, isHTTPError := pkgsite.HTTPErrorCode(err); isHTTPError {
		return target == code
	}
	return false
}

// fetchModule is a workaround for https://pkg.go.dev/github.com/sagernet/ws,
// I don't know why pkg.go.dev clean it frequently. Before this I require a fetch on web manually.
func fetchModule(ctx context.Context, client *pkgsite.Client, modulePath, version string) error {
	ctx, cancel := contextWithDefaultTimeout(ctx)
	defer cancel()
	// resolveLibrary already retries resolveModule with and without a version,
	// so fetch only the variant matching the current options to avoid redundant fetches.
	fetchPath := modulePath
	if version != "" {
		fetchPath += "@" + version
	}
	return client.FetchModule(ctx, fetchPath)
}

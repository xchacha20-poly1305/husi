package main

import (
	"go/build"
	"os"
	"path/filepath"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/mod/modfile"
	"golang.org/x/mod/module"
)

func isCronetGoSubmodule(modulePath string) bool {
	const cronetGoModule = "github.com/sagernet/cronet-go"
	return strings.HasPrefix(modulePath, cronetGoModule+"/")
}

func collectRequiredModules(goModPath string) ([]module.Version, error) {
	content, err := os.ReadFile(goModPath)
	if err != nil {
		return nil, err
	}
	goMod, err := modfile.Parse(goModPath, content, nil)
	if err != nil {
		return nil, E.Cause(err, "parse ", goModPath)
	}

	replacements := buildReplacements(goMod)
	versions := make([]module.Version, 0, len(goMod.Require))
	for _, require := range goMod.Require {
		if isCronetGoSubmodule(require.Mod.Path) {
			continue
		}
		versions = append(versions, resolveReplacement(require.Mod, replacements))
	}
	return versions, nil
}

func buildReplacements(goMod *modfile.File) map[module.Version]module.Version {
	replacements := make(map[module.Version]module.Version, len(goMod.Replace))
	for _, replace := range goMod.Replace {
		replacements[replace.Old] = replace.New
	}
	return replacements
}

func resolveReplacement(version module.Version, replacements map[module.Version]module.Version) module.Version {
	if replaced, loaded := replacements[version]; loaded {
		return replaced
	}
	if replaced, loaded := replacements[module.Version{Path: version.Path}]; loaded {
		return replaced
	}
	return version
}

func moduleCacheDir() (string, error) {
	if cacheDir := os.Getenv("GOMODCACHE"); cacheDir != "" {
		return cacheDir, nil
	}
	goPaths := filepath.SplitList(build.Default.GOPATH)
	if len(goPaths) == 0 || goPaths[0] == "" {
		return "", E.New("neither GOMODCACHE nor GOPATH is set")
	}
	return filepath.Join(goPaths[0], "pkg", "mod"), nil
}

func sourceDir(cacheDir, mainModuleDir string, dependency module.Version) (string, error) {
	var dir string
	if modfile.IsDirectoryPath(dependency.Path) {
		dir = filepath.Join(mainModuleDir, filepath.FromSlash(dependency.Path))
	} else {
		escapedPath, err := module.EscapePath(dependency.Path)
		if err != nil {
			return "", err
		}
		escapedVersion, err := module.EscapeVersion(dependency.Version)
		if err != nil {
			return "", err
		}
		dir = filepath.Join(cacheDir, escapedPath+"@"+escapedVersion)
	}

	stat, err := os.Stat(dir)
	if err != nil || !stat.IsDir() {
		return "", E.New("module ", dependency.Path, "@", dependency.Version, ` is not in the module cache, run "go mod download" first: `, dir)
	}
	return dir, nil
}

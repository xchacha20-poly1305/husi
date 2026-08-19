package main

import (
	"go/build"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/mod/modfile"
	"golang.org/x/mod/module"
)

func isCronetGoSubmodule(modulePath string) bool {
	const cronetGoModule = "github.com/sagernet/cronet-go"
	return strings.HasPrefix(modulePath, cronetGoModule+"/")
}

func collectRequiredModules(goModPath string) ([]*debug.Module, error) {
	content, err := os.ReadFile(goModPath)
	if err != nil {
		return nil, err
	}
	goMod, err := modfile.Parse(goModPath, content, nil)
	if err != nil {
		return nil, E.Cause(err, "parse ", goModPath)
	}

	replacements := make(map[module.Version]module.Version, len(goMod.Replace))
	for _, replace := range goMod.Replace {
		replacements[replace.Old] = replace.New
	}

	modules := make([]*debug.Module, 0, len(goMod.Require))
	for _, require := range goMod.Require {
		if isCronetGoSubmodule(require.Mod.Path) {
			continue
		}
		dependency := &debug.Module{
			Path:    require.Mod.Path,
			Version: require.Mod.Version,
		}
		modules = append(modules, dependency)
	}
	return modules, nil
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

func sourceDir(cacheDir, mainModuleDir string, dependency *debug.Module) (string, error) {
	target := dependency
	if dependency.Replace != nil {
		target = dependency.Replace
	}

	var dir string
	if modfile.IsDirectoryPath(target.Path) {
		dir = filepath.Join(mainModuleDir, filepath.FromSlash(target.Path))
	} else {
		escapedPath, err := module.EscapePath(target.Path)
		if err != nil {
			return "", err
		}
		escapedVersion, err := module.EscapeVersion(target.Version)
		if err != nil {
			return "", err
		}
		dir = filepath.Join(cacheDir, escapedPath+"@"+escapedVersion)
	}

	stat, err := os.Stat(dir)
	if err != nil || !stat.IsDir() {
		return "", E.New("module ", target.Path, "@", target.Version, ` is not in the module cache, run "go mod download" first: `, dir)
	}
	return dir, nil
}

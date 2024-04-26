package main

import (
	"cmp"
	"os"
	"runtime/debug"
	"slices"

	// Import the smallest part of sing-box
	_ "github.com/sagernet/sing-box/constant/goos"
)

const boxPath = "github.com/sagernet/sing-box"

func main() {
	buildInfo, _ := debug.ReadBuildInfo()

	m, found := slices.BinarySearchFunc(
		buildInfo.Deps,
		&debug.Module{
			Path: boxPath,
		},
		func(a, b *debug.Module) int {
			return cmp.Compare(a.Path, b.Path)
		},
	)
	if !found {
		os.Exit(1)
		return
	}

	_, _ = os.Stdout.WriteString(buildInfo.Deps[m].Version)
}

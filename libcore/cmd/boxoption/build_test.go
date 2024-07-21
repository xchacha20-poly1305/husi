package main

import (
	"testing"

	"github.com/sagernet/sing-box/option"
)

func TestBuildClass(t *testing.T) {
	t.Log(buildClass(option.ClashAPIOptions{}))
}

package main

import (
	"testing"

	"github.com/sagernet/sing-box/option"
)

func Test_BuildClass(t *testing.T) {
	t.Log(string(buildClass(option.WireGuardEndpointOptions{}, extendsBox)) + "\n")
}

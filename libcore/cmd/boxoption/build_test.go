package main

import (
	"testing"

	"github.com/sagernet/sing-box/option"
)

func Test_BuildClass(t *testing.T) {
	t.Log(string(buildClass(option.WireGuardEndpointOptions{}, extendsBox)) + "\n")
}

func TestGeneratedClassNameOf(t *testing.T) {
	tests := []struct {
		name    string
		option  any
		belongs string
		want    string
	}{
		{
			name:    "root class keeps reflected type name",
			option:  option.Hysteria2Obfs{},
			belongs: extendsBox,
			want:    "Hysteria2Obfs",
		},
		{
			name:    "outbound class uses generated Kotlin class name",
			option:  option.SnellOutboundOptions{},
			belongs: "Outbound",
			want:    "Outbound_SnellOptions",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got := generatedClassNameOf(test.option, test.belongs)
			if got != test.want {
				t.Fatalf("generatedClassNameOf() = %q, want %q", got, test.want)
			}
		})
	}
}

func TestInlineExtensionsUseGeneratedClassName(t *testing.T) {
	if _, exists := inlineExtensions["SnellOutboundOptions"]; exists {
		t.Fatal("inlineExtensions contains raw Go type name key for Snell")
	}
	if _, exists := inlineExtensions["Outbound_SnellOptions"]; !exists {
		t.Fatal("inlineExtensions does not contain generated Kotlin class name key for Snell")
	}
}

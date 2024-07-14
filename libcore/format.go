package libcore

import (
	"bytes"
	"context"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/humanize"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
)

// FormatBytes formats the bytes to humanize.
func FormatBytes(length int64) string {
	return humanize.Bytes(uint64(length))
}

// parseConfig parses configContent to option.Options.
func parseConfig(configContent string) (option.Options, error) {
	options, err := json.UnmarshalExtended[option.Options]([]byte(configContent))
	if err != nil {
		return option.Options{}, E.Cause(err, "decode config")
	}
	return options, nil
}

// FormatConfig formats json.
func FormatConfig(configContent string) (string, error) {
	configMap, err := json.UnmarshalExtended[map[string]any]([]byte(configContent))
	if err != nil {
		return "", err
	}

	var buffer bytes.Buffer
	encoder := json.NewEncoder(&buffer)
	encoder.SetIndent("", "  ")
	err = encoder.Encode(configMap)
	if err != nil {
		return "", err
	}

	return buffer.String(), nil
}

// CheckConfig checks configContent wheather can run as sing-box configuration.
func CheckConfig(configContent string) error {
	options, err := parseConfig(configContent)
	if err != nil {
		return E.Cause(err, "parse config")
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	instance, err := box.New(box.Options{
		Options:           options,
		Context:           ctx,
		PlatformInterface: &boxPlatformInterfaceWrapper{},
	})
	if err != nil {
		return E.Cause(err, "create box")
	}
	defer instance.Close()
	return nil
}

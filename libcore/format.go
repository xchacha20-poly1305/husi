package libcore

import (
	"bytes"

	"github.com/sagernet/sing-box/common/humanize"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
)

func FormatBytes(length int64) string {
	return humanize.Bytes(uint64(length))
}

func parseConfig(configContent string) (option.Options, error) {
	options, err := json.UnmarshalExtended[option.Options]([]byte(configContent))
	if err != nil {
		return option.Options{}, E.Cause(err, "decode config")
	}
	return options, nil
}

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

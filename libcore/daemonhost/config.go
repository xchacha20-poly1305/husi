package daemonhost

import (
	"context"
	"os"
	"path/filepath"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"

	"github.com/xchacha20-poly1305/husi/libcore/v2/externalapi"
)

const defaultConfigFileName = "daemon.json"

type Config struct {
	API *externalapi.Options `json:"api,omitempty"`
}

func DefaultConfigPath(workingDir string) string {
	return filepath.Join(workingDir, defaultConfigFileName)
}

func LoadConfig(ctx context.Context, path string) (*Config, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, E.Cause(err, "read daemon config")
	}
	var config Config
	err = json.UnmarshalContextDisallowUnknownFields(ctx, content, &config)
	if err != nil {
		return nil, E.Cause(err, "decode daemon config")
	}
	return &config, nil
}

package libcore

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestGenerateConfigSchema(t *testing.T) {
	content, err := GenerateConfigSchema()
	if !assert.NoError(t, err) {
		return
	}

	var generated map[string]any
	if assert.NoError(t, json.Unmarshal([]byte(content), &generated)) {
		assert.Contains(t, generated, "$defs")
		assert.Contains(t, content, `"$ref"`)
	}
}

func Test_FormatConfig(t *testing.T) {
	tt := []struct {
		name    string
		config  string
		wantErr bool
	}{
		{
			name:    "Empty",
			config:  "",
			wantErr: true,
		},
		{
			name:    "2D",
			config:  "{\"inbounds\":[]}",
			wantErr: false,
		},
		{
			name: "3D",
			config: `
{
    "log": {
                    "disabled":     true
}	}`,
			wantErr: false,
		},
		{
			name: "With comment",
			config: `
{
// ntp
"ntp": {
"server": "time.apple.com"
}
}`,
			wantErr: false,
		},
		{
			name: "Invalid format",
			config: `
{{{}
`,
			wantErr: true,
		},
		{
			name: "Nested",
			config: `
{
"outbounds": [
{
"tag": "unknown",
"type": "shadowsocks",
"tls": {
"enabled": true
}
}
]
}`,
			wantErr: false,
		},
	}

	for _, test := range tt {
		t.Run(test.name, func(t *testing.T) {
			formatted, err := FormatConfig(test.config)
			if test.wantErr {
				assert.Error(t, err)
				return
			}
			if assert.NoError(t, err) {
				assert.NotEmpty(t, formatted)
			}
		})
	}
}

func Test_CheckConfig(t *testing.T) {
	tests := []struct {
		name    string
		config  string
		wantErr bool
	}{
		{
			name:    "Empty",
			config:  "",
			wantErr: true,
		},
		{
			name:    "{}",
			config:  "{}",
			wantErr: false,
		},
		{
			name: "Invalid field",
			config: `
{
    "outbounds": [
        {
            "type": "shadowsocks",
            "tag": "proxy",
            "method": "xsala20"
        }
    ]
}
			`,
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := CheckConfig(tt.config)
			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

func Test_ParseDuration(t *testing.T) {
	tests := []struct {
		name    string
		raw     string
		want    int64
		wantErr bool
	}{
		{
			name:    "valid duration",
			raw:     "30s",
			want:    int64(30 * time.Second),
			wantErr: false,
		},
		{
			name:    "valid duration with milliseconds",
			raw:     "30.5s",
			want:    int64(30500 * time.Millisecond),
			wantErr: false,
		},
		{
			name:    "valid duration with microseconds",
			raw:     "30.000005s",
			want:    int64(30000005 * time.Microsecond),
			wantErr: false,
		},

		{
			name:    "valid duration with nanoseconds",
			raw:     "30.000000005s",
			want:    int64(30000000005 * time.Nanosecond),
			wantErr: false,
		},

		{
			name:    "zero duration",
			raw:     "0s",
			want:    0,
			wantErr: false,
		},
		{
			name:    "negative duration",
			raw:     "-30s",
			want:    int64(-30 * time.Second),
			wantErr: false,
		},
		{
			name:    "invalid duration",
			raw:     "invalid",
			want:    0,
			wantErr: true,
		},
		{
			name:    "empty duration",
			raw:     "",
			want:    0,
			wantErr: true,
		},

		{
			name:    "minutes",
			raw:     "1m",
			want:    int64(time.Minute),
			wantErr: false,
		},
		{
			name:    "hours",
			raw:     "1h",
			want:    int64(time.Hour),
			wantErr: false,
		},

		{
			name:    "days",
			raw:     "24h", // One day
			want:    int64(24 * time.Hour),
			wantErr: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseDuration(tt.raw)
			if tt.wantErr {
				assert.Error(t, err)
				return
			}
			assert.NoError(t, err)
			assert.Equal(t, tt.want, got)
		})
	}
}

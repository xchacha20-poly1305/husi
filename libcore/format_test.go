package libcore

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

func TestGenerateSchema(t *testing.T) {
	tests := map[husiv1.SchemaKind][]string{
		husiv1.SchemaKind_SCHEMA_KIND_CONFIG:   {`"log"`, `"outbounds"`},
		husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND: {`"direct"`},
		husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE: {`"domain"`},
	}
	for kind, wantContents := range tests {
		t.Run(kind.String(), func(t *testing.T) {
			content, err := generateSchema(kind)
			if !assert.NoError(t, err) {
				return
			}

			var generated map[string]any
			if assert.NoError(t, json.Unmarshal([]byte(content), &generated)) {
				assert.Contains(t, generated, "$defs")
				assert.Contains(t, content, `"$ref"`)
			}
			for _, want := range wantContents {
				assert.Contains(t, content, want)
			}
		})
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
			formatted, err := formatConfig(test.config)
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

func Test_FormatConfig_KeepComments(t *testing.T) {
	tt := []struct {
		name   string
		config string
		want   string
	}{
		{
			name: "Line",
			config: `{
// ntp
"ntp": {"server": "time.apple.com"}
}`,
			want: "// ntp",
		},
		{
			name: "Hash",
			config: `{
# ntp
"ntp": {"server": "time.apple.com"}
}`,
			want: "# ntp",
		},
		{
			name: "Block",
			config: `{
/* ntp */
"ntp": {"server": "time.apple.com"}
}`,
			want: "/* ntp */",
		},
		{
			name: "Trailing",
			config: `{
"ntp": {"server": "time.apple.com"} // ntp
}`,
			want: "// ntp",
		},
		{
			name: "Nested",
			config: `{
"outbounds": [
// direct
{"type": "direct", "tag": "direct"}
]
}`,
			want: "// direct",
		},
	}

	for _, test := range tt {
		t.Run(test.name, func(t *testing.T) {
			formatted, err := formatConfig(test.config)
			if assert.NoError(t, err) {
				assert.Contains(t, formatted, test.want)
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
			err := checkConfig(tt.config)
			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

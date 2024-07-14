package libcore

import (
	"testing"

	"github.com/sagernet/sing-box/common/humanize"
)

func TestFormatBytes(t *testing.T) {
	tt := []int64{
		humanize.IByte, humanize.KByte, humanize.MByte, humanize.GByte,
		humanize.TByte, humanize.TByte, humanize.EByte,
	}

	for _, test := range tt {
		bytesFormat := FormatBytes(test)
		t.Logf("%d -> %s", test, bytesFormat)
	}
}

func TestFormatConfig(t *testing.T) {
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
		formated, err := FormatConfig(test.config)
		if test.wantErr {
			if err != nil {
				t.Logf("[%s] Successed to get error: %v", test.name, err)
			} else {
				t.Errorf("[%s] Want error but got: %s", test.name, formated)
			}
		} else {
			if err != nil {
				t.Errorf("Failed to format [%s]: %v", test.name, err)
			} else {
				t.Logf("[%s]: %s", test.name, formated)
			}
		}
	}
}

func TestCheckConfig(t *testing.T) {
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
		err := CheckConfig(tt.config)
		if err != nil {
			if tt.wantErr {
				t.Logf("TestCheckConfig [%s] passed", tt.name)
				continue
			}
			t.Errorf("TestCheckConfig [%s] wants error but not", tt.name)
			continue
		}
		if tt.wantErr {
			t.Errorf("TestCheckConfig [%s] wants error but not", tt.name)
			continue
		}
		t.Logf("TestCheckConfig [%s] passed", tt.name)
	}
}

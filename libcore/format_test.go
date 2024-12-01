package libcore

import (
	"testing"
	"time"

	"github.com/sagernet/sing-box/common/humanize"
)

func Test_FormatBytes(t *testing.T) {
	tt := []int64{
		humanize.IByte, humanize.KByte, humanize.MByte, humanize.GByte,
		humanize.TByte, humanize.TByte, humanize.EByte,
	}

	for _, test := range tt {
		bytesFormat := FormatBytes(test)
		t.Logf("%d -> %s", test, bytesFormat)
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
			if (err != nil) != tt.wantErr {
				t.Errorf("ParseDuration() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if got != tt.want {
				t.Errorf("ParseDuration() = %v, want %v", got, tt.want)
			}
		})
	}
}

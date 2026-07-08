package libcore

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func Test_ParseUrl(t *testing.T) {
	type args struct {
		rawURL string
	}
	tests := []struct {
		name    string
		args    args
		isWant  func(u URL) bool
		wantErr bool
	}{
		{
			name: "no port",
			args: args{
				rawURL: "hysteria2://ganggang@icecreamsogood/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "ganggang" {
					return false
				}
				if u.GetHost() != "icecreamsogood" {
					return false
				}
				return true
			},
		},
		{
			name: "single port",
			args: args{
				rawURL: "hysteria2://yesyes@icecreamsogood:8888/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "yesyes" {
					return false
				}
				if u.GetHost() != "icecreamsogood" {
					return false
				}
				if u.GetPorts() != "8888" {
					return false
				}
				return true
			},
		},
		{
			name: "multi port",
			args: args{
				rawURL: "hysteria2://darkness@laplus.org:8888,9999,11111/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "darkness" {
					return false
				}
				if u.GetHost() != "laplus.org" {
					return false
				}
				if u.GetPorts() != "8888,9999,11111" {
					return false
				}
				return true
			},
		},
		{
			name: "range port",
			args: args{
				rawURL: "hysteria2://darkness@laplus.org:8888-9999/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "darkness" {
					return false
				}
				if u.GetHost() != "laplus.org" {
					return false
				}
				if u.GetPorts() != "8888-9999" {
					return false
				}
				return true
			},
		},
		{
			name: "both",
			args: args{
				rawURL: "hysteria2://gawr:gura@atlantis.moe:443,7788-8899,10010/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "gawr" {
					return false
				}
				if u.GetPassword() != "gura" {
					return false
				}
				if u.GetHost() != "atlantis.moe" {
					return false
				}
				if u.GetPorts() != "443,7788-8899,10010" {
					return false
				}
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseURL(tt.args.rawURL)
			if tt.wantErr {
				assert.Error(t, err)
				return
			}
			require.NoError(t, err)
			require.NotNil(t, got)
			assert.True(t, tt.isWant(got), got.GetString())
		})
	}
}

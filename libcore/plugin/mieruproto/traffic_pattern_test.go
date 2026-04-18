package mieruproto

import (
	"encoding/base64"
	"testing"

	"google.golang.org/protobuf/proto"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestDecodeBase64JSON(t *testing.T) {
	tests := []struct {
		name    string
		encoded string
		want    string
	}{
		{
			name:    "full pattern",
			encoded: "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3",
			want: `{
				"seed": 42,
				"unlockAll": true,
				"tcpFragment": {
					"enable": true,
					"maxSleepMs": 10
				},
				"nonce": {
					"type": "NONCE_TYPE_FIXED",
					"applyToAllUDPPacket": true,
					"customHexStrings": ["00010203", "04050607"]
				}
			}`,
		},
		{
			name:    "empty pattern",
			encoded: "",
			want:    `{}`,
		},
		{
			name:    "printable subset nonce",
			encoded: "GgIQBSIGCAIYBCAI",
			want: `{
				"tcpFragment": {
					"maxSleepMs": 5
				},
				"nonce": {
					"type": "NONCE_TYPE_PRINTABLE_SUBSET",
					"minLen": 4,
					"maxLen": 8
				}
			}`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := DecodeBase64JSON(tt.encoded)
			require.NoError(t, err)
			assert.JSONEq(t, tt.want, string(got))
		})
	}
}

func TestEncodeJSONBase64(t *testing.T) {
	tests := []struct {
		name     string
		jsonText string
		want     string
	}{
		{
			name: "wrapped full pattern",
			jsonText: `{
				"trafficPattern": {
					"seed": 42,
					"unlockAll": true,
					"tcpFragment": {
						"enable": true,
						"maxSleepMs": 10
					},
					"nonce": {
						"type": "NONCE_TYPE_FIXED",
						"applyToAllUDPPacket": true,
						"customHexStrings": ["00010203", "04050607"]
					}
				}
			}`,
			want: "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3",
		},
		{
			name: "bare full pattern",
			jsonText: `{
				"seed": 42,
				"unlockAll": true,
				"tcpFragment": {
					"enable": true,
					"maxSleepMs": 10
				},
				"nonce": {
					"type": "NONCE_TYPE_FIXED",
					"applyToAllUDPPacket": true,
					"customHexStrings": ["00010203", "04050607"]
				}
			}`,
			want: "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3",
		},
		{
			name: "empty pattern",
			jsonText: `{
				"trafficPattern": {}
			}`,
			want: "",
		},
		{
			name: "printable subset nonce",
			jsonText: `{
				"nonce": {
					"type": "NONCE_TYPE_PRINTABLE_SUBSET",
					"minLen": 4,
					"maxLen": 8
				},
				"tcpFragment": {
					"maxSleepMs": 5
				}
			}`,
			want: "GgIQBSIGCAIYBCAI",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := EncodeJSONBase64(tt.jsonText)
			require.NoError(t, err)
			require.Equal(t, tt.want, got)
		})
	}
}

func TestDecode(t *testing.T) {
	tests := []struct {
		name    string
		encoded string
		want    *TrafficPattern
	}{
		{
			name:    "full pattern",
			encoded: "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3",
			want: &TrafficPattern{
				Seed:      new(int32(42)),
				UnlockAll: new(true),
				TcpFragment: &TCPFragment{
					Enable:     new(true),
					MaxSleepMs: new(int32(10)),
				},
				Nonce: &NoncePattern{
					Type:                new(NonceType_NONCE_TYPE_FIXED),
					ApplyToAllUDPPacket: new(true),
					CustomHexStrings:    []string{"00010203", "04050607"},
				},
			},
		},
		{
			name:    "printable subset nonce",
			encoded: "GgIQBSIGCAIYBCAI",
			want: &TrafficPattern{
				TcpFragment: &TCPFragment{
					MaxSleepMs: new(int32(5)),
				},
				Nonce: &NoncePattern{
					Type:   new(NonceType_NONCE_TYPE_PRINTABLE_SUBSET),
					MinLen: new(int32(4)),
					MaxLen: new(int32(8)),
				},
			},
		},
		{
			name:    "empty pattern",
			encoded: "",
			want:    &TrafficPattern{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			raw, err := base64.StdEncoding.DecodeString(tt.encoded)
			require.NoError(t, err)

			got, err := Decode(raw)
			require.NoError(t, err)
			assert.True(t, proto.Equal(tt.want, got), "decoded message mismatch")
		})
	}
}

func TestTrafficPatternErrors(t *testing.T) {
	tests := []struct {
		name        string
		test        func(t *testing.T) error
		wantContain string
	}{
		{
			name: "invalid base64",
			test: func(t *testing.T) error {
				_, err := DecodeBase64JSON("%%%")
				return err
			},
			wantContain: "decode base64",
		},
		{
			name: "invalid protobuf",
			test: func(t *testing.T) error {
				_, err := Decode([]byte{0xff})
				return err
			},
			wantContain: "unmarshal traffic pattern protobuf",
		},
		{
			name: "invalid root json",
			test: func(t *testing.T) error {
				_, err := EncodeJSONBase64("{")
				return err
			},
			wantContain: "unmarshal traffic pattern json",
		},
		{
			name: "invalid nested json payload",
			test: func(t *testing.T) error {
				_, err := EncodeJSONBase64(`{"trafficPattern": 1}`)
				return err
			},
			wantContain: "unmarshal traffic pattern payload",
		},
		{
			name: "unknown enum name",
			test: func(t *testing.T) error {
				_, err := EncodeJSONBase64(`{"nonce":{"type":"NONCE_TYPE_WHATEVER"}}`)
				return err
			},
			wantContain: "unmarshal traffic pattern payload",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.test(t)
			require.Error(t, err)
			assert.ErrorContains(t, err, tt.wantContain)
		})
	}
}

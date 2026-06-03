package libcore

import (
	"strings"
	"testing"

	"filippo.io/age"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestValidateAgeIdentities(t *testing.T) {
	x25519Identity, err := age.GenerateX25519Identity()
	require.NoError(t, err)
	hybridIdentity, err := age.GenerateHybridIdentity()
	require.NoError(t, err)

	tests := []struct {
		name    string
		text    string
		wantErr bool
	}{
		{
			name:    "x25519 identity",
			text:    x25519Identity.String(),
			wantErr: false,
		},
		{
			name:    "hybrid identity",
			text:    hybridIdentity.String(),
			wantErr: false,
		},
		{
			name:    "multiple identities",
			text:    strings.Join([]string{x25519Identity.String(), hybridIdentity.String()}, "\n"),
			wantErr: false,
		},
		{
			name:    "blank",
			text:    "",
			wantErr: true,
		},
		{
			name:    "recipient is not identity",
			text:    x25519Identity.Recipient().String(),
			wantErr: true,
		},
		{
			name:    "invalid text",
			text:    "AGE-SECRET-KEY-1INVALID",
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := ValidateAgeIdentities(tt.text)
			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

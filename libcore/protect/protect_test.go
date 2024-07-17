package protect

import (
	"io"
	"testing"

	E "github.com/sagernet/sing/common/exceptions"
)

func TestProtect(t *testing.T) {
	const testProtectPath = "protect_test"
	closer := ServerProtect(testProtectPath, func(fd int) error {
		if fd < 0 {
			return E.New("invalid fd: ", fd)
		}
		return nil
	})
	if closer == nil {
		t.Errorf("ServerProtect failed")
		return
	}
	defer func(t *testing.T, closer io.Closer) {
		if err := closer.Close(); err != nil {
			t.Errorf("failed to close: %v", err)
		}
	}(t, closer)

	type clientArg struct {
		fd   int
		path string
	}
	tests := []struct {
		name    string
		arg     clientArg
		wantErr bool
	}{
		{
			name: "normal",
			arg: clientArg{
				fd:   1,
				path: testProtectPath,
			},
			wantErr: false,
		},
		{
			name: "invalid fd",
			arg: clientArg{
				fd:   -1,
				path: testProtectPath,
			},
			wantErr: true,
		},
		{
			name: "invalid path",
			arg: clientArg{
				fd:   2,
				path: "invalid",
			},
			wantErr: true,
		},
	}
	for _, tt := range tests {
		err := ClientProtect(tt.arg.fd, tt.arg.path)
		if (err != nil) != tt.wantErr {
			t.Errorf("protect failed for [%s]", tt.name)
			return
		}
	}

}

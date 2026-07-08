//go:build unix

package protect

import (
	"context"
	"os"
	"syscall"
	"testing"
	"time"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func Test_Protect(t *testing.T) {
	const (
		testProtectPath = "protect_test"
		timeout         = 5 * time.Second
	)
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	ctx = log.ContextWithOverrideLevel(ctx, log.LevelTrace)
	service, err := New(ctx, log.StdLogger(), testProtectPath, func(fd int) error {
		if fd < 0 {
			return E.New("invalid fd: ", fd)
		}
		return nil
	})
	require.NoError(t, err)
	err = service.Start()
	require.NoError(t, err)
	defer service.Close()

	type clientArg struct {
		fd   int
		path string
	}
	tt := []struct {
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
	for _, test := range tt {
		t.Run(test.name, func(t *testing.T) {
			do := control.ProtectPath(test.arg.path)
			err := do(netUnix, "", fdProvider(test.arg.fd))
			if test.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

var _ syscall.RawConn = fdProvider(0)

type fdProvider int

func (f fdProvider) Control(ctl func(fd uintptr)) error {
	ctl(uintptr(f))
	return nil
}

func (f fdProvider) Read(_ func(fd uintptr) (done bool)) error {
	return os.ErrInvalid
}

func (f fdProvider) Write(_ func(fd uintptr) (done bool)) error {
	return os.ErrInvalid
}

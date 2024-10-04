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
)

func TestProtect(t *testing.T) {
	const (
		testProtectPath = "protect_test"
		timeout         = 5 * time.Second
	)
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	service, err := New(ctx, log.StdLogger(), testProtectPath, func(fd int) error {
		if fd < 0 {
			return E.New("invalid fd: ", fd)
		}
		return nil
	})
	if err != nil {
		t.Errorf("create protect service: %v", err)
		return
	}
	err = service.Start()
	if err != nil {
		t.Errorf("start protect server: %v", err)
		return
	}
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
		do := control.ProtectPath(test.arg.path)
		err := do(netUnix, "", fdProvider(test.arg.fd))
		if (err != nil) != test.wantErr {
			t.Errorf("protect failed for [%s]", test.name)
			return
		}
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

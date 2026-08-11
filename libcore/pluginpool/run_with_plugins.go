package pluginpool

import (
	"os"
	"time"

	"libcore/pb/husi/v1"

	E "github.com/sagernet/sing/common/exceptions"
)

const startupGrace = 500 * time.Millisecond

func RunWithPlugins(
	workingDir string,
	specs []*husiv1.PluginProcessSpec,
	credential ProcessCredentialFunc,
	fn func() (int32, error),
) (int32, error) {
	if len(specs) == 0 {
		return fn()
	}
	if workingDir == "" {
		return -1, E.New("plugin working directory not set")
	}
	if err := os.MkdirAll(workingDir, 0o700); err != nil {
		return -1, E.Cause(err, "create plugin working directory")
	}
	tempDir, err := os.MkdirTemp(workingDir, "urltest-")
	if err != nil {
		return -1, E.Cause(err, "create urltest plugin directory")
	}
	defer func() { _ = os.RemoveAll(tempDir) }()

	fatalCh := make(chan error, 1)
	pool := NewPluginPool(tempDir, func(fatal error) {
		select {
		case fatalCh <- fatal:
		default:
		}
	})
	if credential != nil {
		pool.SetProcessCredential(credential)
	}
	defer func() { _ = pool.Close() }()

	if err := pool.StartAll(specs); err != nil {
		return -1, E.Cause(err, "start urltest plugins")
	}
	time.Sleep(startupGrace)

	select {
	case fatal := <-fatalCh:
		return -1, E.Cause(fatal, "urltest plugin fatal")
	default:
	}

	type result struct {
		latency int32
		err     error
	}
	done := make(chan result, 1)
	go func() {
		latency, runErr := fn()
		done <- result{latency: latency, err: runErr}
	}()

	select {
	case fatal := <-fatalCh:
		// Pool Close in defer kills children; wait for fn to exit so we do not
		// leave the throwaway instance racing with teardown.
		<-done
		return -1, E.Cause(fatal, "urltest plugin fatal")
	case r := <-done:
		select {
		case fatal := <-fatalCh:
			if r.err == nil {
				return -1, E.Cause(fatal, "urltest plugin fatal")
			}
			return -1, E.Errors(r.err, E.Cause(fatal, "urltest plugin fatal"))
		default:
			return r.latency, r.err
		}
	}
}

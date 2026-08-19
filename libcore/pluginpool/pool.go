package pluginpool

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

type ProcessCredentialFunc func() (*syscall.SysProcAttr, error)

const (
	// fatalExitWindow matches the Kotlin GuardedProcessPool: exit within this
	// duration after start is treated as fatal rather than restartable.
	fatalExitWindow = time.Second
	// stopGracePeriod is how long Close waits after SIGTERM before SIGKILL.
	stopGracePeriod = time.Second

	fileTokenPrefix = "${file:"
	fileTokenSuffix = "}"
)

type PluginPool struct {
	access            sync.Mutex
	workingDir        string
	onFatal           func(error)
	processCredential ProcessCredentialFunc
	guards            []*pluginGuard
	closed            bool
}

func NewPluginPool(workingDir string, onFatal func(error)) *PluginPool {
	return &PluginPool{
		workingDir: workingDir,
		onFatal:    onFatal,
	}
}

func (p *PluginPool) SetProcessCredential(fn ProcessCredentialFunc) {
	p.access.Lock()
	defer p.access.Unlock()
	p.processCredential = fn
}

func (p *PluginPool) Start(spec *husiv1.PluginProcessSpec) error {
	if spec == nil {
		return E.New("nil plugin process spec")
	}
	p.access.Lock()
	defer p.access.Unlock()
	if p.closed {
		return E.New("plugin pool closed")
	}
	if p.workingDir == "" {
		return E.New("missing plugin working directory")
	}
	if err := os.MkdirAll(p.workingDir, 0o700); err != nil {
		return E.Cause(err, "create plugin working directory")
	}

	var sysProcAttr *syscall.SysProcAttr
	if p.processCredential != nil {
		attr, err := p.processCredential()
		if err != nil {
			return E.Cause(err, "process credentials for plugin")
		}
		sysProcAttr = attr
	}
	guard, err := newPluginGuard(p.workingDir, spec, p.handleFatal, sysProcAttr)
	if err != nil {
		return err
	}
	if err := guard.start(); err != nil {
		_ = guard.cleanupFiles()
		return err
	}
	p.guards = append(p.guards, guard)
	go guard.loop()
	return nil
}

func (p *PluginPool) StartAll(specs []*husiv1.PluginProcessSpec) error {
	for _, spec := range specs {
		if err := p.Start(spec); err != nil {
			_ = p.Close()
			return err
		}
	}
	return nil
}

func (p *PluginPool) Close() error {
	p.access.Lock()
	if p.closed {
		p.access.Unlock()
		return nil
	}
	p.closed = true
	guards := p.guards
	p.guards = nil
	p.access.Unlock()

	var errs []error
	for _, guard := range guards {
		if err := guard.stop(); err != nil {
			errs = append(errs, err)
		}
	}
	return E.Errors(errs...)
}

func (p *PluginPool) handleFatal(err error) {
	if err == nil || p.onFatal == nil {
		return
	}
	log.Error("plugin pool fatal: ", err)
	// Async so a fatal during Start (caller may hold locks) cannot deadlock.
	go p.onFatal(err)
}

type pluginGuard struct {
	name        string
	command     []string
	env         []string
	workingDir  string
	filePaths   []string
	onFatal     func(error)
	sysProcAttr *syscall.SysProcAttr

	access  sync.Mutex
	cmd     *exec.Cmd
	process *os.Process
	stopCh  chan struct{}
	doneCh  chan struct{}
	stopped bool
}

func newPluginGuard(workingDir string, spec *husiv1.PluginProcessSpec, onFatal func(error), sysProcAttr *syscall.SysProcAttr) (*pluginGuard, error) {
	name := spec.GetName()
	if name == "" {
		name = "plugin"
	}
	command := spec.GetCommand()
	if len(command) == 0 {
		return nil, E.New(name, ": empty command")
	}

	filePaths, fileAbs, err := writePluginFiles(workingDir, name, spec.GetFiles())
	if err != nil {
		return nil, err
	}

	expandedCommand := make([]string, len(command))
	for i, arg := range command {
		expandedCommand[i] = expandFileTokens(arg, fileAbs)
	}
	env := os.Environ()
	for key, value := range spec.GetEnvironment() {
		env = append(env, key+"="+expandFileTokens(value, fileAbs))
	}

	return &pluginGuard{
		name:        name,
		command:     expandedCommand,
		env:         env,
		workingDir:  workingDir,
		filePaths:   filePaths,
		onFatal:     onFatal,
		sysProcAttr: sysProcAttr,
		stopCh:      make(chan struct{}),
		doneCh:      make(chan struct{}),
	}, nil
}

func writePluginFiles(workingDir, pluginName string, files []*husiv1.PluginFile) ([]string, map[string]string, error) {
	fileAbs := make(map[string]string, len(files))
	paths := make([]string, 0, len(files))
	for _, file := range files {
		if file == nil {
			continue
		}
		name := file.GetName()
		if err := validatePluginFileName(name); err != nil {
			return nil, nil, E.Cause(err, pluginName)
		}
		path := filepath.Join(workingDir, name)
		if err := os.WriteFile(path, file.GetContent(), 0o600); err != nil {
			for _, written := range paths {
				_ = os.Remove(written)
			}
			return nil, nil, E.Cause(err, "write plugin file ", name)
		}
		paths = append(paths, path)
		fileAbs[name] = path
	}
	return paths, fileAbs, nil
}

func validatePluginFileName(name string) error {
	if name == "" {
		return E.New("empty plugin file name")
	}
	if name != filepath.Base(name) || strings.Contains(name, `\`) || strings.Contains(name, "/") {
		return E.New("plugin file name must not contain a path separator: ", name)
	}
	if name == "." || name == ".." {
		return E.New("invalid plugin file name: ", name)
	}
	return nil
}

func expandFileTokens(value string, files map[string]string) string {
	if !strings.Contains(value, fileTokenPrefix) {
		return value
	}
	var builder strings.Builder
	remaining := value
	for {
		start := strings.Index(remaining, fileTokenPrefix)
		if start < 0 {
			builder.WriteString(remaining)
			break
		}
		builder.WriteString(remaining[:start])
		remaining = remaining[start+len(fileTokenPrefix):]
		end := strings.Index(remaining, fileTokenSuffix)
		if end < 0 {
			// Unterminated token: keep the rest literally.
			builder.WriteString(fileTokenPrefix)
			builder.WriteString(remaining)
			break
		}
		name := remaining[:end]
		remaining = remaining[end+len(fileTokenSuffix):]
		if path, ok := files[name]; ok {
			builder.WriteString(path)
		} else {
			// Unknown token left as-is so misconfiguration is visible in process args.
			builder.WriteString(fileTokenPrefix)
			builder.WriteString(name)
			builder.WriteString(fileTokenSuffix)
		}
	}
	return builder.String()
}

func (g *pluginGuard) start() error {
	cmd := exec.Command(g.command[0], g.command[1:]...)
	cmd.Dir = g.workingDir
	cmd.Env = g.env
	if g.sysProcAttr != nil {
		cmd.SysProcAttr = g.sysProcAttr
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return E.Cause(err, g.name, ": stdout pipe")
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return E.Cause(err, g.name, ": stderr pipe")
	}
	if err := cmd.Start(); err != nil {
		return E.Cause(err, "start process ", g.name)
	}
	log.Info("start process: ", strings.Join(g.command, " "))
	g.access.Lock()
	g.cmd = cmd
	g.process = cmd.Process
	g.access.Unlock()

	go streamLines(stdout, func(line string) {
		log.Debug("[", g.name, "] ", line)
	})
	go streamLines(stderr, func(line string) {
		log.Debug("[", g.name, "]", line)
	})
	return nil
}

func streamLines(r io.Reader, logLine func(string)) {
	scanner := bufio.NewScanner(r)
	// Plugin logs can be long; raise the token limit modestly above the default 64KiB.
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		logLine(scanner.Text())
	}
}

func (g *pluginGuard) loop() {
	defer close(g.doneCh)
	for {
		startTime := time.Now()
		exitErr := g.wait()
		select {
		case <-g.stopCh:
			return
		default:
		}

		exitCode := exitCodeOf(exitErr)
		elapsed := time.Since(startTime)
		if elapsed < fatalExitWindow {
			err := fmt.Errorf("%s exits too fast (exit code: %d)", g.name, exitCode)
			g.onFatal(err)
			return
		}
		log.Warn(fmt.Sprintf("%s unexpectedly exits with code %d", g.name, exitCode))
		log.Info("restart process: ", strings.Join(g.command, " "), " (last exit code: ", exitCode, ")")

		select {
		case <-g.stopCh:
			return
		default:
		}
		if err := g.start(); err != nil {
			g.onFatal(E.Cause(err, "restart process ", g.name))
			return
		}
	}
}

func (g *pluginGuard) wait() error {
	g.access.Lock()
	cmd := g.cmd
	g.access.Unlock()
	if cmd == nil {
		return E.New("process not started")
	}
	return cmd.Wait()
}

func (g *pluginGuard) stop() error {
	g.access.Lock()
	if g.stopped {
		g.access.Unlock()
		return nil
	}
	g.stopped = true
	close(g.stopCh)
	process := g.process
	g.access.Unlock()

	if process != nil {
		_ = signalTerminate(process)
		select {
		case <-g.doneCh:
		case <-time.After(stopGracePeriod):
			_ = process.Kill()
			select {
			case <-g.doneCh:
			case <-time.After(stopGracePeriod):
			}
		}
	} else {
		<-g.doneCh
	}
	return g.cleanupFiles()
}

func (g *pluginGuard) cleanupFiles() error {
	var errs []error
	for _, path := range g.filePaths {
		if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
			errs = append(errs, err)
		}
	}
	return E.Errors(errs...)
}

func exitCodeOf(err error) int {
	if err == nil {
		return 0
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return exitErr.ExitCode()
	}
	return -1
}

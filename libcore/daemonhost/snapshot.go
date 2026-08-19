package daemonhost

import (
	"encoding/json"
	"os"
	"path/filepath"

	E "github.com/sagernet/sing/common/exceptions"

	husiv1 "github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

const (
	snapshotConfigFile    = "config.snapshot"
	snapshotPluginsFile   = "plugins.snapshot"
	snapshotMetadataFile  = "client_metadata.snapshot"
	snapshotOptionsFile   = "options.snapshot"
	wasRunningMarkerFile  = "was_running"
	startAtBootMarkerFile = "start_at_boot"
	ownerStateFile        = "owner.json"
)

type Snapshot struct {
	Config         string                      `json:"config"`
	Plugins        []*husiv1.PluginProcessSpec `json:"plugins,omitempty"`
	ClientMetadata *husiv1.ClientMetadata      `json:"client_metadata,omitempty"`
	Options        *husiv1.ServiceOptions      `json:"options,omitempty"`
}

type ownerState struct {
	UID       uint32 `json:"uid,omitempty"`
	GID       uint32 `json:"gid,omitempty"`
	SID       string `json:"sid,omitempty"`
	SessionID uint32 `json:"session_id,omitempty"`
	Username  string `json:"username,omitempty"`
}

func SaveSnapshot(dir string, snapshot *Snapshot) error {
	if dir == "" {
		return E.New("missing snapshot directory")
	}
	if snapshot == nil {
		return E.New("missing snapshot")
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return E.Cause(err, "create snapshot directory")
	}
	if err := atomicWriteFile(filepath.Join(dir, snapshotConfigFile), []byte(snapshot.Config), 0o600); err != nil {
		return E.Cause(err, "write config snapshot")
	}
	if err := writeJSONAtomic(filepath.Join(dir, snapshotPluginsFile), snapshot.Plugins); err != nil {
		return E.Cause(err, "write plugins snapshot")
	}
	if err := writeJSONAtomic(filepath.Join(dir, snapshotMetadataFile), snapshot.ClientMetadata); err != nil {
		return E.Cause(err, "write client metadata snapshot")
	}
	if err := writeJSONAtomic(filepath.Join(dir, snapshotOptionsFile), snapshot.Options); err != nil {
		return E.Cause(err, "write options snapshot")
	}
	return nil
}

func LoadSnapshot(dir string) (*Snapshot, error) {
	configPath := filepath.Join(dir, snapshotConfigFile)
	configBytes, err := os.ReadFile(configPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, E.Cause(err, "read config snapshot")
	}
	snapshot := &Snapshot{Config: string(configBytes)}
	if err := readJSONIfExists(filepath.Join(dir, snapshotPluginsFile), &snapshot.Plugins); err != nil {
		return nil, E.Cause(err, "read plugins snapshot")
	}
	if err := readJSONIfExists(filepath.Join(dir, snapshotMetadataFile), &snapshot.ClientMetadata); err != nil {
		return nil, E.Cause(err, "read client metadata snapshot")
	}
	if err := readJSONIfExists(filepath.Join(dir, snapshotOptionsFile), &snapshot.Options); err != nil {
		return nil, E.Cause(err, "read options snapshot")
	}
	return snapshot, nil
}

func ClearSnapshot(dir string) error {
	var errs []error
	for _, name := range []string{
		snapshotConfigFile,
		snapshotPluginsFile,
		snapshotMetadataFile,
		snapshotOptionsFile,
	} {
		if err := os.Remove(filepath.Join(dir, name)); err != nil && !os.IsNotExist(err) {
			errs = append(errs, err)
		}
	}
	return E.Errors(errs...)
}

func SetWasRunning(dir string, running bool) error {
	path := filepath.Join(dir, wasRunningMarkerFile)
	if running {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return err
		}
		return atomicWriteFile(path, []byte("1\n"), 0o600)
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func WasRunning(dir string) bool {
	_, err := os.Stat(filepath.Join(dir, wasRunningMarkerFile))
	return err == nil
}

func SetStartAtBoot(dir string, enabled bool) error {
	path := filepath.Join(dir, startAtBootMarkerFile)
	if enabled {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return err
		}
		return atomicWriteFile(path, []byte("1\n"), 0o600)
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func StartAtBoot(dir string) bool {
	_, err := os.Stat(filepath.Join(dir, startAtBootMarkerFile))
	return err == nil
}

func SaveOwnerState(dir string, identity PeerIdentity) error {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	state := ownerState{
		UID:       identity.UID,
		GID:       identity.GID,
		SID:       identity.SID,
		SessionID: identity.SessionID,
		Username:  identity.Username,
	}
	return writeJSONAtomic(filepath.Join(dir, ownerStateFile), state)
}

func LoadOwnerState(dir string) (*PeerIdentity, error) {
	path := filepath.Join(dir, ownerStateFile)
	content, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	if len(content) == 0 || string(content) == "null\n" || string(content) == "null" {
		return nil, nil
	}
	var state ownerState
	if err := json.Unmarshal(content, &state); err != nil {
		return nil, err
	}
	// UID 0 is a valid root owner on unix; SID empty is normal on unix.
	return &PeerIdentity{
		UID:       state.UID,
		GID:       state.GID,
		SID:       state.SID,
		SessionID: state.SessionID,
		Username:  state.Username,
	}, nil
}

func ClearOwnerState(dir string) error {
	if err := os.Remove(filepath.Join(dir, ownerStateFile)); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func writeJSONAtomic(path string, value any) error {
	if value == nil {
		// Write empty JSON null so Load can distinguish missing vs present.
		return atomicWriteFile(path, []byte("null\n"), 0o600)
	}
	content, err := json.Marshal(value)
	if err != nil {
		return err
	}
	content = append(content, '\n')
	return atomicWriteFile(path, content, 0o600)
}

func readJSONIfExists(path string, dest any) error {
	content, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	if len(content) == 0 || string(content) == "null\n" || string(content) == "null" {
		return nil
	}
	return json.Unmarshal(content, dest)
}

func atomicWriteFile(path string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	cleanup := true
	defer func() {
		if cleanup {
			_ = os.Remove(tmpName)
		}
	}()
	if _, err := tmp.Write(data); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Chmod(perm); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Rename(tmpName, path); err != nil {
		return err
	}
	cleanup = false
	return nil
}

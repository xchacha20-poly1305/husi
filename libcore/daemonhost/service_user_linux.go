//go:build linux

package daemonhost

import (
	"io/fs"
	"os"
	"os/exec"
	"os/user"
	"path/filepath"
	"strconv"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
)

const (
	daemonUserName  = "husi"
	daemonUserGecos = "Husi Core Daemon"
)

type daemonAccount struct {
	Name string
	UID  int
	GID  int
}

func ensureDaemonUser() (*daemonAccount, error) {
	if account, err := lookupDaemonAccount(); err == nil {
		return account, nil
	}
	creationError := createDaemonUser()
	account, err := lookupDaemonAccount()
	if err != nil {
		if creationError != nil {
			return nil, creationError
		}
		return nil, err
	}
	return account, nil
}

func lookupDaemonAccount() (*daemonAccount, error) {
	daemonUser, err := user.Lookup(daemonUserName)
	if err != nil {
		return nil, err
	}
	uid, err := strconv.Atoi(daemonUser.Uid)
	if err != nil {
		return nil, E.Cause(err, "parse uid of user ", daemonUserName)
	}
	gid, err := strconv.Atoi(daemonUser.Gid)
	if err != nil {
		return nil, E.Cause(err, "parse gid of user ", daemonUserName)
	}
	return &daemonAccount{Name: daemonUserName, UID: uid, GID: gid}, nil
}

func createDaemonUser() error {
	sysusersConfig := "u " + daemonUserName + ` - "` + daemonUserGecos + `"` + "\n"
	sysusers := exec.Command("systemd-sysusers", "-")
	sysusers.Stdin = strings.NewReader(sysusersConfig)
	checkUserOutput, checkUserError := sysusers.CombinedOutput()
	if checkUserError == nil {
		return nil
	}
	useradd := exec.Command("useradd",
		"--system",
		"--user-group",
		"--no-create-home",
		"--shell", "/usr/sbin/nologin",
		"--comment", daemonUserGecos,
		daemonUserName,
	)
	useraddOutput, useraddErr := useradd.CombinedOutput()
	if useraddErr != nil {
		return E.New("create user ", daemonUserName,
			": systemd-sysusers: ", commandMessage(checkUserOutput, checkUserError),
			"; useradd: ", commandMessage(useraddOutput, useraddErr))
	}
	return nil
}

func commandMessage(output []byte, err error) string {
	message := strings.TrimSpace(string(output))
	if message == "" {
		return err.Error()
	}
	return message
}

func chownTree(root string, uid, gid int) error {
	return filepath.WalkDir(root, func(path string, _ fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		chownErr := os.Lchown(path, uid, gid)
		if chownErr != nil {
			return E.Cause(chownErr, "chown ", path)
		}
		return nil
	})
}

package protect

import (
	"os"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/protect-go"
)

// verifyPluginPeer verifies if the request come from plugins.
func verifyPluginPeer(request *protect.Request) error {
	peerProcessID, err := peerProcessIDOf(request.Conn)
	if err != nil {
		return E.Cause(err, "identify peer")
	}
	descendant, err := isDescendant(peerProcessID, os.Getpid(), parentProcessIDOf)
	if err != nil {
		return E.Cause(err, "trace ancestors of peer ", peerProcessID)
	}
	if !descendant {
		return E.New("peer ", peerProcessID, " is not a plugin")
	}
	return nil
}

// isDescendant reports whether processID is ancestorID or one of its descendants.
func isDescendant(processID, ancestorID int, parentOf func(processID int) (int, error)) (bool, error) {
	const initProcessID = 1
	for {
		if processID == ancestorID {
			return true, nil
		}
		if processID <= initProcessID {
			return false, nil
		}
		var err error
		processID, err = parentOf(processID)
		if err != nil {
			return false, err
		}
	}
}

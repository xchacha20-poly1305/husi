//go:build linux

package daemonhost

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"math"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/godbus/dbus/v5"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const (
	policyKitService            = "org.freedesktop.PolicyKit1"
	policyKitAuthorityPath      = dbus.ObjectPath("/org/freedesktop/PolicyKit1/Authority")
	policyKitAuthorityInterface = "org.freedesktop.PolicyKit1.Authority"
	policyKitUnixProcessSubject = "unix-process"
	// AllowUserInteraction: polkit may show a password dialog. Without this
	// flag the check fails immediately when the caller is not already
	// authorized.
	policyKitAllowUserInteraction = uint32(1)
	policyKitCancellationPrefix   = "husi-daemon-"
	policyKitCancellationBytes    = 16
)

type policyKitSubject struct {
	Kind    string
	Details map[string]dbus.Variant
}

type policyKitAuthorizationResult struct {
	Authorized bool
	Challenge  bool
	Details    map[string]string
}

func authorizeTakeOver(ctx context.Context, identity PeerIdentity) error {
	return checkPolicyKitAuthorization(ctx, identity, polkitActionTakeOver)
}

func checkPolicyKitAuthorization(ctx context.Context, identity PeerIdentity, action string) error {
	subject, err := policyKitSubjectOf(identity)
	if err != nil {
		return err
	}
	cancellationID := newPolicyKitCancellationID()
	connection, err := dbus.ConnectSystemBus()
	if err != nil {
		return E.Cause(err, "connect to system bus")
	}
	defer connection.Close()

	authority := connection.Object(policyKitService, policyKitAuthorityPath)
	resultChannel := make(chan *dbus.Call, 1)
	authority.Go(
		policyKitAuthorityInterface+".CheckAuthorization",
		0,
		resultChannel,
		subject,
		action,
		map[string]string{},
		policyKitAllowUserInteraction,
		cancellationID,
	)
	select {
	case call := <-resultChannel:
		if call.Err != nil {
			return E.Cause(call.Err, "check polkit authorization")
		}
		var result policyKitAuthorizationResult
		if err := call.Store(&result); err != nil {
			return E.Cause(err, "read polkit authorization result")
		}
		if result.Authorized {
			return nil
		}
		if result.Challenge {
			return status.Error(codes.Unauthenticated, "no authentication agent is available")
		}
		return status.Error(codes.PermissionDenied, "authorization was denied")
	case <-ctx.Done():
		_ = authority.Call(
			policyKitAuthorityInterface+".CancelCheckAuthorization",
			0,
			cancellationID,
		).Err
		return status.Error(codes.Canceled, "authorization was canceled")
	}
}

func policyKitSubjectOf(identity PeerIdentity) (policyKitSubject, error) {
	if identity.PID <= 0 || identity.ProcessStartTime == 0 {
		return policyKitSubject{}, status.Error(codes.Unauthenticated, "daemon peer has an incomplete process identity")
	}
	if identity.UID > math.MaxInt32 {
		return policyKitSubject{}, status.Error(codes.Unauthenticated, "daemon peer has an invalid user id")
	}
	return policyKitSubject{
		Kind: policyKitUnixProcessSubject,
		Details: map[string]dbus.Variant{
			"pid":        dbus.MakeVariant(uint32(identity.PID)),
			"start-time": dbus.MakeVariant(identity.ProcessStartTime),
			"uid":        dbus.MakeVariant(int32(identity.UID)),
		},
	}, nil
}

func newPolicyKitCancellationID() string {
	content := make([]byte, policyKitCancellationBytes)
	_, _ = rand.Read(content)
	return policyKitCancellationPrefix + hex.EncodeToString(content)
}

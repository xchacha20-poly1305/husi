package daemonhost

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestOwnerStoreClaimAndTakeOver(t *testing.T) {
	store := NewOwnerStore(nil)
	alice := PeerIdentity{UID: 1000, Username: "alice"}
	bob := PeerIdentity{UID: 1001, Username: "bob"}

	require.False(t, store.HasOwner(), "expected unclaimed store")
	require.NoError(t, store.Claim(alice))
	assert.True(t, store.IsOwner(alice), "alice should be owner")
	assert.False(t, store.IsOwner(bob), "bob should not be owner")
	require.Error(t, store.Claim(bob), "Claim bob should fail while alice owns")

	// Re-claim by same user refreshes metadata.
	alice2 := PeerIdentity{UID: 1000, Username: "alice", PID: 42}
	require.NoError(t, store.Claim(alice2))
	assert.Equal(t, int32(42), store.Owner().PID)

	require.NoError(t, store.TakeOver(bob))
	assert.True(t, store.IsOwner(bob), "bob should own after takeover")
	info := store.OwnershipInfo(&bob)
	assert.True(t, info.GetClaimed())
	assert.True(t, info.GetOwnedByCaller())
	assert.Equal(t, "1001", info.GetOwnerId())
	infoAlice := store.OwnershipInfo(&alice)
	assert.False(t, infoAlice.GetOwnedByCaller(), "alice should not own by caller after takeover")
}

func TestOwnerStoreWindowsSID(t *testing.T) {
	store := NewOwnerStore(nil)
	a := PeerIdentity{SID: "S-1-5-21-1", Username: "alice"}
	b := PeerIdentity{SID: "S-1-5-21-2", Username: "bob"}
	require.NoError(t, store.Claim(a))
	assert.True(t, store.IsOwner(PeerIdentity{SID: "s-1-5-21-1"}), "SID comparison should be case-insensitive")
	assert.False(t, store.IsOwner(b), "different SID should not match")
}

func TestPeerIdentityEqual(t *testing.T) {
	a := PeerIdentity{UID: 1}
	b := PeerIdentity{UID: 1}
	c := PeerIdentity{UID: 2}
	assert.True(t, a.Equal(b), "same uid")
	assert.False(t, a.Equal(c), "different uid")
	sidA := PeerIdentity{SID: "S-1"}
	sidB := PeerIdentity{SID: "s-1"}
	assert.True(t, sidA.Equal(sidB), "sid case")
}

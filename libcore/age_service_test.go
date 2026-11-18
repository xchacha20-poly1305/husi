package libcore

import (
	"bytes"
	"context"
	"crypto/sha256"
	"io"
	"net"
	"testing"
	"time"

	N "github.com/sagernet/sing/common/network"

	"filippo.io/age"
	"filippo.io/age/armor"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/status"
)

func TestAgeDecryptionOverLocalIPC(t *testing.T) {
	client := dialApplicationService(t)
	classic, err := age.GenerateX25519Identity()
	require.NoError(t, err)
	hybrid, err := age.GenerateHybridIdentity()
	require.NoError(t, err)
	payload := bytes.Repeat([]byte("ss://encrypted-subscription\n"), 180000)
	expected := sha256.Sum256(payload)
	ctx, cancel := context.WithTimeout(t.Context(), 15*time.Second)
	defer cancel()

	for name, recipient := range map[string]age.Recipient{
		"X25519": classic.Recipient(),
		"hybrid": hybrid.Recipient(),
	} {
		t.Run(name, func(t *testing.T) {
			var ciphertext bytes.Buffer
			armored := armor.NewWriter(&ciphertext)
			writer, err := age.Encrypt(armored, recipient)
			require.NoError(t, err)
			_, err = writer.Write(payload)
			require.NoError(t, err)
			require.NoError(t, writer.Close())
			require.NoError(t, armored.Close())

			stream, err := client.DecryptAge(ctx)
			require.NoError(t, err)
			require.NoError(t, stream.Send(ageIdentityMessage(classic.String()+"\n"+hybrid.String())))
			sent := make(chan error, 1)
			go func() {
				sent <- sendAgeCiphertext(stream, ciphertext.Bytes())
			}()

			hash := sha256.New()
			size := 0
			for {
				result, err := stream.Recv()
				if err == io.EOF {
					break
				}
				require.NoError(t, err)
				size += len(result.GetChunk())
				_, _ = hash.Write(result.GetChunk())
			}
			require.NoError(t, <-sent)
			require.Equal(t, len(payload), size)
			require.Equal(t, expected[:], hash.Sum(nil))
			t.Logf("%d bytes, SHA-256 %x", size, hash.Sum(nil))
		})
	}

	t.Run("wrong identity", func(t *testing.T) {
		var ciphertext bytes.Buffer
		armored := armor.NewWriter(&ciphertext)
		writer, err := age.Encrypt(armored, hybrid.Recipient())
		require.NoError(t, err)
		_, err = writer.Write([]byte("subscription"))
		require.NoError(t, err)
		require.NoError(t, writer.Close())
		require.NoError(t, armored.Close())

		stream, err := client.DecryptAge(ctx)
		require.NoError(t, err)
		require.NoError(t, stream.Send(ageIdentityMessage(classic.String())))
		require.NoError(t, sendAgeCiphertext(stream, ciphertext.Bytes()))
		_, err = stream.Recv()
		require.Equal(t, codes.InvalidArgument, status.Code(err))
	})

	t.Run("cancel while waiting for HTTP body", func(t *testing.T) {
		cancellable, stop := context.WithCancel(ctx)
		defer stop()
		stream, err := client.DecryptAge(cancellable)
		require.NoError(t, err)
		require.NoError(t, stream.Send(ageIdentityMessage(classic.String())))
		stop()
		_, err = stream.Recv()
		require.Equal(t, codes.Canceled, status.Code(err))
	})
}

func TestAgeDecryptionRejectsTCP(t *testing.T) {
	listener, err := net.Listen(N.NetworkTCP, "127.0.0.1:0")
	require.NoError(t, err)
	server := grpc.NewServer()
	NewApplicationService(nil, nil).RegisterServices(server, health.NewServer())
	go func() {
		_ = server.Serve(listener)
	}()
	t.Cleanup(server.Stop)

	conn, err := grpc.NewClient(
		listener.Addr().String(),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	require.NoError(t, err)
	t.Cleanup(func() {
		_ = conn.Close()
	})
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	stream, err := husiv1.NewApplicationServiceClient(conn).DecryptAge(ctx)
	require.NoError(t, err)
	_, err = stream.Recv()
	require.Equal(t, codes.PermissionDenied, status.Code(err))
}

func ageIdentityMessage(identities string) *husiv1.DecryptAgeRequest {
	return &husiv1.DecryptAgeRequest{Payload: &husiv1.DecryptAgeRequest_Identities{Identities: identities}}
}

func sendAgeCiphertext(stream husiv1.ApplicationService_DecryptAgeClient, ciphertext []byte) error {
	for len(ciphertext) > 0 {
		size := min(ageChunkSize, len(ciphertext))
		if err := stream.Send(&husiv1.DecryptAgeRequest{
			Payload: &husiv1.DecryptAgeRequest_Chunk{Chunk: ciphertext[:size]},
		}); err != nil {
			return err
		}
		ciphertext = ciphertext[size:]
	}
	return stream.CloseSend()
}

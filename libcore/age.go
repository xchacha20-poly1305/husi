package libcore

import (
	"io"
	"strings"

	"github.com/sagernet/sing/common/buf"

	"filippo.io/age"
	"filippo.io/age/armor"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/status"
)

const ageChunkSize = 16 * 1024

func parseAgeIdentities(text string) ([]age.Identity, error) {
	return age.ParseIdentities(strings.NewReader(text))
}

func (s *applicationService) DecryptAge(
	stream grpc.BidiStreamingServer[husiv1.DecryptAgeRequest, husiv1.DecryptAgeResponse],
) error {
	ctx := stream.Context()
	peerInfo, hasPeer := peer.FromContext(ctx)
	if !hasPeer || peerInfo.Addr == nil {
		return status.Error(codes.PermissionDenied, "age decryption requires local IPC")
	}
	switch peerInfo.Addr.Network() {
	case "unix", "pipe":
	default:
		return status.Error(codes.PermissionDenied, "age decryption requires local IPC")
	}

	head, err := stream.Recv()
	if err == io.EOF {
		return status.Error(codes.InvalidArgument, "missing age identities")
	}
	if err != nil {
		return callError(ctx, err)
	}
	if head.GetIdentities() == "" {
		return status.Error(codes.InvalidArgument, "missing age identities")
	}
	identities, err := parseAgeIdentities(head.GetIdentities())
	if err != nil {
		return status.Error(codes.InvalidArgument, "invalid age identities")
	}
	plaintext, err := age.Decrypt(armor.NewReader(&ageCiphertextReader{recv: stream.Recv}), identities...)
	if err != nil {
		return ageReadError(stream, err)
	}
	buffer := buf.New()
	defer buffer.Release()
	for {
		n, readErr := buffer.ReadOnceFrom(plaintext)
		if n > 0 {
			err := stream.Send(&husiv1.DecryptAgeResponse{Chunk: buffer.Bytes()})
			if err != nil {
				return callError(ctx, err)
			}
		}
		buffer.Reset()
		if readErr == io.EOF {
			return nil
		}
		if readErr != nil {
			return ageReadError(stream, readErr)
		}
	}
}

func ageReadError(
	stream grpc.BidiStreamingServer[husiv1.DecryptAgeRequest, husiv1.DecryptAgeResponse],
	err error,
) error {
	if stream.Context().Err() != nil || status.Code(err) != codes.Unknown {
		return callError(stream.Context(), err)
	}
	return status.Error(codes.InvalidArgument, "decrypt age response: "+err.Error())
}

type ageCiphertextReader struct {
	recv    func() (*husiv1.DecryptAgeRequest, error)
	pending []byte
}

func (r *ageCiphertextReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if len(r.pending) == 0 {
		request, err := r.recv()
		if err != nil {
			return 0, err
		}
		r.pending = request.GetChunk()
		if len(r.pending) == 0 || len(r.pending) > ageChunkSize {
			return 0, status.Error(codes.InvalidArgument, "invalid age ciphertext chunk")
		}
	}
	n := copy(p, r.pending)
	r.pending = r.pending[n:]
	return n, nil
}

package libcore

import (
	"io"
	"net/http"

	C "github.com/sagernet/sing-box/constant"

	"github.com/xchacha20-poly1305/husi/libcore/v2/httpfetch"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
)

// httpFetchChunkSize keeps each body message far below gRPC's 4 MiB limit.
const httpFetchChunkSize = 32 * 1024

func (s *applicationService) HTTPFetch(
	req *husiv1.HTTPFetchRequest,
	stream grpc.ServerStreamingServer[husiv1.HTTPFetchResponse],
) error {
	request, err := httpFetchRequestFromProto(req)
	if err != nil {
		return rpcError(err, codes.InvalidArgument)
	}
	response, err := httpfetch.Get(stream.Context(), request)
	if err != nil {
		return streamError(stream.Context(), err)
	}
	defer response.Body.Close()

	err = stream.Send(&husiv1.HTTPFetchResponse{
		Payload: &husiv1.HTTPFetchResponse_Head{Head: &husiv1.HTTPFetchHead{
			Headers:       firstHeaderValues(response.Header),
			ContentLength: response.ContentLength,
		}},
	})
	if err != nil {
		return streamError(stream.Context(), err)
	}

	chunk := make([]byte, httpFetchChunkSize)
	for {
		n, readErr := response.Body.Read(chunk)
		if n > 0 {
			// Send marshals before returning, so the chunk can be reused.
			err = stream.Send(&husiv1.HTTPFetchResponse{
				Payload: &husiv1.HTTPFetchResponse_Chunk{Chunk: chunk[:n]},
			})
			if err != nil {
				return streamError(stream.Context(), err)
			}
		}
		if readErr == io.EOF {
			return nil
		}
		if readErr != nil {
			return streamError(stream.Context(), readErr)
		}
	}
}

func httpFetchRequestFromProto(req *husiv1.HTTPFetchRequest) (httpfetch.Request, error) {
	request := httpfetch.Request{
		URL:           req.GetUrl(),
		Header:        http.Header{},
		Timeout:       C.TCPTimeout,
		RestrictedTLS: req.GetRestrictedTls(),
		PinnedSHA256:  req.GetPinnedSha256(),
	}
	for key, value := range req.GetHeaders() {
		request.Header.Set(key, value)
	}
	if req.GetNoOverallDeadline() {
		request.Timeout = 0
	}
	if socks5 := req.GetSocks5(); socks5 != nil {
		request.Socks5 = &httpfetch.Socks5{
			Port:     uint16(socks5.GetPort()),
			Username: socks5.GetUsername(),
			Password: socks5.GetPassword(),
		}
	}
	if identities := req.GetAgeIdentities(); identities != "" {
		var err error
		request.AgeIdentities, err = parseAgeIdentities(identities)
		if err != nil {
			return httpfetch.Request{}, err
		}
	}
	return request, nil
}

func firstHeaderValues(header http.Header) map[string]string {
	values := make(map[string]string, len(header))
	for key := range header {
		values[key] = header.Get(key)
	}
	return values
}

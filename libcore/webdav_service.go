package libcore

import (
	"context"
	"errors"
	"io"

	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/buf"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/webdav"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func (s *applicationService) WebDAVList(ctx context.Context, req *husiv1.WebDAVListRequest) (*husiv1.WebDAVListResponse, error) {
	entries, err := webdav.List(ctx, webdavServerFromProto(req.GetServer()))
	if err != nil {
		return nil, wrapWebdavError(ctx, err)
	}
	response := &husiv1.WebDAVListResponse{
		Entries: common.Map(entries, func(it webdav.Entry) *husiv1.WebDAVEntry {
			var modifiedUnixMs int64
			if !it.Modified.IsZero() {
				modifiedUnixMs = it.Modified.UnixMilli()
			}
			return &husiv1.WebDAVEntry{
				Name:           it.Name,
				Size:           it.Size,
				ModifiedUnixMs: modifiedUnixMs,
			}
		}),
	}
	return response, nil
}

func (s *applicationService) WebDAVUpload(
	stream grpc.ClientStreamingServer[husiv1.WebDAVUploadRequest, husiv1.WebDAVUploadResponse],
) error {
	ctx := stream.Context()
	first, err := stream.Recv()
	if err != nil {
		return callError(ctx, err)
	}
	head := first.GetHead()
	if head == nil {
		return status.Error(codes.InvalidArgument, "WebDAVUpload must start with a head")
	}
	if head.GetSize() < 0 {
		return status.Error(codes.InvalidArgument, "WebDAVUpload size must not be negative")
	}

	reader, writer := io.Pipe()
	received := make(chan error, 1)
	go func() {
		err := pumpWebDAVChunks(stream, writer)
		_ = writer.CloseWithError(err)
		received <- err
	}()
	uploadErr := webdav.Upload(ctx, webdavServerFromProto(head.GetServer()), head.GetName(), reader, head.GetSize())
	// Unblocks a receiver still writing; the stream must be idle before this
	// handler returns.
	_ = reader.CloseWithError(uploadErr)
	receiveErr := <-received
	if receiveErr != nil {
		// The upload failed because the chunks stopped coming.
		return callError(ctx, receiveErr)
	}
	if uploadErr != nil {
		return wrapWebdavError(ctx, uploadErr)
	}
	return callError(ctx, stream.SendAndClose(&husiv1.WebDAVUploadResponse{}))
}

func pumpWebDAVChunks(
	stream grpc.ClientStreamingServer[husiv1.WebDAVUploadRequest, husiv1.WebDAVUploadResponse],
	writer *io.PipeWriter,
) error {
	for {
		message, err := stream.Recv()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
		if message.GetHead() != nil {
			return status.Error(codes.InvalidArgument, "WebDAVUpload carries one head only")
		}
		_, err = writer.Write(message.GetChunk())
		if err != nil {
			return nil
		}
	}
}

func (s *applicationService) WebDAVDownload(
	req *husiv1.WebDAVDownloadRequest,
	stream grpc.ServerStreamingServer[husiv1.WebDAVDownloadResponse],
) error {
	ctx := stream.Context()
	err := webdav.Download(ctx, webdavServerFromProto(req.GetServer()), req.GetName(), webdavChunkWriter{stream})
	if err != nil {
		return wrapWebdavError(ctx, err)
	}
	return nil
}

func (s *applicationService) WebDAVDelete(ctx context.Context, req *husiv1.WebDAVDeleteRequest) (*husiv1.WebDAVDeleteResponse, error) {
	err := webdav.Delete(ctx, webdavServerFromProto(req.GetServer()), req.GetName())
	if err != nil {
		return nil, wrapWebdavError(ctx, err)
	}
	return &husiv1.WebDAVDeleteResponse{}, nil
}

type webdavChunkWriter struct {
	stream grpc.ServerStreamingServer[husiv1.WebDAVDownloadResponse]
}

func (w webdavChunkWriter) Write(content []byte) (int, error) {
	written := 0
	for written < len(content) {
		// buf.BufferSize keeps each message far below gRPC's 4 MiB limit.
		chunk := content[written:min(written+buf.BufferSize, len(content))]
		// Send marshals before returning, so the caller may reuse content.
		err := w.stream.Send(&husiv1.WebDAVDownloadResponse{Chunk: chunk})
		if err != nil {
			return written, err
		}
		written += len(chunk)
	}
	return written, nil
}

func webdavServerFromProto(server *husiv1.WebDAVServer) webdav.Server {
	return webdav.Server{
		URL:      server.GetUrl(),
		Username: server.GetUsername(),
		Password: server.GetPassword(),
		Socks5:   socks5FromProto(server.GetSocks5()),
	}
}

func wrapWebdavError(ctx context.Context, err error) error {
	if errors.Is(err, webdav.ErrInvalidName) {
		return rpcError(err, codes.InvalidArgument)
	}
	return callError(ctx, err)
}

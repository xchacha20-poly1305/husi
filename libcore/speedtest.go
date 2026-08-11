package libcore

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/url"
	"time"

	"libcore/pb/husi/v1"

	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

// progressMinInterval throttles SpeedTest progress events to ~10/s.
const progressMinInterval = 100 * time.Millisecond

// runSpeedTest performs a download or upload through an optional socks proxy
// and emits throttled progress. ctx cancels the transfer (client closed stream).
// Host-side only (ApplicationService); not part of the Kotlin FFI surface.
func runSpeedTest(
	ctx context.Context,
	req *husiv1.SpeedTestRequest,
	emit func(*husiv1.SpeedTestResponse) error,
) error {
	targetURL := req.GetUrl()
	if targetURL == "" {
		return E.New("missing url")
	}
	timeout := time.Duration(req.GetTimeoutMs()) * time.Millisecond
	if timeout <= 0 {
		timeout = 20 * time.Second
	}

	transport := &http.Transport{
		DisableKeepAlives:     true,
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
	}
	if proxyURL := req.GetSocksProxyUrl(); proxyURL != "" {
		dialer, err := socks.NewClientFromURL(new(N.DefaultDialer), proxyURL)
		if err != nil {
			return E.Cause(err, "create proxy dialer")
		}
		transport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
			return dialer.DialContext(ctx, network, M.ParseSocksaddr(addr))
		}
	}
	client := &http.Client{
		Transport: transport,
		Timeout:   timeout,
	}

	switch req.GetMode() {
	case husiv1.SpeedTestMode_SPEED_TEST_MODE_DOWNLOAD:
		return speedTestDownload(ctx, client, targetURL, req.GetUserAgent(), emit)
	case husiv1.SpeedTestMode_SPEED_TEST_MODE_UPLOAD:
		return speedTestUpload(ctx, client, targetURL, req.GetUserAgent(), req.GetUploadLengthBytes(), emit)
	default:
		return E.New("unknown speed test mode: ", req.GetMode().String())
	}
}

func speedTestDownload(
	ctx context.Context,
	client *http.Client,
	targetURL, userAgent string,
	emit func(*husiv1.SpeedTestResponse) error,
) error {
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return err
	}
	applySpeedTestHeaders(httpReq, targetURL, userAgent)
	resp, err := client.Do(httpReq)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return E.New("HTTP ", resp.Status)
	}
	reporter := newProgressReporter(resp.ContentLength, emit)
	defer reporter.flush()
	reader := &callbackReader{reader: resp.Body, callback: reporter.update}
	_, err = bufio.Copy(io.Discard, reader)
	return err
}

func speedTestUpload(
	ctx context.Context,
	client *http.Client,
	targetURL, userAgent string,
	length int64,
	emit func(*husiv1.SpeedTestResponse) error,
) error {
	if length < 0 {
		return E.New("invalid upload length")
	}
	reporter := newProgressReporter(length, emit)
	defer reporter.flush()
	body := io.NopCloser(&callbackReader{
		reader:   io.LimitReader(zeroReader{}, length),
		callback: reporter.update,
	})
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, targetURL, body)
	if err != nil {
		return err
	}
	httpReq.ContentLength = length
	applySpeedTestHeaders(httpReq, targetURL, userAgent)
	resp, err := client.Do(httpReq)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return E.New("HTTP ", resp.Status)
	}
	return nil
}

// applySpeedTestHeaders sets UA and the same-origin Referer quirk that
// speed.cloudflare.com requires.
func applySpeedTestHeaders(req *http.Request, targetURL, userAgent string) {
	if userAgent != "" {
		req.Header.Set("User-Agent", userAgent)
	}
	if parsed, err := url.Parse(targetURL); err == nil && parsed.Scheme != "" && parsed.Host != "" {
		req.Header.Set("Referer", parsed.Scheme+"://"+parsed.Host+"/")
	}
}

type progressReporter struct {
	start     time.Time
	total     int64
	saved     int64
	lastEmit  time.Time
	emit      func(*husiv1.SpeedTestResponse) error
	lastError error
}

func newProgressReporter(total int64, emit func(*husiv1.SpeedTestResponse) error) *progressReporter {
	return &progressReporter{
		start: time.Now(),
		total: total,
		emit:  emit,
	}
}

func (p *progressReporter) update(n int64) {
	if p.lastError != nil {
		return
	}
	p.saved += n
	now := time.Now()
	if !p.lastEmit.IsZero() && now.Sub(p.lastEmit) < progressMinInterval {
		return
	}
	p.lastEmit = now
	p.lastError = p.emit(p.snapshot())
}

func (p *progressReporter) flush() {
	if p.lastError != nil {
		return
	}
	if p.saved == 0 && p.total <= 0 {
		return
	}
	p.lastError = p.emit(p.snapshot())
}

func (p *progressReporter) snapshot() *husiv1.SpeedTestResponse {
	elapsed := time.Since(p.start).Seconds()
	var bps int64
	if elapsed > 0 {
		bps = int64(float64(p.saved) / elapsed)
	}
	progress := -1.0
	if p.total > 0 {
		progress = float64(p.saved) / float64(p.total)
		if progress > 1 {
			progress = 1
		}
	}
	return &husiv1.SpeedTestResponse{
		BytesPerSec:      bps,
		Progress:         progress,
		BytesTransferred: p.saved,
	}
}

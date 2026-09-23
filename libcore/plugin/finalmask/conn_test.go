package finalmask

import (
	"bytes"
	"net"
	"testing"
	"time"
)

type mockBufferConn struct {
	net.Conn
	writes [][]byte
}

func (m *mockBufferConn) Write(b []byte) (int, error) {
	cp := make([]byte, len(b))
	copy(cp, b)
	m.writes = append(m.writes, cp)
	return len(b), nil
}

func (m *mockBufferConn) Close() error {
	return nil
}

func (m *mockBufferConn) LocalAddr() net.Addr {
	return &net.TCPAddr{IP: net.ParseIP("127.0.0.1"), Port: 12345}
}

func (m *mockBufferConn) RemoteAddr() net.Addr {
	return &net.TCPAddr{IP: net.ParseIP("127.0.0.1"), Port: 443}
}

func (m *mockBufferConn) SetDeadline(t time.Time) error      { return nil }
func (m *mockBufferConn) SetReadDeadline(t time.Time) error  { return nil }
func (m *mockBufferConn) SetWriteDeadline(t time.Time) error { return nil }

func TestUserFinalmaskConfig(t *testing.T) {
	rawJSON := []byte(`{"tcp": [{"type": "fragment", "settings": {"packets": "tlshello", "lengths": ["0", "104", "1"], "delays": ["0"], "maxSplit": "0"}},{"type": "fragment", "settings": {"packets": "1-1", "lengths": ["114", "1"], "delays": ["1"], "maxSplit": "11"}}]}`)

	cfg, err := ParseFinalmaskJSON(rawJSON)
	if err != nil {
		t.Fatalf("ParseFinalmaskJSON error: %v", err)
	}
	if cfg == nil || len(cfg.TCP) != 2 {
		t.Fatalf("expected 2 TCP filter configs, got %v", cfg)
	}

	mock := &mockBufferConn{}
	wrapped, err := WrapTCPConn(mock, cfg)
	if err != nil {
		t.Fatalf("WrapTCPConn error: %v", err)
	}

	// Construct a synthetic TLS ClientHello (type 22, version 0x0303, length 300)
	payloadLen := 300
	syntheticTLS := make([]byte, 5+payloadLen)
	syntheticTLS[0] = 22 // Handshake
	syntheticTLS[1] = 0x03
	syntheticTLS[2] = 0x03
	syntheticTLS[3] = byte(payloadLen >> 8)
	syntheticTLS[4] = byte(payloadLen)
	for i := 0; i < payloadLen; i++ {
		syntheticTLS[5+i] = byte(i % 256)
	}

	n, err := wrapped.Write(syntheticTLS)
	if err != nil {
		t.Fatalf("wrapped.Write error: %v", err)
	}
	if n != len(syntheticTLS) {
		t.Fatalf("expected written len %d, got %d", len(syntheticTLS), n)
	}

	if len(mock.writes) == 0 {
		t.Fatalf("expected at least one write to underlying mock")
	}
	// Since packets: "1-1" with lengths: ["114", "1"], the first write to underlying mock should be 114 bytes!
	if len(mock.writes[0]) != 114 {
		t.Fatalf("expected first TCP write to be 114 bytes, got %d bytes", len(mock.writes[0]))
	}

	var allWritten bytes.Buffer
	for _, w := range mock.writes {
		allWritten.Write(w)
	}
	if allWritten.Len() <= len(syntheticTLS) {
		t.Fatalf("expected TLS records to add header bytes, got total %d", allWritten.Len())
	}
}

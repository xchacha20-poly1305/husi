package ipprobe

import (
	"bytes"
	"encoding/binary"
	"io"
	"net/netip"
	"testing"
	"testing/iotest"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseSTUNResponseReader(t *testing.T) {
	txID := newSTUNTxID()
	address := netip.MustParseAddrPort("[2001:db8::8]:1234")
	response := stunResponse(txID, address)
	withAttribute := func(packet []byte, kind uint16, value []byte) []byte {
		packet = bytes.Clone(packet)
		packet = binary.BigEndian.AppendUint16(packet, kind)
		packet = binary.BigEndian.AppendUint16(packet, uint16(len(value)))
		packet = append(packet, value...)
		packet = append(packet, make([]byte, (-len(value))&3)...)
		binary.BigEndian.PutUint16(packet[2:4], uint16(len(packet)-headerLen))
		return packet
	}
	fallback := []byte{0, 1, 0x12, 0x34, 192, 0, 2, 1}
	mappedOnly := withAttribute(response[:headerLen], attrMappedAddress, fallback)
	alternative := bytes.Clone(response)
	binary.BigEndian.PutUint16(alternative[headerLen:], attrXorMappedAddressAlt)
	maximum := withAttribute(response, 0x8022, make([]byte, maxMessageSize-(len(response)-headerLen)-4))
	for _, test := range []struct {
		name string
		data []byte
		want netip.AddrPort
	}{
		{"IPv6", response, address},
		{"IPv4", stunResponse(txID, netip.MustParseAddrPort("192.0.2.8:4321")), netip.MustParseAddrPort("192.0.2.8:4321")},
		{"alternate XOR", alternative, address},
		{"mapped with extra bytes", withAttribute(response[:headerLen], attrMappedAddress, append(bytes.Clone(fallback), 1, 2, 3)), netip.MustParseAddrPort("192.0.2.1:4660")},
		{"mapped fallback", mappedOnly, netip.MustParseAddrPort("192.0.2.1:4660")},
		{"XOR before mapped", withAttribute(response, attrMappedAddress, fallback), address},
		{"XOR after mapped", withAttribute(mappedOnly, attrXorMappedAddress, response[headerLen+4:]), address},
		{"padded unknown", withAttribute(response, 0x8022, []byte("abc")), address},
		{"maximum body", maximum, address},
	} {
		t.Run(test.name, func(t *testing.T) {
			id, got, err := parseSTUNResponseReader(iotest.OneByteReader(bytes.NewReader(test.data)))
			require.NoError(t, err)
			require.Equal(t, txID, id)
			require.Equal(t, test.want, got)
			packetID, packetAddress, err := parseSTUNResponse(test.data)
			require.NoError(t, err)
			require.Equal(t, id, packetID)
			require.Equal(t, got, packetAddress)
		})
	}
	stream := bytes.NewReader(append(bytes.Clone(maximum), response...))
	_, got, err := parseSTUNResponseReader(stream)
	require.NoError(t, err)
	require.Equal(t, address, got)
	require.Equal(t, len(response), stream.Len())
	_, got, err = parseSTUNResponseReader(stream)
	require.NoError(t, err)
	require.Equal(t, address, got)
	require.Zero(t, stream.Len())
}

func TestParseSTUNResponseReaderErrors(t *testing.T) {
	response := stunResponse(newSTUNTxID(), netip.MustParseAddrPort("192.0.2.8:1234"))
	modified := func(offset int, value byte) []byte {
		packet := bytes.Clone(response)
		packet[offset] = value
		return packet
	}
	unknown := append(bytes.Clone(response), 0x80, 0x22, 0, 3, 'a', 'b', 'c', 0)
	binary.BigEndian.PutUint16(unknown[2:4], uint16(len(unknown)-headerLen))
	oversized := modified(2, 0xff)
	oversized[3] = 0xff
	for _, test := range []struct {
		name string
		data []byte
		err  error
	}{
		{"truncated discarded value", unknown[:len(unknown)-2], io.EOF},
		{"truncated padding", unknown[:len(unknown)-1], io.EOF},
		{"empty stream", nil, io.EOF},
		{"truncated header", response[:10], io.ErrUnexpectedEOF},
		{"truncated attribute header", response[:headerLen+2], io.ErrUnexpectedEOF},
		{"truncated attribute value", response[:len(response)-1], io.ErrUnexpectedEOF},
		{"bad cookie", modified(4, 0), errNotSTUN},
		{"bad message type", modified(0, 0xc1), errNotSTUN},
		{"not success response", modified(1, 0), errNotSuccessResponse},
		{"unaligned body", modified(3, 13), errMalformedAttrs},
		{"oversized body", oversized, errMalformedAttrs},
		{"attribute exceeds body", modified(headerLen+3, 20), errMalformedAttrs},
		{"short address", modified(headerLen+3, 3), errMalformedAttrs},
		{"invalid family", modified(headerLen+5, 3), errMalformedAttrs},
		{"no address", modified(headerLen, 0x81), errMalformedAttrs},
	} {
		t.Run(test.name, func(t *testing.T) {
			_, _, err := parseSTUNResponseReader(iotest.OneByteReader(bytes.NewReader(test.data)))
			require.ErrorIs(t, err, test.err)
		})
	}
}

func BenchmarkParseSTUNResponseReader(b *testing.B) {
	response := stunResponse(newSTUNTxID(), netip.MustParseAddrPort("[2001:db8::8]:1234"))
	maximum := bytes.Clone(response)
	maximum = binary.BigEndian.AppendUint16(maximum, 0x8022)
	maximum = binary.BigEndian.AppendUint16(maximum, uint16(maxResponseSize-len(maximum)-2))
	maximum = append(maximum, make([]byte, maxResponseSize-len(maximum))...)
	binary.BigEndian.PutUint16(maximum[2:4], maxMessageSize)
	for _, test := range []struct {
		name string
		data []byte
	}{
		{"binding", response},
		{"maximum", maximum},
	} {
		b.Run(test.name, func(b *testing.B) {
			reader := bytes.NewReader(test.data)
			b.ReportAllocs()
			for b.Loop() {
				reader.Reset(test.data)
				_, _, err := parseSTUNResponseReader(reader)
				assert.NoError(b, err)
			}
		})
	}
}

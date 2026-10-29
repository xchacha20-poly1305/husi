// Copyright (c) Tailscale Inc & contributors
// SPDX-License-Identifier: BSD-3-Clause

// Mod from https://github.com/tailscale/tailscale/blob/3a38840ccc7748643bfb2ce260caca7b4b918f3e/net/stun/stun.go
// Removed software identification (https://github.com/tailscale/tailscale/blob/3a38840ccc7748643bfb2ce260caca7b4b918f3e/net/stun/stun.go#L29)

package ipprobe

import (
	"bytes"
	crand "crypto/rand"
	"encoding/binary"
	"errors"
	"hash/crc32"
	"net"
	"net/netip"
)

const (
	attrNumFingerprint   = 0x8028
	attrMappedAddress    = 0x0001
	attrXorMappedAddress = 0x0020
	// This alternative attribute type is not
	// mentioned in the RFC, but the shift into
	// the "comprehension-optional" range seems
	// like an easy mistake for a server to make.
	// And servers appear to send it.
	attrXorMappedAddressAlt = 0x8020

	bindingRequest = "\x00\x01"
	magicCookie    = "\x21\x12\xa4\x42"
	lenFingerprint = 8 // 2+byte header + 2-byte length + 4-byte crc32
	headerLen      = 20
)

// stunTxID is a transaction ID.
type stunTxID [12]byte

// newSTUNTxID returns a new random stunTxID.
func newSTUNTxID() stunTxID {
	var tx stunTxID
	_, _ = crand.Read(tx[:])
	return tx
}

// stunRequest generates a binding request STUN packet.
// The transaction ID, tID, should be a random sequence of bytes.
func stunRequest(tID stunTxID) []byte {
	// STUN header, RFC5389 Section 6.
	b := make([]byte, 0, headerLen+lenFingerprint)
	b = append(b, bindingRequest...)
	b = binary.BigEndian.AppendUint16(b, uint16(lenFingerprint)) // number of bytes following header
	b = append(b, magicCookie...)
	b = append(b, tID[:]...)

	// Attribute FINGERPRINT, RFC5389 Section 15.5.
	fp := fingerPrint(b)
	b = binary.BigEndian.AppendUint16(b, attrNumFingerprint)
	b = binary.BigEndian.AppendUint16(b, 4)
	b = binary.BigEndian.AppendUint32(b, fp)

	return b
}

func fingerPrint(b []byte) uint32 {
	return crc32.ChecksumIEEE(b) ^ 0x5354554e
}

// parseBindingRequest parses a STUN binding request.
func parseBindingRequest(b []byte) (stunTxID, error) {
	if !isSTUN(b) {
		return stunTxID{}, errNotSTUN
	}
	if string(b[:len(bindingRequest)]) != bindingRequest {
		return stunTxID{}, errNotBindingRequest
	}
	var txID stunTxID
	copy(txID[:], b[8:8+len(txID)])
	var lastAttr uint16
	var gotFP uint32
	if err := forEachSTUNAttr(b[headerLen:], func(attrType uint16, a []byte) error {
		lastAttr = attrType
		if attrType == attrNumFingerprint && len(a) == 4 {
			gotFP = binary.BigEndian.Uint32(a)
		}
		return nil
	}); err != nil {
		return stunTxID{}, err
	}
	if lastAttr != attrNumFingerprint {
		return stunTxID{}, errNoFingerprint
	}
	wantFP := fingerPrint(b[:len(b)-lenFingerprint])
	if gotFP != wantFP {
		return stunTxID{}, errWrongFingerprint
	}
	return txID, nil
}

var (
	errNotSTUN            = errors.New("response is not a STUN packet")
	errNotSuccessResponse = errors.New("STUN packet is not a response")
	errMalformedAttrs     = errors.New("STUN response has malformed attributes")
	errNotBindingRequest  = errors.New("STUN request not a binding request")
	errNoFingerprint      = errors.New("STUN request didn't end in fingerprint")
	errWrongFingerprint   = errors.New("STUN request had bogus fingerprint")
)

func forEachSTUNAttr(b []byte, fn func(attrType uint16, a []byte) error) error {
	for len(b) > 0 {
		if len(b) < 4 {
			return errMalformedAttrs
		}
		attrType := binary.BigEndian.Uint16(b[:2])
		attrLen := int(binary.BigEndian.Uint16(b[2:4]))
		attrLenWithPad := (attrLen + 3) &^ 3
		b = b[4:]
		if attrLenWithPad > len(b) {
			return errMalformedAttrs
		}
		if err := fn(attrType, b[:attrLen]); err != nil {
			return err
		}
		b = b[attrLenWithPad:]
	}
	return nil
}

// stunResponse generates a binding response.
func stunResponse(txID stunTxID, addrPort netip.AddrPort) []byte {
	addr := addrPort.Addr()

	var fam byte
	if addr.Is4() {
		fam = 1
	} else if addr.Is6() {
		fam = 2
	} else {
		return nil
	}
	attrsLen := 8 + addr.BitLen()/8
	b := make([]byte, 0, headerLen+attrsLen)

	// Header
	b = append(b, 0x01, 0x01) // success
	b = binary.BigEndian.AppendUint16(b, uint16(attrsLen))
	b = append(b, magicCookie...)
	b = append(b, txID[:]...)

	// Attributes (well, one)
	b = binary.BigEndian.AppendUint16(b, attrXorMappedAddress)
	b = binary.BigEndian.AppendUint16(b, uint16(4+addr.BitLen()/8))
	b = append(b,
		0, // unused byte
		fam)
	b = binary.BigEndian.AppendUint16(b, addrPort.Port()^0x2112) // first half of magicCookie
	ipa := addr.As16()
	for i, o := range ipa[16-addr.BitLen()/8:] {
		if i < 4 {
			b = append(b, o^magicCookie[i])
		} else {
			b = append(b, o^txID[i-len(magicCookie)])
		}
	}
	return b
}

// parseSTUNResponse parses a successful binding response STUN packet.
// The IP address is extracted from the XOR-MAPPED-ADDRESS attribute.
func parseSTUNResponse(b []byte) (tID stunTxID, addr netip.AddrPort, err error) {
	if !isSTUN(b) {
		return tID, netip.AddrPort{}, errNotSTUN
	}
	copy(tID[:], b[8:8+len(tID)])
	if b[0] != 0x01 || b[1] != 0x01 {
		return tID, netip.AddrPort{}, errNotSuccessResponse
	}
	attrsLen := int(binary.BigEndian.Uint16(b[2:4]))
	b = b[headerLen:] // remove STUN header
	if attrsLen > len(b) {
		return tID, netip.AddrPort{}, errMalformedAttrs
	} else if len(b) > attrsLen {
		b = b[:attrsLen] // trim trailing packet bytes
	}

	var fallbackAddr netip.AddrPort

	// Read through the attributes.
	// The addr+port reported by XOR-MAPPED-ADDRESS
	// is the canonical value. If the attribute is not
	// present but the STUN server responds with
	// MAPPED-ADDRESS we fall back to it.
	if err := forEachSTUNAttr(b, func(attrType uint16, attr []byte) error {
		switch attrType {
		case attrXorMappedAddress, attrXorMappedAddressAlt:
			ipSlice, port, err := xorMappedAddress(tID, attr)
			if err != nil {
				return err
			}
			if ip, ok := netip.AddrFromSlice(ipSlice); ok {
				addr = netip.AddrPortFrom(ip.Unmap(), port)
			}
		case attrMappedAddress:
			ipSlice, port, err := mappedAddress(attr)
			if err != nil {
				return errMalformedAttrs
			}
			if ip, ok := netip.AddrFromSlice(ipSlice); ok {
				fallbackAddr = netip.AddrPortFrom(ip.Unmap(), port)
			}
		}
		return nil
	}); err != nil {
		return stunTxID{}, netip.AddrPort{}, err
	}

	if addr.IsValid() {
		return tID, addr, nil
	}
	if fallbackAddr.IsValid() {
		return tID, fallbackAddr, nil
	}
	return tID, netip.AddrPort{}, errMalformedAttrs
}

func xorMappedAddress(tID stunTxID, b []byte) (addr []byte, port uint16, err error) {
	// XOR-MAPPED-ADDRESS attribute, RFC5389 Section 15.2
	if len(b) < 4 {
		return nil, 0, errMalformedAttrs
	}
	xorPort := binary.BigEndian.Uint16(b[2:4])
	addrField := b[4:]
	port = xorPort ^ 0x2112 // first half of magicCookie

	addrLen := familyAddrLen(b[1])
	if addrLen == 0 {
		return nil, 0, errMalformedAttrs
	}
	if len(addrField) < addrLen {
		return nil, 0, errMalformedAttrs
	}
	xorAddr := addrField[:addrLen]
	addr = make([]byte, addrLen)
	for i := range xorAddr {
		if i < len(magicCookie) {
			addr[i] = xorAddr[i] ^ magicCookie[i]
		} else {
			addr[i] = xorAddr[i] ^ tID[i-len(magicCookie)]
		}
	}
	return addr, port, nil
}

func familyAddrLen(fam byte) int {
	switch fam {
	case 0x01: // IPv4
		return net.IPv4len
	case 0x02: // IPv6
		return net.IPv6len
	default:
		return 0
	}
}

func mappedAddress(b []byte) (addr []byte, port uint16, err error) {
	if len(b) < 4 {
		return nil, 0, errMalformedAttrs
	}
	port = uint16(b[2])<<8 | uint16(b[3])
	addrField := b[4:]
	addrLen := familyAddrLen(b[1])
	if addrLen == 0 {
		return nil, 0, errMalformedAttrs
	}
	if len(addrField) < addrLen {
		return nil, 0, errMalformedAttrs
	}
	return bytes.Clone(addrField[:addrLen]), port, nil
}

// Is reports whether b is a STUN message.
func isSTUN(b []byte) bool {
	return len(b) >= headerLen &&
		b[0]&0b11000000 == 0 && // top two bits must be zero
		string(b[4:8]) == magicCookie
}

package protect

import _ "unsafe"

// Protect sends fileDescriptors to protectPath,
// expecting to make fileDescriptors protected by Android VPN service.
func Protect(protectPath string, fileDescriptors []int) error {
	return sendAncillaryFileDescriptors(protectPath, fileDescriptors)
}

//go:linkname sendAncillaryFileDescriptors github.com/sagernet/sing/common/control.sendAncillaryFileDescriptors
func sendAncillaryFileDescriptors(protectPath string, fileDescriptors []int) error

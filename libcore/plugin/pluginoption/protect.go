package pluginoption

type ProtectServiceOptions struct {
	Path string `json:"path"`
	// VerifyPeer rejects requests from processes other than this one and the
	// plugins it launched.
	VerifyPeer bool `json:"verify_peer,omitempty"`
}

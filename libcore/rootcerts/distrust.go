package rootcerts

import (
	"crypto/sha256"
	"encoding/hex"

	"github.com/sagernet/sing/common"
)

// fingerprintSet holds SHA-256 fingerprints of DER certificates.
type fingerprintSet map[[sha256.Size]byte]struct{}

func newFingerprintSet(hexFingerprints ...string) fingerprintSet {
	set := make(fingerprintSet, len(hexFingerprints))
	for _, hexFingerprint := range hexFingerprints {
		decoded := common.Must1(hex.AppendDecode(nil, []byte(hexFingerprint)))
		set[[sha256.Size]byte(decoded)] = struct{}{}
	}
	return set
}

// distrustedRoots are roots left out of every bundle, embedded or system.
//
// 1. https://emudhra.we-have-a-problem.com/
var distrustedRoots = newFingerprintSet(
	// emSign Root TLS CA - G3, the issuer of the compromised TLS intermediates.
	// Only Microsoft's store includes it.
	"7DD78D5F4F13459A83DFF9ABBBA62EDBAF6F2D102BF257FD712F4D9F2746ED8D",
	// emSign Root CS CA - G3, the issuer of the compromised code-signing
	// intermediates. Only Microsoft's store includes it.
	"F2EDAAFF3D9B701FB4D14C9F285F8C0BAD90FC3BCC1FA07D47F4718EDABA34A5",
	// emSign ECC Root CA - G3, cross-signs the two roots above.
	"86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B",
	// emSign ECC Root CA - C3 and emSign Root CA - C1, which Mozilla's
	// 2026-10-20 bundle dropped together with G3.
	"BC4D809B15189D78DB3E1D8CF4F9726A795DA1643CA5F1358E1DDB0EDC0D7EB3",
	"125609AA301DA0A249B97A8239CB6A34216F44DCAC9F3954B14292F2E8C8608F",
	// AAA Certificate Services (Sectigo), cross-signs the compromised
	// intermediates. Mozilla and Chrome had already removed it; Apple and
	// Microsoft still include it.
	"D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4",
)

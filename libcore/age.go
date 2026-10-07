package libcore

import (
	"strings"

	"filippo.io/age"
)

func parseAgeIdentities(text string) ([]age.Identity, error) {
	return age.ParseIdentities(strings.NewReader(text))
}

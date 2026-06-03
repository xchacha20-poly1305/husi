package libcore

import (
	"strings"

	"filippo.io/age"
)

func ValidateAgeIdentities(text string) error {
	_, err := parseAgeIdentities(text)
	return err
}

func parseAgeIdentities(text string) ([]age.Identity, error) {
	return age.ParseIdentities(strings.NewReader(text))
}

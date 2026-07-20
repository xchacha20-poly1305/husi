package libcore

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestOpenConnectAuthChallengeStatusRoundTrip(t *testing.T) {
	status := &OpenConnectEndpointStatus{
		Tag:   "openconnect",
		State: OpenConnectStateAuthPending,
		AuthChallenge: &OpenConnectAuthChallenge{
			ID:      "challenge",
			Banner:  "Banner",
			Message: "Sign in",
			Error:   "",
			Form: &OpenConnectAuthForm{fields: []*OpenConnectAuthFormField{{
				SubmissionKey: "username",
				Name:          "username",
				Label:         "Username",
				Kind:          "text",
				Value:         "alice",
			}}},
			Browser: &OpenConnectBrowserRequest{
				URL:         "https://login.example.com",
				FinalURL:    "https://vpn.example.com/final",
				cookieNames: []string{"webvpn"},
				headerNames: []string{"saml-username"},
			},
		},
	}

	var buffer bytes.Buffer
	require.NoError(t, status.WriteToBinary(&buffer))

	restored, err := readOpenConnectEndpointStatus(&buffer)
	require.NoError(t, err)
	require.Equal(t, status.Tag, restored.Tag)
	require.Equal(t, status.State, restored.State)
	require.NotNil(t, restored.AuthChallenge)
	require.Equal(t, status.AuthChallenge.ID, restored.AuthChallenge.ID)
	require.Equal(t, status.AuthChallenge.Form.fields[0].SubmissionKey, restored.AuthChallenge.Form.fields[0].SubmissionKey)
	require.Equal(t, status.AuthChallenge.Browser.URL, restored.AuthChallenge.Browser.URL)
	require.Equal(t, status.AuthChallenge.Browser.FinalURL, restored.AuthChallenge.Browser.FinalURL)
	require.Equal(t, status.AuthChallenge.Browser.cookieNames, restored.AuthChallenge.Browser.cookieNames)
	require.Equal(t, status.AuthChallenge.Browser.headerNames, restored.AuthChallenge.Browser.headerNames)
}

func TestOpenConnectBrowserResultRoundTrip(t *testing.T) {
	result := NewOpenConnectBrowserResult("https://vpn.example.com/final")
	result.AddCookie("webvpn", "token")
	result.AddHeader("saml-username", "alice")
	result.AddHeader("saml-username", "alice@example.com")

	var buffer bytes.Buffer
	require.NoError(t, result.writeToBinary(&buffer))

	restored, err := readOpenConnectBrowserResult(&buffer)
	require.NoError(t, err)
	require.Equal(t, result.FinalURL, restored.FinalURL)
	require.Equal(t, result.cookies, restored.cookies)
	require.Equal(t, result.headers, restored.headers)
}

package webdav

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/net/webdav"
)

const (
	testUsername = "user"
	testPassword = "secret"
)

func startTestServer(t *testing.T) *httptest.Server {
	t.Helper()
	handler := &webdav.Handler{
		FileSystem: webdav.NewMemFS(),
		LockSystem: webdav.NewMemLS(),
	}
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		username, password, loaded := request.BasicAuth()
		if !loaded || username != testUsername || password != testPassword {
			writer.WriteHeader(http.StatusUnauthorized)
			return
		}
		handler.ServeHTTP(writer, request)
	}))
	t.Cleanup(server.Close)
	return server
}

func testServer(baseURL string) Server {
	return Server{
		URL:      baseURL + "/husi",
		Username: testUsername,
		Password: testPassword,
	}
}

func uploadString(t *testing.T, server Server, name string, content string) error {
	t.Helper()
	return Upload(t.Context(), server, name, strings.NewReader(content), int64(len(content)))
}

func TestRoundTrip(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)
	content := []byte(`{"version":3}`)

	// The directory does not exist yet: Upload has to create it.
	err := Upload(t.Context(), server, "husi_backup_1.json", bytes.NewReader(content), int64(len(content)))
	require.NoError(t, err)
	err = Upload(t.Context(), server, "husi_backup_2.json", bytes.NewReader(content), int64(len(content)))
	require.NoError(t, err)

	entries, err := List(t.Context(), server)
	require.NoError(t, err)
	names := make([]string, 0, len(entries))
	for _, entry := range entries {
		names = append(names, entry.Name)
		assert.Equal(t, int64(len(content)), entry.Size)
		assert.WithinDuration(t, time.Now(), entry.Modified, time.Minute)
	}
	assert.ElementsMatch(t, []string{"husi_backup_1.json", "husi_backup_2.json"}, names)

	var downloaded bytes.Buffer
	err = Download(t.Context(), server, "husi_backup_1.json", &downloaded)
	require.NoError(t, err)
	assert.Equal(t, content, downloaded.Bytes())
}

func TestUploadReplaces(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)

	require.NoError(t, uploadString(t, server, "backup.json", "old"))
	require.NoError(t, uploadString(t, server, "backup.json", "new"))

	var downloaded bytes.Buffer
	require.NoError(t, Download(t.Context(), server, "backup.json", &downloaded))
	assert.Equal(t, "new", downloaded.String())
}

func TestListSkipsSubdirectories(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)
	require.NoError(t, uploadString(t, server, "backup.json", "{}"))
	nested := server
	nested.URL += "/nested"
	require.NoError(t, uploadString(t, nested, "inner.json", "{}"))

	entries, err := List(t.Context(), server)
	require.NoError(t, err)
	require.Len(t, entries, 1)
	assert.Equal(t, "backup.json", entries[0].Name)
}

func TestWrongCredentials(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)
	server.Password = "wrong"

	_, err := List(t.Context(), server)
	assert.ErrorContains(t, err, "401")
	err = uploadString(t, server, "backup.json", "{}")
	assert.ErrorContains(t, err, "401")
}

func TestInvalidName(t *testing.T) {
	server := testServer("http://127.0.0.1:1")
	for _, name := range []string{"", ".", "..", "a/b", `a\b`} {
		err := Download(t.Context(), server, name, &bytes.Buffer{})
		assert.ErrorIs(t, err, ErrInvalidName, name)
		err = Delete(t.Context(), server, name)
		assert.ErrorIs(t, err, ErrInvalidName, name)
	}
}

func TestUploadEmpty(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)

	require.NoError(t, uploadString(t, server, "empty.json", ""))
	var downloaded bytes.Buffer
	require.NoError(t, Download(t.Context(), server, "empty.json", &downloaded))
	assert.Empty(t, downloaded.Bytes())
}

func TestUploadSizeMismatch(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)

	err := Upload(t.Context(), server, "backup.json", strings.NewReader("{}"), 3)
	assert.Error(t, err)
}

func TestDelete(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)
	require.NoError(t, uploadString(t, server, "kept.json", "{}"))
	require.NoError(t, uploadString(t, server, "deleted.json", "{}"))

	require.NoError(t, Delete(t.Context(), server, "deleted.json"))

	entries, err := List(t.Context(), server)
	require.NoError(t, err)
	require.Len(t, entries, 1)
	assert.Equal(t, "kept.json", entries[0].Name)
}

func TestDeleteMissing(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)
	require.NoError(t, uploadString(t, server, "kept.json", "{}"))

	err := Delete(t.Context(), server, "missing.json")
	assert.ErrorContains(t, err, "404")
}

func TestDownloadMissing(t *testing.T) {
	httpServer := startTestServer(t)
	server := testServer(httpServer.URL)

	err := Download(t.Context(), server, "missing.json", &bytes.Buffer{})
	assert.ErrorContains(t, err, "404")
}

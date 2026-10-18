package webdav

import (
	"context"
	"encoding/xml"
	"io"
	"net/http"
	"net/url"
	"path"
	"strings"
	"time"

	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/httpfetch"
)

const (
	methodPropfind = "PROPFIND"
	methodMkcol    = "MKCOL"
)

const propfindBody = `<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
  </d:prop>
</d:propfind>`

var ErrInvalidName = E.New("invalid file name")

type Server struct {
	// URL of the directory that holds the files.
	URL      string
	Username string
	Password string
	httpfetch.Transport
}

type Entry struct {
	Name     string
	Size     int64
	Modified time.Time
}

func List(ctx context.Context, server Server) ([]Entry, error) {
	sess, err := newSession(server)
	if err != nil {
		return nil, err
	}
	request, err := sess.newRequest(ctx, methodPropfind, sess.directory, strings.NewReader(propfindBody))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Depth", "1")
	request.Header.Set("Content-Type", "application/xml; charset=utf-8")
	response, err := sess.client.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusMultiStatus {
		return nil, httpfetch.StatusError(response)
	}
	var status multistatus
	err = xml.NewDecoder(response.Body).Decode(&status)
	if err != nil {
		return nil, E.Cause(err, "decode PROPFIND response")
	}
	return status.entries(), nil
}

// Upload stores size bytes of content as name in the directory, replacing a
// file of the same name. A missing directory is created, but not its parents.
// It is created before the PUT, so content is read once and need not seek.
func Upload(ctx context.Context, server Server, name string, content io.Reader, size int64) error {
	sess, err := newSession(server)
	if err != nil {
		return err
	}
	file, err := sess.file(name)
	if err != nil {
		return err
	}
	err = sess.ensureDirectory(ctx)
	if err != nil {
		return err
	}
	return sess.put(ctx, file, content, size)
}

func Download(ctx context.Context, server Server, name string, target io.Writer) error {
	sess, err := newSession(server)
	if err != nil {
		return err
	}
	file, err := sess.file(name)
	if err != nil {
		return err
	}
	request, err := sess.newRequest(ctx, http.MethodGet, file, nil)
	if err != nil {
		return err
	}
	response, err := sess.client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return httpfetch.StatusError(response)
	}
	_, err = bufio.Copy(target, response.Body)
	return err
}

func Delete(ctx context.Context, server Server, name string) error {
	sess, err := newSession(server)
	if err != nil {
		return err
	}
	file, err := sess.file(name)
	if err != nil {
		return err
	}
	request, err := sess.newRequest(ctx, http.MethodDelete, file, nil)
	if err != nil {
		return err
	}
	response, err := sess.client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	switch response.StatusCode {
	case http.StatusOK, http.StatusAccepted, http.StatusNoContent:
		return nil
	default:
		return httpfetch.StatusError(response)
	}
}

type session struct {
	server    Server
	client    *http.Client
	directory *url.URL
}

func newSession(server Server) (*session, error) {
	link, err := url.Parse(server.URL)
	if err != nil {
		return nil, err
	}
	if link.Scheme != "http" && link.Scheme != "https" {
		return nil, E.New("unsupported WebDAV URL scheme: ", link.Scheme)
	}
	// Credentials travel as an Authorization header, never in the URL.
	link.User = nil
	if !strings.HasSuffix(link.Path, "/") {
		link = link.JoinPath("/")
	}
	return &session{
		server:    server,
		client:    httpfetch.NewClient(server.Transport, link.Hostname()),
		directory: link,
	}, nil
}

func (s *session) file(name string) (*url.URL, error) {
	if name == "" || name == "." || name == ".." || strings.ContainsAny(name, `/\`) {
		return nil, E.Extend(ErrInvalidName, name)
	}
	return s.directory.JoinPath(name), nil
}

func (s *session) newRequest(ctx context.Context, method string, link *url.URL, body io.Reader) (*http.Request, error) {
	request, err := http.NewRequestWithContext(ctx, method, link.String(), body)
	if err != nil {
		return nil, err
	}
	if s.server.Username != "" || s.server.Password != "" {
		request.SetBasicAuth(s.server.Username, s.server.Password)
	}
	return request, nil
}

func (s *session) put(ctx context.Context, file *url.URL, content io.Reader, size int64) error {
	if size == 0 {
		content = http.NoBody
	}
	request, err := s.newRequest(ctx, http.MethodPut, file, content)
	if err != nil {
		return err
	}
	request.ContentLength = size
	response, err := s.client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	switch response.StatusCode {
	case http.StatusOK, http.StatusCreated, http.StatusNoContent:
		return nil
	default:
		return httpfetch.StatusError(response)
	}
}

func (s *session) ensureDirectory(ctx context.Context) error {
	request, err := s.newRequest(ctx, methodPropfind, s.directory, strings.NewReader(propfindBody))
	if err != nil {
		return err
	}
	request.Header.Set("Depth", "0")
	request.Header.Set("Content-Type", "application/xml; charset=utf-8")
	response, err := s.client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	switch response.StatusCode {
	case http.StatusMultiStatus:
		return nil
	case http.StatusNotFound:
		return s.makeDirectory(ctx)
	default:
		return E.Cause(httpfetch.StatusError(response), "find directory")
	}
}

func (s *session) makeDirectory(ctx context.Context) error {
	request, err := s.newRequest(ctx, methodMkcol, s.directory, nil)
	if err != nil {
		return err
	}
	response, err := s.client.Do(request)
	if err != nil {
		return err
	}
	// 405 Method Not Allowed: the directory exists already.
	if response.StatusCode != http.StatusCreated && response.StatusCode != http.StatusMethodNotAllowed {
		_ = response.Body.Close()
		return E.Cause(httpfetch.StatusError(response), "create directory")
	}
	return response.Body.Close()
}

type multistatus struct {
	Responses []propfindResponse `xml:"DAV: response"`
}

type propfindResponse struct {
	Href      string     `xml:"DAV: href"`
	Propstats []propstat `xml:"DAV: propstat"`
}

type propstat struct {
	Status string `xml:"DAV: status"`
	Prop   struct {
		ResourceType struct {
			Collection *struct{} `xml:"DAV: collection"`
		} `xml:"DAV: resourcetype"`
		ContentLength int64  `xml:"DAV: getcontentlength"`
		LastModified  string `xml:"DAV: getlastmodified"`
	} `xml:"DAV: prop"`
}

func (m *multistatus) entries() []Entry {
	entries := make([]Entry, 0, len(m.Responses))
	for _, response := range m.Responses {
		entry, isFile := response.entry()
		if isFile {
			entries = append(entries, entry)
		}
	}
	return entries
}

// entry reports false for collections, which include the listed directory itself.
func (r *propfindResponse) entry() (Entry, bool) {
	link, err := url.Parse(r.Href)
	if err != nil {
		return Entry{}, false
	}
	name := path.Base(link.Path)
	if name == "/" || name == "." {
		return Entry{}, false
	}
	entry := Entry{Name: name}
	for _, stat := range r.Propstats {
		// Each propstat carries the properties that share its status; only
		// the found ones (200) have values.
		if !strings.Contains(stat.Status, " 200 ") {
			continue
		}
		prop := stat.Prop
		if prop.ResourceType.Collection != nil {
			return Entry{}, false
		}
		entry.Size = prop.ContentLength
		if prop.LastModified != "" {
			entry.Modified, _ = http.ParseTime(prop.LastModified)
		}
	}
	return entry, true
}

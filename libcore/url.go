package libcore

import (
	"net"
	"net/url"
	"strings"
	_ "unsafe"

	E "github.com/sagernet/sing/common/exceptions"
)

type URL interface {
	GetScheme() string
	SetScheme(scheme string)
	GetOpaque() string
	SetOpaque(opaque string)
	GetUsername() string
	SetUsername(username string)
	GetPassword() string
	SetPassword(password string) error
	GetHost() string
	SetHost(host string)
	GetFullHost() string
	SetFullHost(host string)
	GetPorts() string
	SetPorts(port string)
	GetPath() string
	SetPath(path string)
	GetRawPath() string
	SetRawPath(rawPath string) error
	QueryParameterNotBlank(key string) string
	AddQueryParameter(key, value string)
	GetFragment() string
	SetRawFragment(rawFragment string) error
	GetString() string
}

var _ URL = (*netURL)(nil)

type netURL struct {
	url.URL
	url.Values
}

func NewURL(scheme string) URL {
	u := new(netURL)
	u.Scheme = scheme
	u.Values = make(url.Values)
	return u
}

//go:linkname getScheme net/url.getScheme
func getScheme(rawURL string) (scheme, path string, err error)

//go:linkname setFragment net/url.(*URL).setFragment
func setFragment(u *url.URL, fragment string) error

//go:linkname setPath net/url.(*URL).setPath
func setPath(u *url.URL, fragment string) error

// parse parses a URL from a string in one of two contexts. If
// viaRequest is true, the URL is assumed to have arrived via an HTTP request,
// in which case only absolute URLs or path-absolute relative URLs are allowed.
// If viaRequest is false, all forms of relative URLs are allowed.
func parse(rawURL string) (*url.URL, error) {
	var rest string
	var err error

	newUrl := new(url.URL)

	if rawURL == "*" {
		newUrl.Path = "*"
		return newUrl, nil
	}

	// Split off possible leading "http:", "mailto:", etc.
	// Cannot contain escaped characters.
	if newUrl.Scheme, rest, err = getScheme(rawURL); err != nil {
		return nil, err
	}
	newUrl.Scheme = strings.ToLower(newUrl.Scheme)

	if strings.HasSuffix(rest, "?") && strings.Count(rest, "?") == 1 {
		newUrl.ForceQuery = true
		rest = rest[:len(rest)-1]
	} else {
		rest, newUrl.RawQuery, _ = strings.Cut(rest, "?")
	}

	if !strings.HasPrefix(rest, "/") {
		if newUrl.Scheme != "" {
			// We consider rootless paths per RFC 3986 as opaque.
			newUrl.Opaque = rest
			return newUrl, nil
		}

		// Avoid confusion with malformed schemes, like cache_object:foo/bar.
		// See golang.org/issue/16822.
		//
		// RFC 3986, §3.3:
		// In addition, a URI reference (Section 4.1) may be a relative-path reference,
		// in which case the first path segment cannot contain a colon (":") character.
		if segment, _, _ := strings.Cut(rest, "/"); strings.Contains(segment, ":") {
			// First path segment has colon. Not allowed in relative URL.
			return nil, E.New("first path segment in URL cannot contain colon")
		}
	}

	if (newUrl.Scheme != "" || !strings.HasPrefix(rest, "///")) && strings.HasPrefix(rest, "//") {
		var authority string
		authority, rest = rest[2:], ""
		if i := strings.Index(authority, "/"); i >= 0 {
			authority, rest = authority[:i], authority[i:]
		}
		newUrl.User, newUrl.Host, err = parseAuthority(authority)
		if err != nil {
			return nil, err
		}
	}
	// Set Path and, optionally, RawPath.
	// RawPath is a hint of the encoding of Path. We don't want to set it if
	// the default escaping of Path is equivalent, to help make sure that people
	// don't rely on it in general.
	if err := setPath(newUrl, rest); err != nil {
		return nil, err
	}
	return newUrl, nil
}

func ParseURL(rawURL string) (URL, error) {
	u := &netURL{}
	ru, frag, _ := strings.Cut(rawURL, "#")
	uu, err := parse(ru)
	if err != nil {
		return nil, E.New("failed to parse url: ", rawURL)
	}
	u.URL = *uu
	u.Values = u.Query()
	if u.Values == nil {
		u.Values = make(url.Values)
	}
	if frag == "" {
		return u, nil
	}
	if err = u.SetRawFragment(frag); err != nil {
		return nil, err
	}
	return u, nil
}

func (u *netURL) GetScheme() string {
	return u.Scheme
}

func (u *netURL) SetScheme(scheme string) {
	u.Scheme = scheme
}

func (u *netURL) GetOpaque() string {
	return u.Opaque
}

func (u *netURL) SetOpaque(opaque string) {
	u.Opaque = opaque
}

func (u *netURL) GetUsername() string {
	if u.User != nil {
		return u.User.Username()
	}
	return ""
}

func (u *netURL) SetUsername(username string) {
	if u.User != nil {
		if password, ok := u.User.Password(); !ok {
			u.User = url.User(username)
		} else {
			u.User = url.UserPassword(username, password)
		}
	} else {
		u.User = url.User(username)
	}
}

func (u *netURL) GetPassword() string {
	if u.User != nil {
		if password, ok := u.User.Password(); ok {
			return password
		}
	}
	return ""
}

func (u *netURL) SetPassword(password string) error {
	if u.User == nil {
		return E.New("set username first")
	}
	u.User = url.UserPassword(u.User.Username(), password)
	return nil
}

func (u *netURL) GetHost() string {
	host, _, err := net.SplitHostPort(u.Host)
	if err != nil {
		return u.Host
	}
	return host
}

func (u *netURL) SetHost(host string) {
	_, port, err := net.SplitHostPort(u.Host)
	if err != nil {
		u.Host = host
	}

	u.Host = net.JoinHostPort(host, port)
}

func (u *netURL) GetFullHost() string {
	return u.Host
}

func (u *netURL) SetFullHost(host string) {
	u.Host = host
}

func (u *netURL) GetPorts() string {
	_, port, _ := net.SplitHostPort(u.Host)
	return port
}

func (u *netURL) SetPorts(port string) {
	host, _, err := net.SplitHostPort(u.Host)
	if err != nil {
		u.Host = net.JoinHostPort(u.Host, port)
	}

	u.Host = net.JoinHostPort(host, port)
}

func (u *netURL) GetPath() string {
	return u.Path
}

func (u *netURL) SetPath(path string) {
	u.Path = path
	u.RawPath = ""
}

func (u *netURL) GetRawPath() string {
	return u.RawPath
}

func (u *netURL) SetRawPath(rawPath string) error {
	return setPath(&u.URL, rawPath)
}

func (u *netURL) QueryParameterNotBlank(key string) string {
	return u.Get(key)
}

func (u *netURL) AddQueryParameter(key, value string) {
	u.Add(key, value)
}

func (u *netURL) GetFragment() string {
	return u.Fragment
}

func (u *netURL) SetRawFragment(rawFragment string) error {
	return setFragment(&u.URL, rawFragment)
}

func (u *netURL) GetString() string {
	u.RawQuery = u.Encode()
	return u.String()
}

// https://github.com/apernet/hysteria/blob/d9346f6c2482d34d876c55ff7228ea26445ee2a2/app/internal/url/url.go

// validOptionalPort reports whether port is either an empty string
// or matches /^:\d*$/
func validOptionalPort(port string) bool {
	if port == "" {
		return true
	}
	if port[0] != ':' {
		return false
	}
	for _, b := range port[1:] {
		if (b < '0' || b > '9') && (b != '-' && b != ',') {
			// Neither a digit nor a valid separator character.
			return false
		}
	}
	return true
}

//go:linkname unescape net/url.unescape
func unescape(s string, mode int) (string, error)

// parseHost parses host as an authority without user
// information. That is, as host[:port].
func parseHost(host string) (string, error) {
	if strings.HasPrefix(host, "[") {
		// Parse an IP-Literal in RFC 3986 and RFC 6874.
		// E.g., "[fe80::1]", "[fe80::1%25en0]", "[fe80::1]:80".
		i := strings.LastIndex(host, "]")
		if i < 0 {
			return "", E.New("missing ']' in host")
		}
		colonPort := host[i+1:]
		if !validOptionalPort(colonPort) {
			return "", E.New("invalid port ", colonPort, " after host")
		}

		// RFC 6874 defines that %25 (%-encoded percent) introduces
		// the zone identifier, and the zone identifier can use basically
		// any %-encoding it likes. That's different from the host, which
		// can only %-encode non-ASCII bytes.
		// We do impose some restrictions on the zone, to avoid stupidity
		// like newlines.
		zone := strings.Index(host[:i], "%25")
		if zone >= 0 {
			host1, err := unescape(host[:zone], 3) // encodeHost
			if err != nil {
				return "", err
			}
			host2, err := unescape(host[zone:i], 4) // encodeZone
			if err != nil {
				return "", err
			}
			host3, err := unescape(host[i:], 3) // encodeHost
			if err != nil {
				return "", err
			}
			return host1 + host2 + host3, nil
		}
	} else if i := strings.LastIndex(host, ":"); i != -1 {
		colonPort := host[i:]
		if !validOptionalPort(colonPort) {
			return "", E.New("invalid port ", colonPort, " after host")
		}
	}

	var err error
	if host, err = unescape(host, 3); /*encodeHost*/ err != nil {
		return "", err
	}
	return host, nil
}

//go:linkname validUserinfo net/url.validUserinfo
func validUserinfo(s string) bool

func parseAuthority(authority string) (user *url.Userinfo, host string, err error) {
	i := strings.LastIndex(authority, "@")
	if i < 0 {
		host, err = parseHost(authority)
	} else {
		host, err = parseHost(authority[i+1:])
	}
	if err != nil {
		return nil, "", err
	}
	if i < 0 {
		return nil, host, nil
	}
	userinfo := authority[:i]
	if !validUserinfo(userinfo) {
		return nil, "", E.New("net/url: invalid userinfo")
	}
	if !strings.Contains(userinfo, ":") {
		if userinfo, err = unescape(userinfo, 5); /*encodeUserPassword*/ err != nil {
			return nil, "", err
		}
		user = url.User(userinfo)
	} else {
		username, password, _ := strings.Cut(userinfo, ":")
		if username, err = unescape(username, 5); /*encodeUserPassword*/ err != nil {
			return nil, "", err
		}
		if password, err = unescape(password, 5); /*encodeUserPassword*/ err != nil {
			return nil, "", err
		}
		user = url.UserPassword(username, password)
	}
	return user, host, nil
}

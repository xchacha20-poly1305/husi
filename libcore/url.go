package libcore

import (
	"net"
	"net/url"
	"strings"
	_ "unsafe"

	"github.com/sagernet/sing/common"
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
	SetQueryParameter(key, value string)

	GetFragment() string
	SetFragment(fragment string)

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

//go:linkname setPath net/url.(*URL).setPath
func setPath(u *url.URL, fragment string) error

func ParseURL(rawURL string) (URL, error) {
	u := &netURL{}
	uu, err := url.Parse(rawURL)
	if err != nil {
		// For Hysteria port hop non-standard format
		errStr := err.Error()
		if !strings.Contains(errStr, "invalid port") {
			return nil, E.Cause(err, "pause rawURL")
		}
		multiplePort := common.SubstringBetween(errStr, "invalid port \"", "\" after host")
		noPort := strings.Replace(rawURL, multiplePort, "", 1)
		uu, err = url.Parse(noPort)
		if err != nil {
			return nil, E.Cause(err, "parse rawURL with invalid port")
		}
		uu.Host = net.JoinHostPort(uu.Host, strings.TrimPrefix(multiplePort, ":"))
	}
	u.URL = *uu
	u.Values = u.Query()
	if u.Values == nil {
		u.Values = make(url.Values)
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

func (u *netURL) SetQueryParameter(key, value string) {
	u.Set(key, value)
}

func (u *netURL) GetFragment() string {
	return u.Fragment
}

func (u *netURL) SetFragment(fragment string) {
	u.Fragment = fragment
	u.RawFragment = ""
}

func (u *netURL) GetString() string {
	u.RawQuery = u.Encode()
	return u.String()
}

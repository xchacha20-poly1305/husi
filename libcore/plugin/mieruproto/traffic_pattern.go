// Package mieruproto implements minimum protobuf encoding/decoding for Mieru traffic pattern.
package mieruproto

//go:generate protoc --go_out=. --go_opt=paths=source_relative --proto_path=. traffic_pattern.proto

import (
	"encoding/base64"
	"encoding/json"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
)

func DecodeBase64JSON(encoded string) ([]byte, error) {
	raw, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return nil, E.Cause(err, "decode base64")
	}
	pattern, err := Decode(raw)
	if err != nil {
		return nil, err
	}
	formatted, err := protojson.MarshalOptions{
		UseProtoNames: true,
		Multiline:     true,
		Indent:        strings.Repeat(" ", 2),
	}.Marshal(pattern)
	if err != nil {
		return nil, E.Cause(err, "marshal traffic pattern json")
	}
	return formatted, nil
}

func EncodeJSONBase64(jsonText string) (string, error) {
	pattern, err := decodeJSON(jsonText)
	if err != nil {
		return "", err
	}
	raw, err := proto.Marshal(pattern)
	if err != nil {
		return "", E.Cause(err, "marshal traffic pattern protobuf")
	}
	return base64.StdEncoding.EncodeToString(raw), nil
}

func Decode(raw []byte) (*TrafficPattern, error) {
	pattern := &TrafficPattern{}
	err := proto.Unmarshal(raw, pattern)
	if err != nil {
		return nil, E.Cause(err, "unmarshal traffic pattern protobuf")
	}
	return pattern, nil
}

// decodeJSON adapt wrapped traffic pattern.
func decodeJSON(jsonText string) (*TrafficPattern, error) {
	var root map[string]json.RawMessage
	err := json.Unmarshal([]byte(jsonText), &root)
	if err != nil {
		return nil, E.Cause(err, "unmarshal traffic pattern json")
	}

	payload := []byte(jsonText)
	if nested, loaded := root["trafficPattern"]; loaded {
		payload = nested
	}

	pattern := &TrafficPattern{}
	err = protojson.Unmarshal(payload, pattern)
	if err != nil {
		return nil, E.Cause(err, "unmarshal traffic pattern payload")
	}
	return pattern, nil
}

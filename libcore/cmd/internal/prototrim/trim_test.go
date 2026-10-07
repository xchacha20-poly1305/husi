package main

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

const sampleProto = `syntax = "proto3";

package daemon;
option go_package = "github.com/sagernet/sing-box/daemon";

import "google/protobuf/empty.proto";

service StartedService {
  rpc SubscribeLog(google.protobuf.Empty) returns(stream Log) {}

  rpc SubscribeGroups(google.protobuf.Empty) returns(stream Groups) {}
  rpc SetClashMode(ClashMode) returns(google.protobuf.Empty) {}

  rpc StartTailscalePing(TailscalePingRequest) returns (stream TailscalePingResponse) {}
}

enum LogLevel {
  PANIC = 0;
}

message Log {
  repeated Message messages = 1;
  message Message {
    LogLevel level = 1;
  }
}

// Groups are what the dashboard draws.
message Groups {
  repeated Group group = 1;
}

message Group {
  string tag = 1;
}

message ClashMode {
  string mode = 3;
}

message TailscalePingRequest {
  string endpointTag = 1;
}

message TailscalePingResponse {
  double latencyMs = 1;
}

message Done {}
`

func trimSample(t *testing.T, wantedRPCs ...string) string {
	t.Helper()
	trimmed, err := trim(sampleProto, "StartedServiceProto", wantedRPCs)
	require.NoError(t, err)
	return trimmed
}

func TestTrimKeepsOnlyReachableTypes(t *testing.T) {
	trimmed := trimSample(t, "SubscribeLog", "SubscribeGroups", "SetClashMode")

	for _, kept := range []string{
		"message Log {",
		"enum LogLevel {",  // Referenced by a nested message.
		"message Groups {", // Referenced by an rpc.
		"message Group {",  // Referenced by a kept message.
		"message ClashMode {",
		"// Groups are what the dashboard draws.", // Comments follow their block.
	} {
		assert.Contains(t, trimmed, kept)
	}

	for _, dropped := range []string{
		"rpc StartTailscalePing",
		"message TailscalePingRequest {",
		"message TailscalePingResponse {",
		"message Done {}", // Reachable from nothing, single line block or not.
	} {
		assert.NotContains(t, trimmed, dropped)
	}
}

func TestTrimKeepsPreambleAndInjectsJavaOptions(t *testing.T) {
	trimmed := trimSample(t, "SubscribeLog")

	assert.Contains(t, trimmed, "package daemon;\n"+
		`option go_package = "github.com/sagernet/sing-box/daemon";`+"\n"+
		"option java_multiple_files = true;\n"+
		`option java_outer_classname = "StartedServiceProto";`+"\n"+
		`option java_package = "fr.husi.proto.daemon";`+"\n")
	assert.Contains(t, trimmed, `import "google/protobuf/empty.proto";`)
}

func TestTrimKeepsRpcGroupingWithoutStrayBlankLines(t *testing.T) {
	trimmed := trimSample(t, "SubscribeLog", "SetClashMode")

	assert.Contains(t, trimmed, strings.Join([]string{
		"service StartedService {",
		"  rpc SubscribeLog(google.protobuf.Empty) returns(stream Log) {}",
		"",
		"  rpc SetClashMode(ClashMode) returns(google.protobuf.Empty) {}",
		"}",
	}, "\n"))
	assert.NotContains(t, trimmed, "\n\n}")
	assert.NotContains(t, trimmed, "{\n\n")
	assert.True(t, strings.HasSuffix(trimmed, "}\n"), "output must end with a single newline")
}

func TestTrimReportsRpcsMissingUpstream(t *testing.T) {
	_, err := trim(sampleProto, "StartedServiceProto", []string{"SubscribeLog", "Renamed", "Gone"})

	require.Error(t, err)
	assert.EqualError(t, err, "wanted rpc not found upstream: Gone Renamed")
}

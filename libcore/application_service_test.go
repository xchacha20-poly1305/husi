package libcore

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func TestNoPluginLauncherRunsWithoutSpecs(t *testing.T) {
	const latency = int32(42)
	got, err := noPluginLauncher{}.RunWithPlugins(nil, func() (int32, error) {
		return latency, nil
	})
	require.NoError(t, err)
	assert.Equal(t, latency, got)
}

func TestNoPluginLauncherRejectsSpecs(t *testing.T) {
	ran := false
	_, err := noPluginLauncher{}.RunWithPlugins([]*husiv1.PluginProcessSpec{{Name: "naive"}}, func() (int32, error) {
		ran = true
		return 0, nil
	})
	require.Error(t, err, "a host that cannot spawn plugins must say so")
	assert.False(t, ran, "the test must not run against an outbound whose plugin never started")
}

func TestStandaloneURLTestWithoutPluginSupport(t *testing.T) {
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	_, err := service.StandaloneURLTest(t.Context(), &husiv1.StandaloneURLTestRequest{
		Config:  "{}",
		Plugins: []*husiv1.PluginProcessSpec{{Name: "naive"}},
	})
	require.Error(t, err)
	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.Internal, st.Code())
}

func TestGenerateSchemaUnknownKind(t *testing.T) {
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	_, err := service.GenerateSchema(t.Context(), &husiv1.GenerateSchemaRequest{
		Kind: husiv1.SchemaKind_SCHEMA_KIND_UNSPECIFIED,
	})
	require.Error(t, err)
	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.InvalidArgument, st.Code())
}

package coresvc

import (
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
)

type ServiceRegistrar interface {
	RegisterServices(server *grpc.Server, healthServer *health.Server)
}

func ServingStatus(healthServer *health.Server, serviceName string) {
	healthServer.SetServingStatus(serviceName, grpc_health_v1.HealthCheckResponse_SERVING)
}

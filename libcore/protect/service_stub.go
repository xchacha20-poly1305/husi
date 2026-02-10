//go:build !unix

package protect

import (
	"context"
	"os"

	"github.com/sagernet/sing/common/logger"
)

type Service struct {
}

func New(ctx context.Context, ctxLogger logger.ContextLogger, path string, do func(fd int) error) (*Service, error) {
	return nil, os.ErrInvalid
}

func (p *Service) Start() error {
	return os.ErrInvalid
}

func (p *Service) Close() error {
	return os.ErrInvalid
}

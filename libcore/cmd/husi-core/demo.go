//go:build !android

package main

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"github.com/sagernet/sing-box/log"

	"github.com/spf13/cobra"
	"github.com/xchacha20-poly1305/husi/libcore/v2/demoapi"
)

func newDemoCommand() *cobra.Command {
	var options demoapi.Options
	command := &cobra.Command{
		Use:   "demo",
		Short: "Run an in-memory API demo for remote dashboards",
		Long:  "Run a simulated API with live traffic, connections, proxy groups and logs.\nOperations only change demo state; restarting restores the sample data.",
		Args:  cobra.NoArgs,
		RunE: func(command *cobra.Command, _ []string) error {
			ctx, stop := signal.NotifyContext(command.Context(), os.Interrupt, syscall.SIGTERM)
			defer stop()
			server, err := demoapi.Start(ctx, log.StdLogger(), options)
			if err != nil {
				return err
			}
			defer server.Close()
			fmt.Fprintf(command.OutOrStdout(), "Demo API: http://%s\nSimulated data only; changes reset on restart. Press Ctrl+C to stop.\n", server.Addr())
			<-ctx.Done()
			return nil
		},
	}
	command.Flags().StringVar(&options.Listen, "listen", demoapi.DefaultListenAddress, "TCP listen address")
	command.Flags().StringVar(&options.Secret, "secret", "", "Bearer secret (optional)")
	return command
}

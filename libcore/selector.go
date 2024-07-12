package libcore

import (
	"context"
	"time"

	"github.com/sagernet/sing-box/log"
)

type selectorCallback func(tag string)

// listenSelectorChange check the change of the main selector once
// in a while and use callback.
// It will block the thread, so run it in a new goroutine.
func (b *BoxInstance) listenSelectorChange(ctx context.Context, callback selectorCallback) {
	if b.selector == nil || callback == nil {
		return
	}

	defer catchPanic("listenSelectorChange", func(panicErr error) { log.Error(panicErr) })

	const (
		minimumDuration = 300 * time.Millisecond
		maximumDuration = 2 * time.Second
	)

	duration := minimumDuration
	ticker := time.NewTicker(minimumDuration)
	defer ticker.Stop()

	// updateTicker adds duration little by little.
	updateTicker := func(changed bool) {
		if changed {
			duration = minimumDuration
		} else {
			if duration < maximumDuration {
				duration = duration * 2
			}
		}
		ticker.Reset(duration)
	}

	oldTag := b.selector.Now()
	log.Trace("Started selector change listener")

	for {
		select {
		case <-ctx.Done():
			log.Trace("Selector change listener close by context: ", ctx.Err())
			return
		case <-ticker.C:
			if b.state.Load() == boxStateClosed {
				log.Trace("Selector change listener close because of box close.")
				return
			}
		}

		newTag := b.selector.Now()
		changed := oldTag != newTag
		if changed {
			callback(newTag)
			oldTag = newTag
		}
		updateTicker(changed)
	}
}

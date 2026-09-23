package finalmask

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

type Range struct {
	From int64
	To   int64
}

func ParseRange(s string) (Range, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return Range{0, 0}, nil
	}
	parts := strings.Split(s, "-")
	if len(parts) == 1 {
		v, err := strconv.ParseInt(strings.TrimSpace(parts[0]), 10, 64)
		if err != nil {
			return Range{}, err
		}
		return Range{From: v, To: v}, nil
	}
	from, err := strconv.ParseInt(strings.TrimSpace(parts[0]), 10, 64)
	if err != nil {
		return Range{}, err
	}
	to, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
	if err != nil {
		return Range{}, err
	}
	if from > to {
		from, to = to, from
	}
	return Range{From: from, To: to}, nil
}

func (r *Range) UnmarshalJSON(data []byte) error {
	var num int64
	if err := json.Unmarshal(data, &num); err == nil {
		r.From = num
		r.To = num
		return nil
	}
	var str string
	if err := json.Unmarshal(data, &str); err != nil {
		return err
	}
	parsed, err := ParseRange(str)
	if err != nil {
		return err
	}
	*r = parsed
	return nil
}

type FragmentConfig struct {
	PacketsFrom int64
	PacketsTo   int64
	LengthsMin  []int64
	LengthsMax  []int64
	DelaysMin   []int64
	DelaysMax   []int64
	MaxSplitMin int64
	MaxSplitMax int64
}

type FragmentMaskSettings struct {
	Packets  string  `json:"packets"`
	Length   Range   `json:"length"`
	Delay    Range   `json:"delay"`
	Lengths  []Range `json:"lengths"`
	Delays   []Range `json:"delays"`
	MaxSplit Range   `json:"maxSplit"`
}

func (s *FragmentMaskSettings) BuildConfig() (*FragmentConfig, error) {
	c := &FragmentConfig{}

	switch strings.ToLower(strings.TrimSpace(s.Packets)) {
	case "tlshello":
		c.PacketsFrom = 0
		c.PacketsTo = 1
	case "":
		c.PacketsFrom = 0
		c.PacketsTo = 0
	default:
		r, err := ParseRange(s.Packets)
		if err != nil {
			return nil, fmt.Errorf("invalid packets range '%s': %w", s.Packets, err)
		}
		c.PacketsFrom = r.From
		c.PacketsTo = r.To
		if c.PacketsFrom == 0 && c.PacketsTo == 0 {
			return nil, fmt.Errorf("packets range cannot be 0")
		}
	}

	if len(s.Lengths) > 0 {
		for _, r := range s.Lengths {
			c.LengthsMin = append(c.LengthsMin, r.From)
			c.LengthsMax = append(c.LengthsMax, r.To)
		}
	} else if s.Length.From > 0 || s.Length.To > 0 {
		c.LengthsMin = append(c.LengthsMin, s.Length.From)
		c.LengthsMax = append(c.LengthsMax, s.Length.To)
	} else {
		// default fallback length
		c.LengthsMin = append(c.LengthsMin, 100)
		c.LengthsMax = append(c.LengthsMax, 200)
	}

	if len(s.Delays) > 0 {
		for _, r := range s.Delays {
			c.DelaysMin = append(c.DelaysMin, r.From)
			c.DelaysMax = append(c.DelaysMax, r.To)
		}
	} else {
		c.DelaysMin = append(c.DelaysMin, s.Delay.From)
		c.DelaysMax = append(c.DelaysMax, s.Delay.To)
	}

	c.MaxSplitMin = s.MaxSplit.From
	c.MaxSplitMax = s.MaxSplit.To

	return c, nil
}

type TCPMaskItem struct {
	Type     string               `json:"type"`
	Settings FragmentMaskSettings `json:"settings"`
}

type FinalmaskConfig struct {
	TCP []TCPMaskItem `json:"tcp"`
}

func ParseFinalmaskJSON(raw []byte) (*FinalmaskConfig, error) {
	if len(raw) == 0 {
		return nil, nil
	}
	var cfg FinalmaskConfig
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return nil, err
	}
	return &cfg, nil
}

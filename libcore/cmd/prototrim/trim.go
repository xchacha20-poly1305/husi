package main

import (
	"fmt"
	"regexp"
	"slices"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
)

const (
	serviceKind     = "service"
	javaPackageName = "fr.husi.proto.daemon"
	lineSeparator   = "\n"
)

var (
	blockStartPattern = regexp.MustCompile(`^(message|enum|service)\s+(\w+)`)
	rpcStartPattern   = regexp.MustCompile(`^\s*rpc\s+(\w+)`)
	identifierPattern = regexp.MustCompile(`[A-Za-z_][A-Za-z0-9_.]*`)
)

type protoBlock struct {
	kind  string
	name  string
	lines []string
}

func (p *protoBlock) text() string {
	return strings.Join(p.lines, lineSeparator)
}

func braceDepthDelta(line string) int {
	return strings.Count(line, "{") - strings.Count(line, "}")
}

func parseSource(lines []string) (preamble []string, blocks []*protoBlock) {
	var (
		pendingComments []string
		current         *protoBlock
		depth           int
	)

	for _, line := range lines {
		if current != nil {
			current.lines = append(current.lines, line)
			depth += braceDepthDelta(line)
			if depth == 0 {
				blocks = append(blocks, current)
				current = nil
			}
			continue
		}

		if match := blockStartPattern.FindStringSubmatch(line); match != nil {
			current = &protoBlock{
				kind:  match[1],
				name:  match[2],
				lines: append(slices.Clone(pendingComments), line),
			}
			pendingComments = nil
			depth = braceDepthDelta(line)
			if depth == 0 { // A block written on one line, such as `message Done {}`.
				blocks = append(blocks, current)
				current = nil
			}
			continue
		}

		const commentPrefix = "//"
		if strings.HasPrefix(strings.TrimSpace(line), commentPrefix) {
			pendingComments = append(pendingComments, line)
			continue
		}

		if len(blocks) > 0 { // Past the preamble only blank lines separate blocks.
			pendingComments = nil
			continue
		}

		preamble = append(preamble, pendingComments...)
		pendingComments = nil
		preamble = append(preamble, line)
	}

	return preamble, blocks
}

func injectJavaOptions(preamble []string, className string) []string {
	result := make([]string, 0, len(preamble)+1)
	for _, line := range preamble {
		result = append(result, line)
		const goPackageOption = "option go_package"
		if strings.HasPrefix(line, goPackageOption) {
			const optionFormat = `option java_multiple_files = true;
option java_outer_classname = %q;
option java_package = %q;`
			result = append(result, fmt.Sprintf(optionFormat, className, javaPackageName))
		}
	}
	return result
}

func trimService(service *protoBlock, wantedRPCs map[string]bool) (keptLines []string, seenRPCs map[string]bool) {
	seenRPCs = make(map[string]bool)
	pendingBlank := false

	for _, line := range service.lines {
		if strings.TrimSpace(line) == "" {
			pendingBlank = true
			continue
		}

		if match := rpcStartPattern.FindStringSubmatch(line); match != nil {
			seenRPCs[match[1]] = true
			if !wantedRPCs[match[1]] {
				continue
			}
			if pendingBlank && len(keptLines) > 0 {
				keptLines = append(keptLines, "")
			}
		}

		pendingBlank = false
		keptLines = append(keptLines, line)
	}

	return keptLines, seenRPCs
}

func collectReferencedTypes(text string, blockNames map[string]bool, kept map[string]bool) bool {
	widened := false
	for _, identifier := range identifierPattern.FindAllString(text, -1) {
		name, _, _ := strings.Cut(identifier, ".")
		if blockNames[name] && !kept[name] {
			kept[name] = true
			widened = true
		}
	}
	return widened
}

func trim(source, className string, wantedRPCs []string) (string, error) {
	wanted := make(map[string]bool, len(wantedRPCs))
	for _, name := range wantedRPCs {
		wanted[name] = true
	}

	preamble, blocks := parseSource(strings.Split(source, lineSeparator))
	blockNames := make(map[string]bool, len(blocks))
	for _, block := range blocks {
		blockNames[block.name] = true
	}

	kept := make(map[string]bool)
	var seedText strings.Builder
	for _, block := range blocks {
		if block.kind != serviceKind {
			continue
		}
		keptLines, seenRPCs := trimService(block, wanted)
		var missing []string
		for _, name := range wantedRPCs {
			if !seenRPCs[name] {
				missing = append(missing, name)
			}
		}
		if len(missing) > 0 {
			slices.Sort(missing)
			return "", E.New("wanted rpc not found upstream: ", strings.Join(missing, " "))
		}
		block.lines = keptLines
		kept[block.name] = true
		seedText.WriteString(block.text())
	}

	collectReferencedTypes(seedText.String(), blockNames, kept)
	for widened := true; widened; {
		widened = false
		for _, block := range blocks {
			if block.kind == serviceKind || !kept[block.name] {
				continue
			}
			if collectReferencedTypes(block.text(), blockNames, kept) {
				widened = true
			}
		}
	}

	sections := []string{
		strings.TrimRight(strings.Join(injectJavaOptions(preamble, className), lineSeparator), lineSeparator),
	}
	for _, block := range blocks {
		if kept[block.name] {
			sections = append(sections, strings.TrimRight(block.text(), lineSeparator))
		}
	}

	const blockSeparator = "\n\n"
	return strings.Join(sections, blockSeparator) + lineSeparator, nil
}

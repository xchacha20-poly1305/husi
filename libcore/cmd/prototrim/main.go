package main

import (
	"flag"
	"log"
	"os"
)

func main() {
	flag.Parse()
	arguments := flag.Args()
	const leadingArguments = 2
	if len(arguments) <= leadingArguments {
		log.Fatal("usage: prototrim <source.proto> <java outer classname> <rpc name>...")
	}

	source, err := os.ReadFile(arguments[0])
	if err != nil {
		log.Fatal(err)
	}

	trimmed, err := trim(string(source), arguments[1], arguments[leadingArguments:])
	if err != nil {
		log.Fatal(err)
	}

	_, err = os.Stdout.WriteString(trimmed)
	if err != nil {
		log.Fatal(err)
	}
}

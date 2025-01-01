package main

import (
	"bytes"
	"reflect"
	"strings"

	"github.com/sagernet/sing-box/option"
	F "github.com/sagernet/sing/common/format"

	"libcore/named"
)

// TODO ignore deprecated

const (
	classSpace = "    "     // 4
	fieldSpace = "        " // 8

	public      = "public "
	staticClass = "static class "
	extends     = "extends "
)

const extendsBox = "SingBoxOption"

// mainBuilder is shared buffer for building class content.
// It is safe because we just use one thread.
var mainBuilder = bytes.NewBuffer(make([]byte, mainBuilderSize))

// mainBuilderSize is the maximum size of the mainBuilder.
// We found this is the max size the mainBuilder will use.
const mainBuilderSize = 4096

func buildClass(opt any, belongs string) []byte {
	value := reflect.Indirect(reflect.ValueOf(opt))
	valueType := value.Type()

	mainBuilder.Reset()

	var fieldName string
	if belongs != extendsBox {
		fieldName = belongs + "_" + strings.ReplaceAll(valueType.Name(), belongs, "")
	} else {
		fieldName = valueType.Name()
	}
	// public static class ClashAPIOptions extends SingBoxOption {
	mainBuilder.WriteString(
		F.ToString(
			classSpace, public, staticClass,
			fieldName, " ",
			extends, belongs, " ",
			"{\n\n",
		),
	)

	// public String xxx;
	// public Boolean xxx;
	// public Integer xxx;
	mainBuilder.Write(buildContent(valueType))

	// }
	mainBuilder.WriteString(F.ToString(classSpace, "}\n"))

	return mainBuilder.Bytes()
}

func buildContent(valueType reflect.Type) []byte {
	builder := bytes.NewBuffer(nil)

	for i := 0; i < valueType.NumField(); i++ {
		field := valueType.Field(i)

		tag := field.Tag.Get("json")
		tag = strings.TrimSuffix(tag, ",omitempty")

		switch tag {
		case "-":
			continue
		case "":
			if field.Type.Kind() != reflect.Struct {
				panic("no tag and not struct: " + field.Name)
			}
			switch field.Name {
			case "RuleAction":
				_, _ = builder.Write(ruleActionFields)
			case "DNSRuleAction":
				_, _ = builder.Write(dnsRuleActionFields)
			default:
				_, _ = builder.WriteString(F.ToString(fieldSpace, "// Generate note: nested type ", field.Name, "\n"))
				_, _ = builder.Write(buildContent(field.Type))
			}
			continue
		case reservedDefault, reservedFinal:
			// @SerializedName("default")
			// public String default_;
			builder.WriteString(F.ToString(fieldSpace, "@SerializedName(\"", tag, "\")\n"))
			tag += "_"
		}

		typeName := className(field.Type)
		// Example:
		//         public String listen;
		builder.WriteString(F.ToString(fieldSpace, public, typeName, " ", tag, ";\n\n"))
	}

	return builder.Bytes()
}

const (
	javaBoolean = "Boolean"
	javaInteger = "Integer"
	javaLong    = "Long"
	javaString  = "String"

	reservedDefault = "default"
	reservedFinal   = "final"
)

func className(valueType reflect.Type) string {
	switch valueType.Kind() {
	case reflect.Ptr:
		return className(valueType.Elem())
	case reflect.Bool:
		return javaBoolean
	case reflect.Uint16:
		switch valueType.Name() {
		case "DNSQueryType":
			return javaString
		}
		return javaInteger
	case reflect.Int, reflect.Int32, reflect.Uint32:
		return javaInteger
	case reflect.Int64:
		switch valueType.Name() {
		case "Duration":
			return javaString
		}
		return javaLong
	case reflect.Uint64:
		return javaLong
	case reflect.String:
		return javaString
	case reflect.Slice:
		elem := valueType.Elem()
		if elem.Kind() == reflect.Uint8 {
			// Go json save []uint8 or []byte as base64 string
			return javaString
		}
		return "List<" + className(elem) + ">"
	case reflect.Map:
		return "Map<" + className(valueType.Key()) + ", " + className(valueType.Elem()) + ">"
	case reflect.Struct:
		valueName := valueType.Name()
		switch valueName {
		case "Addr", "Prefix", "Prefixable", "Regexp":
			return javaString
		case "NetworkList":
			return "Named<String>"
		}
		return valueType.Name()
	case reflect.Uint8:
		switch valueType.Name() {
		case "DomainStrategy":
			return javaString
		}
		return javaInteger
	default:
		panic(F.ToString("[", valueType.Name(), "] is not  included"))
	}
}

// Build rule action
func init() {
	ruleActions := []named.Named[any]{
		{
			Name:    "RouteActionOptions",
			Content: option.RouteActionOptions{},
		},
		{
			Name:    "RouteOptionsActionOptions",
			Content: option.RouteOptionsActionOptions{},
		},
		{
			Name:    "DirectActionOptions",
			Content: option.DirectActionOptions{},
		},
		{
			Name:    "RejectActionOptions",
			Content: option.RejectActionOptions{},
		},
		{
			Name:    "RouteActionSniff",
			Content: option.RouteActionSniff{},
		},
		{
			Name:    "RouteActionResolve",
			Content: option.RouteActionResolve{},
		},
	}
	for _, field := range ruleActions {
		ruleActionFields = append(ruleActionFields, buildContent(reflect.TypeOf(field.Content))...)
	}

	dnsRuleActions := []named.Named[any]{
		{
			Name:    "RouteOptions",
			Content: option.DNSRouteActionOptions{},
		},
		{
			Name:    "RouteOptionsOptions",
			Content: option.DNSRouteOptionsActionOptions{},
		},
		{
			Name:    "RejectOptions",
			Content: option.RejectActionOptions{},
		},
	}
	for _, field := range dnsRuleActions {
		dnsRuleActionFields = append(dnsRuleActionFields, buildContent(reflect.TypeOf(field.Content))...)
	}
}

const actionPrefix = fieldSpace + "// Generate Note: Action\n" +
	fieldSpace + "public String action;\n\n"

var (
	ruleActionFields    = []byte(actionPrefix)
	dnsRuleActionFields = []byte(actionPrefix)
)

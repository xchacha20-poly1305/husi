package main

import (
	"bytes"
	"reflect"
	"strings"

	"github.com/sagernet/sing-box/option"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/x/collections"
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
				if strings.HasPrefix(field.Name, "Legacy") {
					continue
				}
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
	javaList    = "List<"
	javaMap     = "Map<"

	reservedDefault = "default"
	reservedFinal   = "final"
)

func className(valueType reflect.Type) string {
	switch valueType.Kind() {
	case reflect.Ptr:
		return className(valueType.Elem())
	case reflect.Bool:
		return javaBoolean
	case reflect.Int, reflect.Uint16, reflect.Int32, reflect.Uint32, reflect.Uint8:
		switch valueType.Name() {
		case "DNSRCode", "DNSQueryType",
			/* Custom enum types */ "DomainStrategy", "InterfaceType", "NetworkStrategy":
			return javaString
		}
		return javaInteger
	case reflect.Int64, reflect.Uint64:
		switch valueType.Name() {
		case "Duration":
			return javaString
		}
		return javaLong
	case reflect.String:
		return javaString
	case reflect.Slice:
		elem := valueType.Elem()
		switch elem.Kind() {
		case reflect.Uint8:
			switch elem.Name() {
			case "byte", "uint8":
				// Go json save []uint8 or []byte as base64 string.
				return javaString
			default:
				// Others may be custom enum types
			}
		}
		return javaList + className(elem) + ">"
	case reflect.Map:
		return javaMap + className(valueType.Key()) + ", " + className(valueType.Elem()) + ">"
	case reflect.Struct:
		valueName := valueType.Name()
		switch valueName {
		case "Addr", "Prefix", "Prefixable",
			"Regexp", "DNSRecordOptions":
			return javaString
		case "NetworkList":
			return javaList + javaString + ">"
		case "SurgeURLRewriteLine", "SurgeHeaderRewriteLine",
			"SurgeBodyRewriteLine", "SurgeMapLocalLine":
			// Script
			return javaString
		default:
			if strings.HasPrefix(valueName, "TypedMap") {
				// What a great foot binding cloth
				linkedHashMap := valueType.Field(0).Type
				rawMap := linkedHashMap.Field(1).Type
				key := className(rawMap.Key())

				listElement := rawMap.Elem().Elem()       // map value + unwrap pointer => list.Element
				elementValue := listElement.Field(3).Type // MapEntry
				mapValue := elementValue.Field(1).Type    // Something that can get name easily
				value := className(mapValue)
				return javaMap + key + ", " + value + ">"
			}
		}
		return valueType.Name()
	case reflect.Interface:
		// any
		return javaString
	default:
		panic(F.ToString("[ ", valueType.Name(), " ] is not included"))
	}
}

// Build rule action
func init() {
	ruleActions := []collections.MapEntry[string, any]{
		{
			Key:   "RouteActionOptions",
			Value: option.RouteActionOptions{},
		},
		{
			Key:   "RouteOptionsActionOptions",
			Value: option.RouteOptionsActionOptions{},
		},
		{
			Key:   "DirectActionOptions",
			Value: option.DirectActionOptions{},
		},
		{
			Key:   "RejectActionOptions",
			Value: option.RejectActionOptions{},
		},
		{
			Key:   "RouteActionSniff",
			Value: option.RouteActionSniff{},
		},
		{
			Key:   "RouteActionResolve",
			Value: option.RouteActionResolve{},
		},
	}
	for _, field := range ruleActions {
		ruleActionFields = append(ruleActionFields, buildContent(reflect.TypeOf(field.Value))...)
	}

	dnsRuleActions := []collections.MapEntry[string, any]{
		{
			Key:   "RouteOptions",
			Value: option.DNSRouteActionOptions{},
		},
		{
			Key:   "RouteOptionsOptions",
			Value: option.DNSRouteOptionsActionOptions{},
		},
		{
			Key:    "Predefined",
			Value: option.DNSRouteActionPredefined{},
		},
		{
			Key:    "RejectOptions",
			Value: option.RejectActionOptions{},
		},
	}
	for _, field := range dnsRuleActions {
		dnsRuleActionFields = append(dnsRuleActionFields, buildContent(reflect.TypeOf(field.Value))...)
	}
}

const actionPrefix = fieldSpace + "// Generate Note: Action\n" +
	fieldSpace + "public String action;\n\n"

var (
	ruleActionFields    = []byte(actionPrefix)
	dnsRuleActionFields = []byte(actionPrefix)
)

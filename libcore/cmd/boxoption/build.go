package main

import (
	"bytes"
	"reflect"
	"strings"
	"unicode"

	"github.com/sagernet/sing-box/option"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/x/collections"
)

// TODO ignore deprecated

const (
	classSpace = "    "     // 4
	fieldSpace = "        " // 8

	openClass = "open class "
	extends   = ": "
)

const extendsBox = "SingBoxOption"

// mainBuilder is shared buffer for building class content.
// It is safe because we just use one thread.
var mainBuilder = bytes.NewBuffer(make([]byte, mainBuilderSize))

// mainBuilderSize is the maximum size of the mainBuilder.
// We found this is the max size the mainBuilder will use.
const mainBuilderSize = 8192

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
	// open class ClashAPIOptions : SingBoxOption {
	mainBuilder.WriteString(
		F.ToString(
			classSpace, "@KxsSerializable\n",
			classSpace, openClass,
			fieldName, " ",
			extends, belongs, "() ",
			"{\n\n",
		),
	)

	// @JvmField
	// var xxx: String? = null
	// var xxx: Boolean? = null
	// var xxx: Int? = null
	mainBuilder.Write(buildContent(valueType))

	// }
	mainBuilder.WriteString(F.ToString(classSpace, "}\n"))

	return mainBuilder.Bytes()
}

func buildContent(valueType reflect.Type) []byte {
	return buildContentWithSeen(valueType, map[string]struct{}{})
}

func buildContentWithSeen(valueType reflect.Type, seen map[string]struct{}) []byte {
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
				writeActionFields(builder, seen, ruleActionTypes)
			case "DNSRuleAction":
				writeActionFields(builder, seen, dnsRuleActionTypes)
			default:
				if strings.HasPrefix(field.Name, "Legacy") {
					continue
				}
				_, _ = builder.WriteString(F.ToString(fieldSpace, "// Generate note: nested type ", field.Name, "\n"))
				_, _ = builder.Write(buildContentWithSeen(field.Type, seen))
			}
			continue
		case reservedDefault, reservedFinal:
			// @SerialName("default")
			// var default_: String? = null
			builder.WriteString(F.ToString(fieldSpace, "@SerialName(\"", tag, "\")\n"))
			tag += "_"
		}

		if _, exists := seen[tag]; exists {
			continue
		}
		seen[tag] = struct{}{}

		typeName := className(field.Type)
		// Example:
		//         var listen: String? = null
		builder.WriteString(F.ToString(fieldSpace, "@JvmField\n", fieldSpace, "var ", kotlinFieldName(tag), ": ", typeName, "? = null\n\n"))
	}

	return builder.Bytes()
}

func writeActionFields(builder *bytes.Buffer, seen map[string]struct{}, actionTypes []reflect.Type) {
	if _, exists := seen["action"]; !exists {
		_, _ = builder.WriteString(actionPrefix)
		seen["action"] = struct{}{}
	}
	for _, actionType := range actionTypes {
		_, _ = builder.Write(buildContentWithSeen(actionType, seen))
	}
}

const (
	kotlinBoolean = "Boolean"
	kotlinInteger = "Int"
	kotlinLong    = "Long"
	kotlinString  = "String"
	kotlinJsonElement = "JsonElement"
	kotlinList    = "MutableList<"
	kotlinMap     = "MutableMap<"

	reservedDefault = "default"
	reservedFinal   = "final"
)

func className(valueType reflect.Type) string {
	switch valueType.Kind() {
	case reflect.Ptr:
		return className(valueType.Elem())
	case reflect.Bool:
		return kotlinBoolean
	case reflect.Int, reflect.Uint16, reflect.Int32, reflect.Uint32, reflect.Uint8:
		switch valueType.Name() {
		case "DNSRCode", "DNSQueryType",
			/* Custom enum types */ "DomainStrategy", "InterfaceType", "NetworkStrategy":
			return kotlinString
		}
		return kotlinInteger
	case reflect.Int64, reflect.Uint64:
		switch valueType.Name() {
		case "Duration":
			return kotlinString
		}
		return kotlinLong
	case reflect.String:
		return kotlinString
	case reflect.Slice:
		elem := valueType.Elem()
		switch elem.Kind() {
		case reflect.Uint8:
			switch elem.Name() {
			case "byte", "uint8":
				// Go json save []uint8 or []byte as base64 string.
				return kotlinString
			default:
				// Others may be custom enum types
			}
		}
		return kotlinList + className(elem) + ">"
	case reflect.Map:
		return kotlinMap + className(valueType.Key()) + ", " + className(valueType.Elem()) + ">"
	case reflect.Struct:
		valueName := valueType.Name()
		switch valueName {
		case "Addr", "Prefix", "Prefixable",
			"Regexp", "DNSRecordOptions", "NetworkBytesCompat":
			return kotlinString
		case "GeoIPOptions", "GeositeOptions", "InboundACMEOptions", "InboundECHOptions", "InboundRealityOptions":
			return kotlinJsonElement
		case "DNSServerOptions":
			return "NewDNSServerOptions"
		case "TunPlatformOptions":
			return "Inbound_TunPlatformOptions"
		case "HTTPProxyOptions":
			return "Inbound_HTTPProxyOptions"
		case "MemoryBytes":
			return kotlinInteger
		case "NetworkList":
			return kotlinList + kotlinString + ">"
		case "SurgeURLRewriteLine", "SurgeHeaderRewriteLine",
			"SurgeBodyRewriteLine", "SurgeMapLocalLine":
			// Script
			return kotlinString
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
				return kotlinMap + key + ", " + value + ">"
			}
		}
		return valueType.Name()
	case reflect.Interface:
		// any
		return kotlinString
	default:
		panic(F.ToString("[ ", valueType.Name(), " ] is not included"))
	}
}

func kotlinFieldName(name string) string {
	if isKotlinKeyword(name) || !isKotlinIdentifier(name) {
		return "`" + name + "`"
	}
	return name
}

func isKotlinIdentifier(value string) bool {
	if value == "" {
		return false
	}

	for index, char := range value {
		if index == 0 {
			if char != '_' && !unicode.IsLetter(char) {
				return false
			}
			continue
		}
		if char != '_' && !unicode.IsLetter(char) && !unicode.IsDigit(char) {
			return false
		}
	}

	return true
}

func isKotlinKeyword(value string) bool {
	switch value {
	case "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
		"interface", "is", "null", "object", "package", "return", "super", "this", "throw",
		"true", "try", "typealias", "val", "var", "when", "while", "by", "catch", "constructor",
		"delegate", "dynamic", "field", "file", "finally", "get", "import", "init", "param",
		"property", "receiver", "set", "setparam", "where", "actual", "abstract", "annotation",
		"companion", "const", "crossinline", "data", "enum", "expect", "external", "final",
		"infix", "inline", "inner", "internal", "lateinit", "noinline", "open", "operator",
		"out", "override", "private", "protected", "public", "reified", "sealed", "suspend",
		"tailrec", "vararg":
		return true
	default:
		return false
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
		ruleActionTypes = append(ruleActionTypes, reflect.TypeOf(field.Value))
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
			Key:   "Predefined",
			Value: option.DNSRouteActionPredefined{},
		},
		{
			Key:   "RejectOptions",
			Value: option.RejectActionOptions{},
		},
	}
	for _, field := range dnsRuleActions {
		dnsRuleActionTypes = append(dnsRuleActionTypes, reflect.TypeOf(field.Value))
	}
}

const actionPrefix = fieldSpace + "// Generate Note: Action\n" +
	fieldSpace + "@JvmField\n" +
	fieldSpace + "var action: String? = null\n\n"

var (
	ruleActionTypes    []reflect.Type
	dnsRuleActionTypes []reflect.Type
)

# Character Encoding

## <a id="text-vs-bytes">Text vs bytes mental model</a>
A Java String is a sequence of UTF-16 code units; files, network payloads, and many storage formats are bytes. A charset defines the reversible/partially reversible mapping between text characters/code points and byte sequences. Never assume bytes “are already text”.

## <a id="charset-encode-decode">Charset encode/decode</a>
Encoding converts text to bytes with a chosen charset; decoding converts bytes to text with a charset. Use explicit charsets such as `StandardCharsets.UTF_8` at boundaries.

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String copy = new String(bytes, StandardCharsets.UTF_8);
```

## <a id="default-charset-risk">Default charset portability risk</a>
APIs that use the platform default charset can behave differently across machines or process configuration. Since modern Java defaults have evolved, relying on “whatever the machine uses” is still a weak external-data contract. Declare the charset of persisted/networked content explicitly.

## <a id="malformed-input">Malformed/unmappable input boundary</a>
A decoder may encounter malformed byte sequences; an encoder may encounter characters not representable in the target charset. High-level convenience methods often replace invalid data, while `CharsetEncoder`/`CharsetDecoder` can be configured to report, replace, or ignore. Choose failure behavior deliberately when data integrity matters.

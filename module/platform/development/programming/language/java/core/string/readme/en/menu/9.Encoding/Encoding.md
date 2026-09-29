# Encoding and Charset

`String` represents text inside Java, while files, network payloads, and wire protocols ultimately move **bytes**. Encoding is the mapping between those two representations.

## <a id="text-vs-bytes">Text vs Bytes</a>

A String such as `"Xin chào"` does not have one universal byte sequence. Different charsets can encode the same text differently.

```text
String / text
        ↓ encode with Charset
byte[]
        ↓ decode with the same Charset
String / text
```

If encoding and decoding disagree about the charset, the bytes may arrive intact while the reconstructed text is wrong.

### WHY — a String does not “already have an encoding”

`String` is a Java text value. Encoding appears when text crosses a byte-oriented boundary:

```text
Java String
→ file
→ socket / HTTP body
→ database protocol
→ message-broker payload
```

The same text can have different byte representations:

```text
"é"

UTF-8
→ C3 A9

ISO-8859-1
→ E9
```

So “what encoding is this String?” is usually the wrong-layer question. Ask instead: **which Charset maps String ↔ bytes at this boundary?**

## <a id="charset-encode-decode">Charset Encode/Decode</a>

Make the charset explicit at boundaries:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

An explicit standard charset makes the contract independent of the machine's default configuration.

If the two sides disagree:

```text
text
→ encode as UTF-8
→ bytes
→ decode as ISO-8859-1
→ mojibake / wrong text
```

The transport may have delivered every byte correctly; the bug is the interpretation contract.

A round trip preserves text only when the charset and error policy can represent the input.

## <a id="default-charset-risk">Default Charset Risk</a>

Since **JDK 18**, Java SE standard APIs use UTF-8 as the default charset under JEP 400. In Java 21, `Charset.defaultCharset()` is UTF-8 unless changed in an implementation-specific way.

The older mental model that “the Java default charset normally follows each OS/locale” is therefore no longer the Java 21 default.

For persistent or network data, make the charset part of the explicit protocol/format contract.

### Why still specify Charset explicitly?

Because encoding is a **data contract**, not merely an environment setting:

```text
producer
→ UTF-8 bytes
→ consumer

the contract should say UTF-8
```

Explicit `StandardCharsets.UTF_8` makes intent visible in code, avoids compatibility/configuration ambiguity, and makes protocol/file-format review easier.

Using the runtime default is fine when that is genuinely the API contract; durable and network formats usually benefit from an explicit charset.

## <a id="malformed-input">Malformed and Unmappable Input</a>

Encoding/decoding can fail semantically:

- **malformed input**: a byte sequence is invalid for the charset;
- **unmappable character**: a character cannot be represented in the target charset.

`CharsetDecoder` and `CharsetEncoder` can report, replace, or ignore such cases. Choose that policy intentionally; silent replacement can hide data corruption.

### Malformed is different from unmappable

```text
malformed input
→ byte/code-unit sequence is invalid for the encoding being read

unmappable character
→ valid character cannot be represented in the target charset
```

A UTF-8 decoder can encounter malformed bytes; an encoder targeting US-ASCII can encounter a valid Unicode `é` that ASCII cannot represent.

### Convenience APIs vs strict validation

`getBytes(Charset)` and `new String(bytes, charset)` are convenient for common cases. When invalid input must be handled precisely, use `CharsetEncoder` / `CharsetDecoder` with an explicit `CodingErrorAction.REPORT`, `REPLACE`, or `IGNORE` policy.

```text
data integrity matters
→ REPORT often supports fail-fast behavior

best-effort display
→ REPLACE may be acceptable

IGNORE
→ can silently lose data, so it needs a deliberate reason
```

The next chapter asks whether Java `char` is really the same thing as one Unicode character a user sees.

# Encoding and Charset

After establishing the Unicode model used by Java text, the next boundary is external representation. Files, network payloads, and wire protocols ultimately move **bytes**, and encoding maps between those bytes and Java text.

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

Since **JDK 18**, JEP 400 defines UTF-8 as the **Java default charset**. Under standard JDK behavior on Java 21, `Charset.defaultCharset()` is therefore UTF-8, and APIs whose contract says they use the default charset follow that choice; an explicitly selected compatibility/runtime configuration can deliberately change it. Do not generalize this to “every standard I/O stream uses `Charset.defaultCharset()`”: consoles and some standard streams have separate charset behavior.

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

- **malformed input**: the input sequence is structurally invalid for the encode/decode operation, such as malformed UTF-8 bytes during decoding or an unpaired surrogate during encoding;
- **unmappable character**: a character cannot be represented in the target charset.

`CharsetDecoder` and `CharsetEncoder` can report, replace, or ignore such cases. Choose that policy intentionally; silent replacement can hide data corruption.

### Malformed is different from unmappable

```text
malformed input
→ input structure is invalid for the decoder/encoder
→ for example malformed UTF-8 bytes or an unpaired UTF-16 surrogate

unmappable character
→ valid character cannot be represented in the target charset
```

A UTF-8 decoder can encounter malformed bytes, while an encoder can encounter malformed UTF-16 input such as an unpaired surrogate. By contrast, a valid Unicode `é` encoded to US-ASCII is **unmappable** because ASCII cannot represent that character.

### Convenience APIs vs strict validation

`getBytes(Charset)` and `new String(bytes, charset)` are convenient for common cases, but these convenience overloads are specified to **replace** malformed/unmappable input using the charset's replacement rather than report it to the caller. If invalid data must be detected, ignored, or handled with a custom policy, use `CharsetEncoder` / `CharsetDecoder` with an explicit `CodingErrorAction.REPORT`, `REPLACE`, or `IGNORE` policy.

```text
data integrity matters
→ REPORT often supports fail-fast behavior

best-effort display
→ REPLACE may be acceptable

IGNORE
→ can silently lose data, so it needs a deliberate reason
```

The next chapter moves from representation boundaries to **regular expressions**, where patterns describe how text should be matched, searched, or transformed.

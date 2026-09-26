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

## <a id="charset-encode-decode">Charset Encode/Decode</a>

Make the charset explicit at boundaries:

```java
byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

An explicit standard charset makes the contract independent of the machine's default configuration.

## <a id="default-charset-risk">Default Charset Risk</a>

Code such as `text.getBytes()` or `new String(bytes)` may depend on the platform default charset.

That can behave differently across developer machines, CI, containers, and production.

For persistent or network data, make the charset part of the explicit protocol/format contract.

## <a id="malformed-input">Malformed and Unmappable Input</a>

Encoding/decoding can fail semantically:

- **malformed input**: a byte sequence is invalid for the charset;
- **unmappable character**: a character cannot be represented in the target charset.

`CharsetDecoder` and `CharsetEncoder` can report, replace, or ignore such cases. Choose that policy intentionally; silent replacement can hide data corruption.

The next chapter asks whether Java `char` is really the same thing as one Unicode character a user sees.

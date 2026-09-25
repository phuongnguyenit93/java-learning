# String Immutability

## <a id="string-immutability">Why String is immutable</a>
A `String` object's character sequence never changes after construction. Operations such as `substring`, `replace`, `toUpperCase`, or concatenation produce another string value when content changes. This makes sharing safe and lets strings serve reliably as hash keys.

## <a id="immutability-consequences">Sharing, hashing and thread-safety consequences</a>
Because content cannot mutate, a string can be shared across callers and threads without synchronization for its own state, cached hash values remain valid, and literals can be pooled safely. Immutability does not make surrounding mutable objects thread-safe; it only stabilizes the String value itself.

## <a id="string-operation-new-value">String operations return new values</a>
Ignoring a returned string means ignoring the transformation.

```java
String s = " java ";
s.trim();       // s is unchanged
s = s.trim();   // now s refers to "java"
```

The variable can be reassigned even though each String object is immutable.

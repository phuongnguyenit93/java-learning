# Try-with-Resources

## <a id="autocloseable">AutoCloseable contract</a>
A resource used in try-with-resources must implement `AutoCloseable` (or `Closeable`). The construct owns closing the declared resources when the try scope ends. `close()` may throw, so cleanup itself participates in exception handling.

## <a id="resource-close-order">Reverse resource close order</a>
Resources are closed in reverse declaration order, mirroring nested acquisition.

```java
try (A a = openA(); B b = openB()) {
    use(a, b);
} // b.close(), then a.close()
```

This matters when later resources depend on earlier ones.

## <a id="effective-final-resource">Java 9 effective-final resource usage</a>
A final or effectively-final variable declared before the `try` can be referenced directly in the resource specification in modern Java. The resource must not be reassigned because the construct needs a stable object to close.

## <a id="twr-vs-finally">Try-with-resources vs manual finally</a>
Try-with-resources is preferred for `AutoCloseable` resources because it generates reliable reverse-order cleanup and preserves close failures as suppressed exceptions when the body already failed. Manual `finally` code is easier to get wrong and can accidentally hide the primary failure.

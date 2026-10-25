# Jackson

## <a id="jackson-what">1. What is Jackson?</a>

Jackson is a widely used data-binding and serialization library family in the Java ecosystem, commonly used to convert between Java objects and JSON.

```text
Java Object
↔
JSON representation
```

`ObjectMapper` is the central API commonly encountered in Jackson Databind.

## <a id="jackson-why">2. Why does Jackson exist?</a>

Applications regularly exchange data across boundaries such as HTTP APIs, messages, files, and external systems.

Those boundaries cannot directly transmit an in-memory Java object graph. Objects must be represented in a transferable or storable format and reconstructed on the receiving side.

Jackson automates much of the binding between Java types and JSON structures.

## <a id="jackson-before">3. What happens without Jackson?</a>

Developers can build JSON strings and parse individual fields manually.

That works for very small payloads but becomes difficult to maintain with nested objects, collections, generic types, naming policies, date-time values, or schema evolution.

## <a id="jackson-model">4. Mental model</a>

```text
Java object
    ↓ serialization
JSON tokens/tree/text
    ↓ deserialization
Java object
```

Jackson exposes several API layers:

- data binding through `ObjectMapper`;
- tree model;
- streaming parser/generator;
- annotations and module extensions.

## <a id="jackson-object-mapping-boundary">5. How is Jackson different from Object Mapping?</a>

MapStruct and ModelMapper primarily transform:

```text
Object A → Object B
```

Jackson primarily transforms:

```text
Object ↔ external data representation
```

Jackson can bind JSON into a DTO, but its canonical concern here remains serialization/data binding rather than DTO-to-DTO mapping.

## <a id="jackson-spring-boundary">6. Boundary with Spring</a>

Spring Boot may auto-configure Jackson for HTTP message conversion, but Jackson itself is not part of Spring.

This module owns Jackson mechanics; Spring-specific HTTP integration belongs to Spring web modules.

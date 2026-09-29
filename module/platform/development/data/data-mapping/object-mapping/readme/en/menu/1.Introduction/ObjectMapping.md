# Object Mapping

## <a id="object-mapping-what">1. What is Object Mapping?</a>

Object Mapping transforms data from one object model into another object model while preserving or deliberately transforming its meaning according to a target contract.

Examples:

```text
Entity → DTO
Domain Model → API Response
Request DTO → Command
External Model → Internal Model
```

Object Mapping is not serialization. Both sides remain in-memory object models.

## <a id="object-mapping-why">2. Why is Object Mapping needed?</a>

One model should usually not serve every boundary at once.

A database entity has persistence concerns, an API DTO has external-contract concerns, and a domain model has business-invariant concerns.

Reusing one class everywhere allows changes at one boundary to leak into others.

Object Mapping lets each model keep its own responsibility while still moving data between them.

## <a id="object-mapping-without">3. What happens without a mapper?</a>

The simplest approach is manual mapping:

```java
UserDto dto = new UserDto();
dto.setId(entity.getId());
dto.setName(entity.getName());
```

Manual mapping is often the best choice when mappings are few, semantics are explicit, and complete control is desirable.

The problem grows when field count, nested objects, collections, and conversion rules increase, producing boilerplate and missed-field risk.

## <a id="object-mapping-model">4. Mental model</a>

```text
Source Model
     ↓
mapping rules
     ↓
conversion / transformation
     ↓
Target Model
```

Mapping rules may:

- copy fields directly;
- rename fields;
- convert types;
- map nested objects;
- map collections;
- derive one target field from multiple sources;
- ignore fields outside the target contract.

## <a id="object-mapping-tools">5. Where do MapStruct and ModelMapper fit?</a>

**MapStruct** favors compile-time code generation: mapping implementations are generated during compilation.

**ModelMapper** favors runtime mapping based on conventions plus reflection/configuration.

Both solve object-mapping concerns but have different trade-offs in explicitness, type safety, performance, and runtime behavior.

## <a id="object-mapping-boundary">6. Boundary</a>

Object Mapping:

```text
Java Object A
→ Java Object B
```

Serialization:

```text
Java Object
→ JSON / bytes / external representation
```

ORM Mapping:

```text
Object model
↔ relational persistence model
```

These concerns are related but have different canonical owners.

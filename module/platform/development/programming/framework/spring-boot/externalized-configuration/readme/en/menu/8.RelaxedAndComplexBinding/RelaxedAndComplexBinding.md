<a id="back-to-top"></a>

# Relaxed Binding, Complex Types, and Conversion

## Menu
- [How Relaxed Binding Normalizes Property Names](#relaxed-binding)
- [Mapping Canonical Properties to Environment Variables](#environment-variable-mapping)
- [Binding Nested Configuration Objects](#nested-object-binding)
- [Binding Lists and Sets](#list-set-binding)
- [Binding Maps and Preserving Special Keys](#map-binding)
- [Why Higher-Precedence Lists Replace Lower-Precedence Lists](#list-replacement)
- [Type Conversion During Binding](#type-conversion)
- [Duration, DataSize, and Other Common Target Types](#duration-datasize-types)

## <a id="relaxed-binding">How Relaxed Binding Normalizes Property Names</a>

<details>
<summary>Click for details</summary>

Relaxed binding separates the logical property name from the physical spelling used by a particular source. An application can define a Java property named firstName while configuration is written in canonical kebab form such as customer.first-name.

For @ConfigurationProperties, Boot recognizes common variants where the source supports them: kebab case, camel case, and underscore notation in property/YAML sources, plus an uppercase underscore form for system environment variables. This flexibility is for binding; it does not mean every consumer performs identical relaxed lookup. That is why the canonical lower-case kebab form remains the safest form for documentation, metadata, prefixes, and placeholders.

~~~text
customer.first-name      ← canonical application key
customer.firstName       ← accepted in suitable file sources
customer.first_name      ← accepted in suitable file sources
CUSTOMER_FIRSTNAME       ← environment-variable representation
~~~

</details>

- [Back to top](#back-to-top)

---

## <a id="environment-variable-mapping">Mapping Canonical Properties to Environment Variables</a>

<details>
<summary>Click for details</summary>

Operating systems typically restrict environment-variable names, so Boot defines a deterministic mapping from canonical property names. Starting from the canonical form: replace dots with underscores, remove dashes, and convert to uppercase.

~~~text
spring.main.log-startup-info
        ↓
SPRING_MAIN_LOGSTARTUPINFO
~~~

For indexed list elements, surround the numeric index with underscores. For example my.service[0].other maps to MY_SERVICE_0_OTHER.

This rule should be applied from the canonical property name, not invented independently per deployment. If a property name is hard to map predictably, improve the application-facing key rather than teaching operations teams a one-off spelling.

</details>

- [Back to top](#back-to-top)

---

## <a id="nested-object-binding">Binding Nested Configuration Objects</a>

<details>
<summary>Click for details</summary>

A configuration object can contain another object so the Java shape mirrors the property hierarchy. This is useful when a namespace has meaningful subgroups rather than a flat collection of unrelated fields.

~~~properties
mail.host=smtp.example.com
mail.security.enabled=true
mail.security.protocol=tls
~~~

~~~java
@ConfigurationProperties("mail")
public class MailProperties {
    private String host;
    private final Security security = new Security();

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Security getSecurity() { return security; }

    public static class Security {
        private boolean enabled;
        private String protocol;
        // getters/setters
    }
}
~~~

Nested binding keeps related values close and gives validation a place to express nested constraints. It should reflect the configuration domain rather than reproduce every package or class boundary in the codebase.

</details>

- [Back to top](#back-to-top)

---

## <a id="list-set-binding">Binding Lists and Sets</a>

<details>
<summary>Click for details</summary>

Boot can bind indexed or comma-separated values into collection properties. YAML lists are especially readable for structured configuration, while .properties files can use indexed keys or supported comma-separated forms.

~~~yaml
app:
  servers:
    - api-a.example
    - api-b.example
~~~

For environment variables, list indexes use the underscore convention, such as APP_SERVERS_0 and APP_SERVERS_1 where the target property shape allows it. Collection binding still follows property-source precedence, but lists have an important replacement rule discussed separately: do not assume elements from several sources are merged element-by-element.

</details>

- [Back to top](#back-to-top)

---

## <a id="map-binding">Binding Maps and Preserving Special Keys</a>

<details>
<summary>Click for details</summary>

Maps are useful when the set of configuration keys is data-driven. Boot can bind a subtree into Map properties while still applying conversion to map values.

~~~properties
labels.region=eu
labels.tier=gold
~~~

Special characters in map keys require attention. Bracket notation preserves characters that would otherwise be interpreted as path separators or removed during binding. For a Map<String,Object>, a key such as [a.b] keeps a.b as one map key, whereas a.b without brackets naturally describes nested structure.

The map key rules are part of the binding contract, not a reason to encode arbitrary application data into property names. If configuration starts resembling a large document store, reconsider whether properties are the right representation.

</details>

- [Back to top](#back-to-top)

---

## <a id="list-replacement">Why Higher-Precedence Lists Replace Lower-Precedence Lists</a>

<details>
<summary>Click for details</summary>

For ordinary complex-list binding through `@ConfigurationProperties`, when the same list property is defined in more than one configuration source, Spring Boot does not merge the bound list element-by-element across those sources. The list supplied by the higher-precedence participating source replaces the lower-precedence list as a whole.

~~~yaml
# lower precedence
app:
  users:
    - name: alice
      role: reader
    - name: bob
      role: writer

# higher precedence
app:
  users:
    - name: carol
      role: admin
~~~

The effective users list contains only carol. This matters with profile-specific Config Data and external overrides: overriding one element is not a patch operation over the old list. Maps behave differently and can combine keys from multiple sources, with higher-precedence values winning for duplicate keys.

Do not generalize this binding rule to every list-valued Boot property. `spring.profiles.include` is a documented special case: Boot processes includes per property source and adds the resulting profiles rather than treating the property like an ordinary complex list that is replaced by the single highest-precedence source. Model each mechanism by its own documented contract instead of assuming all list-shaped configuration behaves identically.

</details>

- [Back to top](#back-to-top)

---

## <a id="type-conversion">Type Conversion During Binding</a>

<details>
<summary>Click for details</summary>

Configuration sources are text-oriented, but typed property objects are not. During @ConfigurationProperties binding, Boot converts resolved values to the target Java types. Common conversions include numbers, booleans, enums, InetAddress, URI, Duration, DataSize, and many standard Java types.

~~~properties
client.timeout=2s
client.max-payload=10MB
~~~

~~~java
@ConfigurationProperties("client")
record ClientProperties(Duration timeout, DataSize maxPayload) {}
~~~

A conversion failure is distinct from a missing config file or a validation failure: the key was found, but its value could not become the target type. Custom conversion is possible, but conversion beans may be requested early in the application lifecycle; keep their dependencies minimal. Generic Spring conversion internals remain outside this module.

</details>

- [Back to top](#back-to-top)

---

## <a id="duration-datasize-types">Duration, DataSize, and Other Common Target Types</a>

<details>
<summary>Click for details</summary>

Duration and DataSize deserve explicit units because bare numbers can be ambiguous. Boot accepts readable forms such as 250ms, 2s, 5m, 10KB, or 20MB when binding to the corresponding types.

~~~properties
cache.ttl=30s
upload.max-size=25MB
~~~

Annotations such as @DurationUnit or @DataSizeUnit can define the unit assumed for a bare numeric value when compatibility with an older numeric property is required. Prefer explicit units in new configuration because they make intent visible to both people and tooling.

The broader lesson is to expose configuration in domain-appropriate types. A Duration communicates time semantics more safely than a long whose unit must be remembered from documentation.

</details>

- [Back to top](#back-to-top)

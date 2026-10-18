<a id="back-to-top"></a>

# Back-off and User Control

## Menu
- [Back-off as a Design Contract](#back-off-as-design-contract)
- [How User-defined Beans Take Control](#user-defined-beans-win)
- [Bean Type Specificity and Condition Visibility](#bean-type-specificity)
- [Configurable Defaults vs Forced Policy](#defaults-vs-forced-policy)
- [Back-off Failure Patterns](#back-off-failure-patterns)

## <a id="back-off-as-design-contract">Back-off as a Design Contract</a>

<details>
<summary>Click for details</summary>

Back-off is the rule that a reusable default should disappear when the application has made an explicit choice that serves the same role. It is one of the main reasons auto-configuration remains convenient without becoming invasive.

The design question is not merely “can Boot create this bean?” but:

~~~text
Does the application already own this decision?
        ↓
yes → back off
no  → contribute the safe default
~~~

ConditionalOnMissingBean is the common mechanism, but the principle also appears in property switches, exclusions, and narrower applicability conditions.

Good back-off behavior is observable: adding an application-defined alternative should change the context predictably without requiring the user to disable the entire integration.

</details>

- [Back to top](#back-to-top)

---

## <a id="user-defined-beans-win">How User-defined Beans Take Control</a>

<details>
<summary>Click for details</summary>

EnableAutoConfiguration is designed so auto-configuration is applied after user-defined bean definitions have been registered. That gives missing-bean conditions a chance to see the application's explicit choices.

For example:

~~~java
@Bean
@ConditionalOnMissingBean
AcmeClient acmeClient() {
    return new DefaultAcmeClient();
}
~~~

If the application defines its own AcmeClient bean, the default should not be registered. This is more useful than asking the user to exclude the whole Acme auto-configuration just to customize one collaborator.

The exact search strategy and target type still matter. Back-off is deterministic only when the author clearly defines which bean role counts as “the application already decided.”

</details>

- [Back to top](#back-to-top)

---

## <a id="bean-type-specificity">Bean Type Specificity and Condition Visibility</a>

<details>
<summary>Click for details</summary>

A bean condition works on types and names visible to the condition. The declared type of a Bean method therefore affects how other conditions can reason about it.

Suppose an auto-configuration creates PremiumAcmeClient but declares the method as the broad AcmeClient interface. A later condition searching specifically for PremiumAcmeClient may not be able to infer that more specific type from the bean definition at the time it evaluates.

Prefer a return type that is at least as specific as the conditions and injection points that need to reason about it:

~~~java
@Bean
@ConditionalOnMissingBean(AcmeClient.class)
DefaultAcmeClient acmeClient() {
    return new DefaultAcmeClient();
}
~~~

The explicit condition target preserves the intended back-off contract: any user-provided `AcmeClient` prevents the default bean. The concrete method return type still exposes `DefaultAcmeClient` information to conditions that need to reason about that implementation. This is not a rule to expose implementation classes unnecessarily; it is a reminder that condition targets and bean-definition type visibility are related but distinct authoring choices.

</details>

- [Back to top](#back-to-top)

---

## <a id="defaults-vs-forced-policy">Configurable Defaults vs Forced Policy</a>

<details>
<summary>Click for details</summary>

A default is a value or implementation chosen for convenience while leaving a supported path for the application to choose differently. Forced policy is configuration that is difficult or impossible for the application to replace.

Good default:

~~~text
no AcmeClient bean
→ create DefaultAcmeClient

custom AcmeClient bean
→ use the custom bean
~~~

Overly aggressive policy:

~~~text
always create DefaultAcmeClient
→ custom bean creates ambiguity or is ignored
→ application must fight the integration
~~~

Not every infrastructure bean must be replaceable in every way, but extension points should be deliberate. A starter should make its supported customization surface clear instead of relying on accidental bean-definition conflicts.

</details>

- [Back to top](#back-to-top)

---

## <a id="back-off-failure-patterns">Back-off Failure Patterns</a>

<details>
<summary>Click for details</summary>

Back-off failures usually appear in a few recognizable forms:

- the condition targets the wrong type, so a user bean is not recognized;
- the configuration evaluates a bean condition before the relevant definition is available;
- multiple defaults use overlapping missing-bean conditions and become order-sensitive;
- an auto-configuration creates infrastructure unconditionally and forces the user to exclude too much;
- a user expects replacement by bean name while the condition actually searches by type, or the reverse.

Diagnose the failure from the condition's point of view. Inspect the Condition Evaluation Report, the bean definitions present at evaluation time, and the exact target type/name.

The design test is simple: define the supported user override and prove that the default disappears while the rest of the integration remains healthy.

Once individual defaults back off correctly, multiple auto-configurations still need to cooperate predictably. The next chapter separates configuration ordering from bean creation and shows how optional pieces compose safely.

</details>

- [Back to top](#back-to-top)

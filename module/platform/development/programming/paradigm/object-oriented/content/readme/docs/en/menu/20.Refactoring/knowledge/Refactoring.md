<a id="back-to-top"></a>

# Refactoring

## Menu
- [Recognizing Signs That Design Needs Improvement](#refactoring-001)
- [Renaming to Clarify Intent](#refactoring-002)
- [Extracting Methods and Classes](#refactoring-003)
- [Moving Behavior to the Responsible Object](#refactoring-004)
- [Encapsulating Fields and Internal Structures](#refactoring-005)
- [Replacing Type-Based Conditionals with Polymorphism](#refactoring-006)
- [Replacing Inheritance with Composition](#refactoring-007)
- [Consolidating or Removing Unnecessary Abstractions](#refactoring-008)
- [Improving Design Incrementally with Protective Tests](#refactoring-009)

## <a id="refactoring-001">Recognizing Signs That Design Needs Improvement</a>

<details>
<summary>View details</summary>

# Recognizing Signs That OOP Design Needs Improvement

> **Topic:** Refactoring — recognizing when design starts causing difficulties, identifying underlying causes, and choosing small, verifiable improvements.

A Java program that **runs correctly** is not necessarily **easy to change**. Warning signs appear when adding one small rule requires edits in many places, one class handles unrelated concerns, a business condition is duplicated, or testing a simple feature requires constructing numerous dependencies.

Such symptoms are commonly called **code smells**: signals that a code structure may deserve investigation. **A code smell is not automatically a defect**, and there is no rule requiring every smell to be refactored. Instead, assess the design's *impact* on debugging, extension, comprehension, and testing, then improve it at a reasonable cost.

## 1. Examine the Difficulty of Change, Not Just Code Length

Ask practical questions:

- When adding a small feature, how many classes must be opened and modified? Do those edits share one reason to change?
- When fixing a bug, are business conditions or formulas duplicated in several places?
- Does a class perform business calculations, query data, format UI content, and send email?
- Can a method be understood from its name and the abstraction level of its steps, or must readers track many flags, nested branches, and temporary variables?
- Can tests easily construct objects and verify behavior, or must they mock numerous implementation details?
- Does modifying one class frequently cause defects in seemingly unrelated classes?

These answers are more useful than line counts alone: a long, stable method expressing a straightforward sequence may be less risky than a short method with hidden side effects.

### Common Warning Signs

| Code smell | Observation in Java code | Practical risk | Possible direction, not a mandate |
|---|---|---|---|
| **Long Method** | A method mixes many steps, branches, or abstraction levels | Difficult to read and test individual rules | Use `Extract Method` to name meaningful steps |
| **Large Class / God Object** | A class owns unrelated responsibilities and many fields and method groups | Every change has a broad impact | Split by reasons to change; improve cohesion |
| **Duplicate Code** | A business condition or formula appears in multiple places | Missing an update creates inconsistent behavior | Centralize genuinely shared rules; avoid abstracting coincidental similarities |
| **Shotgun Surgery** | A small requirement forces edits across many classes | Scattered changes are difficult to review | Move related logic to an appropriate owner |
| **Divergent Change** | A class repeatedly changes for unrelated reasons | Unclear responsibilities and cross-impact risks | Separate responsibilities along distinct change axes |
| **Feature Envy** | A method on A repeatedly inspects B's data/details to make decisions | Rules live in the wrong place; low cohesion | Consider `Move Method` or a meaningful API on B |
| **Primitive Obsession** | `String`/`int`/`boolean` carry many domain rules and "magic strings" | Unit confusion and invalid states | Use `enum`, records/value objects, or domain types |
| **Long Parameter List / Data Clumps** | Repeated parameter groups and many boolean flags | Wrong argument order and expensive changes | Introduce a parameter object when the group represents one concept |
| **Conditional Complexity** | Repeated `switch`/`if` branches based on object type | Each new type requires edits in many places | Consider polymorphism, Strategy, or rule tables |
| **Inappropriate Intimacy / Tight Coupling** | A class knows another class's internals and directly constructs many dependencies | Testing and substitution are difficult | Reduce coupling; inject dependencies at meaningful boundaries |
| **Data Class / Anemic Model** | Domain objects only expose getters/setters while associated rules live in services | Invariants are hard to enforce | Move *domain-owned behavior* to its appropriate owner |
| **Speculative Generality** | Interfaces, inheritance, and plugin points lack a real need | Too many classes and poor navigability | Remove abstraction layers without clear benefits |
| **Comments as Deodorant** | Comments must explain obscure branches | Intent is hidden behind confusing structure | Improve names and decompose logic; retain comments explaining why |
| **Mutable Shared State** | Many objects mutate the same shared list/cache | Order-dependent bugs and race conditions | Prefer immutable data and explicit ownership/concurrency management |

**Do not impose rigid line-count or class-count rules.** A small class with conflicting rules can still be problematic. Conversely, splitting simple logic across 15 interfaces may make the design worse.

## 2. Distinguishing Easily Confused Code Smells

**Divergent Change** and **Shotgun Surgery** both make code changes painful, but from different perspectives:

- **Divergent Change:** *One class* must change for several independent **reasons**; for example, `OrderManager` is affected by pricing policy, email-format, and storage-schema changes.
- **Shotgun Surgery:** *One reason for change* forces edits in **many classes**; for example, changing the free-shipping threshold requires updates to `Cart`, `Checkout`, `Invoice`, and `OrderPreview`.

Likewise, **Duplicate Code** does not mean every similar snippet must be merged. Rules that look alike today but belong to contexts that change independently may be better kept separate. An abstraction should represent **shared knowledge or policy**, not merely shared syntax.

**Long Method** differs from **Complex Method**: a method that describes many sequential steps is not necessarily hard to understand, while a short method involving state interactions and side effects may be complex.

### From Observation to a Design Hypothesis

| Observation | Hypothesis to test | Evidence to seek |
|---|---|---|
| Changing one rule requires editing 5 files | The rule is scattered | Previous diffs and duplicated formulas |
| Testing one class requires 8 mocked collaborators | Too many responsibilities or high coupling | Constructors, I/O calls, and test setup |
| Newcomers take a long time to understand a method | Unclear abstraction levels or names | Nested branches, temporary variables, explanations needed during review |
| Many `boolean` flags | Multiple modes control one behavior | Flag combinations, invalid combinations, and missing tests |
| The same bug occurs across multiple screens | The same rule has inconsistent implementations | Failing inputs/outputs and copies of the rule |
| Change velocity noticeably declines | A design hotspot may exist | Edit frequency, regressions, review time, and diff scope |

To avoid hasty conclusions, examine **responsibilities**, **change history**, **actual defects**, and **existing tests**. Static analysis and complexity warnings can help locate candidates, but metrics only provide supporting signals.

## 3. Example: Three Responsibilities Mixed in One Class

Suppose a quotation system has an established contract:

- `subtotalCents` is the goods subtotal in **cents**, from `0` to `1_000_000`.
- Valid customer types are the case-sensitive strings `"STANDARD"` and `"VIP"`.
- VIP customers receive a `10%` discount on goods (rounded down to the cent) and free shipping.
- Standard customers receive no discount; shipping is free when `subtotalCents >= 100_000` and otherwise costs `15_000` cents.
- `total = subtotal - discount + shipping`.
- Invalid input must throw `IllegalArgumentException`.
- The result string has the format `discount=...;shipping=...;total=...`.

The initial version combines **input validation**, **pricing calculations**, and **output formatting** in one method. Changes to shipping rules, output format, or customer types all affect that code. This suggests **Divergent Change** as well as **Primitive Obsession**, because a `String` represents the customer type.

One refactoring option extracts **pricing rules**, a **formatter**, and a **facade** preserving the old API. This is only *one* sensible choice when those parts have independent reasons to change; adding classes may not be worth the cost in a tiny application.

### Running Before and After Refactoring (Java 17+)

Create `DesignSmellsDemo.java` and place the complete code below in it:

~~~java
import java.util.List;

public class DesignSmellsDemo {
    private static final long MAX_SUBTOTAL = 1_000_000L;
    private static final long FREE_SHIPPING_AT = 100_000L;
    private static final long SHIPPING_FEE = 15_000L;

    // API mà client sử dụng; cả hai phiên bản đều thực hiện.
    interface QuoteService {
        String quote(String customerType, long subtotalCents);
    }

    // BEFORE: validation, chính sách giá và định dạng nằm chung.
    static final class LegacyQuoteService implements QuoteService {
        @Override
        public String quote(String customerType, long subtotalCents) {
            if (subtotalCents < 0 || subtotalCents > MAX_SUBTOTAL) {
                throw new IllegalArgumentException("subtotal");
            }
            if (!"VIP".equals(customerType)
                    && !"STANDARD".equals(customerType)) {
                throw new IllegalArgumentException("customerType");
            }

            boolean vip = "VIP".equals(customerType);
            long discount = vip ? subtotalCents / 10 : 0;
            long shipping = (vip || subtotalCents >= FREE_SHIPPING_AT)
                    ? 0 : SHIPPING_FEE;
            long total = subtotalCents - discount + shipping;

            return "discount=" + discount
                    + ";shipping=" + shipping
                    + ";total=" + total;
        }
    }

    // AFTER: biểu diễn loại khách hàng bằng một kiểu miền rõ ràng.
    enum CustomerType {
        STANDARD, VIP;

        static CustomerType parse(String text) {
            if ("STANDARD".equals(text)) return STANDARD;
            if ("VIP".equals(text)) return VIP;
            throw new IllegalArgumentException("customerType");
        }
    }

    // Value object bất biến; trường dữ liệu không bị trộn với format.
    record Price(long discount, long shipping, long total) {}

    static final class PricingRules {
        Price calculate(CustomerType type, long subtotalCents) {
            long discount = type == CustomerType.VIP ? subtotalCents / 10 : 0;
            long shipping =
                    (type == CustomerType.VIP || subtotalCents >= FREE_SHIPPING_AT)
                        ? 0 : SHIPPING_FEE;
            return new Price(discount, shipping,
                    subtotalCents - discount + shipping);
        }
    }

    static final class QuoteFormatter {
        String format(Price price) {
            return "discount=" + price.discount()
                    + ";shipping=" + price.shipping()
                    + ";total=" + price.total();
        }
    }

    // Giữ API cũ; phối hợp các trách nhiệm đã tách ra.
    static final class RefactoredQuoteService implements QuoteService {
        private final PricingRules pricing;
        private final QuoteFormatter formatter;

        RefactoredQuoteService(PricingRules pricing, QuoteFormatter formatter) {
            this.pricing = java.util.Objects.requireNonNull(pricing);
            this.formatter = java.util.Objects.requireNonNull(formatter);
        }

        @Override
        public String quote(String customerType, long subtotalCents) {
            if (subtotalCents < 0 || subtotalCents > MAX_SUBTOTAL) {
                throw new IllegalArgumentException("subtotal");
            }
            CustomerType type = CustomerType.parse(customerType);
            Price price = pricing.calculate(type, subtotalCents);
            return formatter.format(price);
        }
    }

    record Example(String type, long subtotal, String expected) {}

    // Expected values được viết theo yêu cầu, không tính bằng production code.
    private static final List<Example> EXAMPLES = List.of(
        new Example("STANDARD", 0,
                "discount=0;shipping=15000;total=15000"),
        new Example("STANDARD", 99_999,
                "discount=0;shipping=15000;total=114999"),
        new Example("STANDARD", 100_000,
                "discount=0;shipping=0;total=100000"),
        new Example("VIP", 100_000,
                "discount=10000;shipping=0;total=90000"),
        new Example("VIP", 250_001,
                "discount=25000;shipping=0;total=225001"),
        new Example("STANDARD", 1_000_000,
                "discount=0;shipping=0;total=1000000")
    );

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void verifyBehavior(String label, QuoteService service) {
        for (Example ex : EXAMPLES) {
            String actual = service.quote(ex.type(), ex.subtotal());
            require(ex.expected().equals(actual),
                    label + " mismatch at " + ex + "; actual=" + actual);
        }
        expectInvalid(() -> service.quote("VIP", -1));
        expectInvalid(() -> service.quote("STANDARD", 1_000_001));
        expectInvalid(() -> service.quote("UNKNOWN", 50_000));
        expectInvalid(() -> service.quote(null, 50_000));
    }

    interface Action {
        void run();
    }

    private static void expectInvalid(Action action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        QuoteService legacy = new LegacyQuoteService();
        QuoteService refactored =
                new RefactoredQuoteService(new PricingRules(), new QuoteFormatter());

        verifyBehavior("legacy", legacy);
        verifyBehavior("refactored", refactored);

        for (Example ex : EXAMPLES) {
            require(
                legacy.quote(ex.type(), ex.subtotal())
                    .equals(refactored.quote(ex.type(), ex.subtotal())),
                "Observable behavior changed at " + ex);
        }

        System.out.println("PASS: original business rules");
        System.out.println("PASS: refactored business rules");
        System.out.println("PASS: same public API and observable output");
        System.out.println(refactored.quote("VIP", 250_001));
    }
}
~~~

Compile and run:

~~~bash
javac DesignSmellsDemo.java
java DesignSmellsDemo
~~~

Expected output:

~~~text
PASS: original business rules
PASS: refactored business rules
PASS: same public API and observable output
discount=25000;shipping=0;total=225001
~~~

Here, the **design benefit** is the ability to test and modify `PricingRules` independently from `QuoteFormatter` while reducing the risk that presentation changes unintentionally affect pricing. `CustomerType` replaces unrestricted strings with a domain enum; the external API still accepts strings under the original contract.

**Important note:** This example is intentionally small so it can run independently. Do not conclude that every method resembling `LegacyQuoteService.quote` must be split into three classes. Consider change frequency and testing benefits before introducing additional abstractions.

## 4. Follow the Change: Where Must You Edit, and What Is Affected?

Consider a request: "Raise the free-shipping threshold from 100.000 to 120.000 cents."

- If one clearly defined policy is updated, tests cover the new thresholds, and output formatting remains unaffected, the design has a good responsibility boundary.
- If the same constant must change in `CartController`, `CheckoutService`, `OrderConfirmationEmail`, and `InvoiceExporter`, **Shotgun Surgery** is likely. Verify whether they really share *the same business rule*.
- If the affected class contains pricing formulas, DB persistence, SMS delivery, and HTML rendering, it may suffer from **Divergent Change**.
- If adding a customer type always requires updating `switch` blocks in five classes, consider **polymorphism/Strategy** or moving the logic to an appropriate owner. A single `switch` is not necessarily a smell.

~~~mermaid
flowchart TD
    A["Phát hiện khó khăn khi sửa hoặc test"] --> B["Mô tả triệu chứng bằng ví dụ cụ thể"]
    B --> C["Xác định các lý do thay đổi và trách nhiệm"]
    C --> D{"Có bằng chứng về chi phí hoặc lỗi?"}
    D -->|Chưa| E["Theo dõi; tránh trừu tượng hóa vội"]
    D -->|Có| F["Đặt giả thuyết: lặp logic, coupling, cohesion..."]
    F --> G["Chọn phép refactor nhỏ nhất phù hợp"]
    G --> H["Chạy unit/contract/regression tests"]
    H --> I{"Hành vi còn đúng và code dễ thay đổi hơn?"}
    I -->|Không| J["Điều chỉnh hoặc hoàn tác bước vừa làm"]
    I -->|Có| K["Giữ thay đổi, ghi lại lý do thiết kế"]
~~~

## 5. Choose Refactorings That Address the Cause

| Confirmed problem | Potential technique | Important caution |
|---|---|---|
| A method contains several meaningful groups of operations | `Extract Method` and intention-revealing names | Avoid chains of forwarding methods that add no clarity |
| A class mixes UI, business logic, and data access | `Extract Class` and responsibility-based layering | Preserve boundary contracts and avoid cyclic dependencies |
| The same rule is duplicated | `Extract Function` or move to a shared service/value object | Consolidate only genuinely shared rules |
| Domain parameters are error-prone strings/numbers | `Replace Primitive with Object`, `enum`, `record` | Preserve serialization/deserialization and API compatibility |
| Type-based branches repeat across components | `Replace Conditional with Polymorphism` or Strategy | Avoid hierarchies for one simple, stable condition |
| A method manipulates another class's data extensively | `Move Method`, `Encapsulate Field` | Avoid breaking encapsulation when moving behavior |
| Object creation tightly couples a class to dependencies | Constructor injection and interfaces at suitable boundaries | Not every class needs an interface |
| Inheritance exists only to reuse a few lines | Switch to composition or delegation | Verify substitutability and compatibility before removing inheritance |
| A boolean flag selects two different behaviors | `Replace Parameter with Explicit Methods` or a domain type | Consider client-facing APIs, not only internal naming |
| Many getters/setters permit invalid states | Move validation/invariants into entities/value objects | Avoid making pure data objects unnecessarily complex |

**Cohesion:** The parts of a class serve one meaningful responsibility. **Coupling:** The degree to which modules need to know about each other. We generally want higher cohesion and less unnecessary coupling while balancing the complexity introduced by abstractions.

## 6. Four Levels of Evidence Before Proposing a Refactoring

### Level 1: Code-Reading Signals

Examine class/method names, repeated conditions, unrelated fields, and dependency counts to form a hypothesis. This is preliminary evidence, **not enough** to determine design costs.

### Level 2: Actual Changes

Trace one specific requirement: how many places change, where defects occur, and what makes review difficult. Areas that are both **frequently changed** and **complex** deserve higher priority than old code rarely touched.

### Level 3: Behavioral Tests

Identify inputs, expected outputs, exception types, state, and observable side effects. Test ordinary cases, boundaries, failures, and operation sequences. Establish regression tests before refactoring important business logic.

### Level 4: Evaluation After Improvement

A good refactoring makes subsequent changes easier to locate, tests clearer, and rules less duplicated without violating contracts. If it merely increases class count and makes abstractions harder to understand, there is insufficient evidence of improvement.

Useful metrics include **cyclomatic complexity**, dependency counts, duplication, files frequently edited together, time to fix bugs, and regression counts. No universal threshold is correct for every project.

## 7. When Should You Avoid Refactoring Immediately?

- **No current change need, little risk, and sufficiently clear code:** Document the issue and prioritize areas with greater impact.
- **Code lacks tests and handles money, data, or critical workflows:** First write characterization/contract tests rather than refactor based on guesses.
- **Business rules are not yet understood:** Consult domain owners and clarify edge cases before splitting classes.
- **An urgent bug fix is underway:** Isolate the fix and add regression tests; scope large refactorings separately if they increase risk.
- **An abstraction has one stable implementation and does not simplify tests or substitution:** Consider using a concrete class; interfaces do not automatically improve design.
- **Two snippets share syntax but change independently:** Merging them may introduce inappropriate coupling, causing changes in domain A to break domain B.

**Technical debt** can describe the future costs of an existing design, but not every smell is a debt that must be repaid immediately. Consider impact, likelihood of change, and the cost of improvement.

## 8. Checklist for Analyzing a Java Class

When reviewing a class, use this sequence:

1. **Purpose:** Describe the class's responsibility in one sentence. If it needs several unrelated "and" clauses, reassess cohesion.
2. **Boundary:** Which methods do clients use? Do they depend on internals, call order, or hidden state?
3. **Changes:** Identify 2–3 recent requirements that would force class edits. Do they share one reason to change?
4. **Dependencies:** Does the class create dependencies and perform I/O throughout its methods, or are dependencies managed at clear boundaries?
5. **Domain representation:** Can invalid values be passed using `String`/`int`/multiple booleans? Would an enum/value object help?
6. **Duplication:** Does the business logic exist elsewhere? Is it genuinely shared or merely coincidentally similar?
7. **Tests:** Can you state a clear input and expected output/exception? Do existing tests protect the change?
8. **Scope:** What is the smallest refactoring, and how can unchanged behavior be demonstrated?

A useful code-review report might say: **"For change X, we had to edit the same rule in A/B/C, risking inconsistency. Move the shared rule into Y and preserve boundary tests Z."** This is more actionable than "the code is bad; apply SOLID."

## 9. Common Mistakes

**Treating SOLID as a mandatory checklist.** SRP, OCP, LSP, ISP, and DIP are design guidelines; they do not require an interface for every method or class. Start with a real problem and apply the relevant principle.

**Mixing refactoring with business changes without separating their scopes.** If a new pricing requirement changes expected output, document it as a functional change. Pure refactoring must preserve the observable contract.

**Replacing outcome tests with implementation-detail tests.** Tests asserting how often a private method is called break when classes are split, even if results stay correct. Prefer public behavior; test interactions only when they form a meaningful contract (for example, sending a notification at most once).

**Measuring only complexity before and after.** Lower complexity metrics are insufficient if the API becomes harder to use, processing gets significantly slower, or unsafe state emerges. Test and assess actual development impact.

**Mistaking additional classes for extensibility.** Numerous interfaces without real variants can scatter logic. Good abstractions make intent and likely change directions easier to understand.

**Ignoring compatibility.** Replacing `String` with `enum` in the core may still require adapters preserving existing JSON/API or database representations. Internal refactoring does not automatically permit breaking public APIs.

## 10. Self-Assessment Exercises

**Exercise 1 — Recognition:** A `ReportManager` class has methods `loadFromDatabase()`, `calculateTax()`, `renderHtml()`, and `sendEmail()`. Identify independent reasons to change, possible smells, and appropriate class boundaries. Do not decide based on names alone; consider whether the methods always change together.

**Exercise 2 — Shotgun Surgery:** Changing VAT codes requires updating the same lookup table in three services. Identify one owner for VAT rules, design tests covering existing cases, and explain why future rule changes should occur there.

**Exercise 3 — Primitive Obsession:** The signature `createAccount(String type, String status, int level, boolean active)` allows conflicting state combinations. Propose an enum or value object that rules out invalid states. Give one case where a new class would be unnecessary.

**Exercise 4 — Safe refactoring:** In `DesignSmellsDemo`, rename `PricingRules` and extract `shippingFor`. Rerun tests to confirm unchanged output. Then deliberately change the free-shipping condition from `>=` to `>` and identify the test that detects the error at 100.000 cents.

**Exercise 5 — Prioritization:** Two areas of code exist: A has 800 lines but has not changed for 18 months; B has 150 lines, changes weekly, and often causes regressions. Choose which to evaluate first, describe the evidence to collect, and explain when the choice might differ.

### Completion Criteria

You can identify **a concrete symptom**, explain **why it creates cost or risk**, gather evidence from behavior and change history, select a clearly scoped refactoring, and write tests proving that Java contracts remain correct afterward. Recognizing a code smell is diagnostic; **design improvements must be validated by outcomes**, not by changed line or class counts.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-002">Renaming to Clarify Intent</a>

<details>
<summary>View details</summary>

# Renaming to Clarify Intent (Rename for Intent) in Java OOP

## How Can a Name Make Code Hard to Understand?

Consider a call in a sales application:

~~~java
long total = Calc.f(items, true);
~~~

The reader cannot tell what `f` does, which condition `true` enables, or which amounts `total` includes. To modify the code, they must inspect the implementation, trace variables, and infer the business rules.

Renaming to clarify intent is a **refactoring**: changing a class, method, field, variable, or constant name to express its actual purpose while **preserving the program's observable behavior**.

The same idea with clearer names:

~~~java
long payableAmountVnd =
        orderPriceCalculator.calculatePayableAmountVnd(
                orderLines, includeDeliveryFee);
~~~

The names now answer three questions: the value is **the payable amount**, the unit is **VND**, and the boolean controls **whether a delivery fee is included**.

> A good name reveals intent rather than just a data type. `orderLines` is more useful than `list`, `remainingStock` more useful than `number`, and `isPaymentApproved` more useful than `flag`.

## Names Worth Improving

| OOP element | Unclear name | Intention-revealing name | Why it helps |
|---|---|---|---|
| Class | `DataManager` | `OrderRepository` | Identifies responsibility for storing/retrieving orders |
| Method | `process()` | `confirmOrder()` | Names the business operation |
| Calculation method | `getTotal()` | `calculatePayableAmountVnd()` | Clarifies the calculation and its result |
| Field | `n` | `quantity` | Explains what the number represents |
| Collection | `data` | `unpaidInvoices` | Identifies the items and selection criterion |
| Boolean | `flag` | `includeDeliveryFee` | Explains what `true` means |
| Boolean state | `status` | `isOrderCancelled` | Reads naturally as a true/false condition |
| Constant | `LIMIT` | `MAX_LOGIN_ATTEMPTS` | Identifies the business limit |
| Unit | `timeout` | `connectionTimeoutMillis` | Avoids confusing milliseconds with seconds |
| Time point | `time` | `paymentExpiresAt` | Distinguishes a timestamp from a duration |

These examples are conditional: **the correct name must follow actual behavior**. Do not rename `DataManager` to `OrderRepository` if it manages many data types. Nor should `getTotal()` become `calculatePayableAmountVnd()` if it returns a product count rather than money.

## Runnable Refactoring Example: Before and After Renaming

The following program calculates line-item totals with an optional fixed delivery fee of 30.000 VND. The `Before` and `After` implementations coexist **only to demonstrate verification in this lesson**; in real code, keep one implementation after refactoring and remove old names when no API-compatibility requirement remains.

Save the following code as **RenameForIntentDemo.java** (Java 11 or later):

~~~java
import java.util.List;

public final class RenameForIntentDemo {
    private static final long DELIVERY_FEE_VND = 30_000L;

    static final class OrderLine {
        private final long unitPriceVnd;
        private final int quantity;

        OrderLine(long unitPriceVnd, int quantity) {
            if (unitPriceVnd < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid order line");
            }
            this.unitPriceVnd = unitPriceVnd;
            this.quantity = quantity;
        }

        long calculateLineAmountVnd() {
            return Math.multiplyExact(unitPriceVnd, (long) quantity);
        }
    }

    // BEFORE: phải đọc phần thân mới đoán được tên và biến có ý nghĩa gì.
    static final class Before {
        static long f(List<OrderLine> x, boolean y) {
            long z = 0L;
            for (OrderLine i : x) {
                z = Math.addExact(z, i.calculateLineAmountVnd());
            }
            if (y) {
                z = Math.addExact(z, DELIVERY_FEE_VND);
            }
            return z;
        }
    }

    // AFTER: chỉ đổi các định danh; điều kiện, phép tính và thứ tự giữ nguyên.
    static final class After {
        static long calculatePayableAmountVnd(
                List<OrderLine> orderLines,
                boolean includeDeliveryFee) {

            long payableAmountVnd = 0L;
            for (OrderLine orderLine : orderLines) {
                payableAmountVnd = Math.addExact(
                        payableAmountVnd,
                        orderLine.calculateLineAmountVnd());
            }
            if (includeDeliveryFee) {
                payableAmountVnd = Math.addExact(
                        payableAmountVnd, DELIVERY_FEE_VND);
            }
            return payableAmountVnd;
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(
                    "Expected " + expected + ", actual " + actual);
        }
    }

    public static void main(String[] args) {
        List<OrderLine> orderLines = List.of(
                new OrderLine(20_000L, 2),
                new OrderLine(55_000L, 1)
        );
        // Tiền hàng: 20.000 * 2 + 55.000 = 95.000 VND.

        long oldWithoutDelivery = Before.f(orderLines, false);
        long newWithoutDelivery =
                After.calculatePayableAmountVnd(orderLines, false);

        long oldWithDelivery = Before.f(orderLines, true);
        long newWithDelivery =
                After.calculatePayableAmountVnd(orderLines, true);

        assertEquals(95_000L, oldWithoutDelivery);
        assertEquals(oldWithoutDelivery, newWithoutDelivery);

        assertEquals(125_000L, oldWithDelivery);
        assertEquals(oldWithDelivery, newWithDelivery);

        // Kiểm tra một trường hợp biên: không có dòng hàng.
        assertEquals(Before.f(List.of(), false),
                After.calculatePayableAmountVnd(List.of(), false));

        System.out.println("Rename preserves behavior: PASS");
    }
}
~~~

Run:

~~~bash
javac RenameForIntentDemo.java
java RenameForIntentDemo
~~~

Output:

~~~text
Rename preserves behavior: PASS
~~~

The main changes within the method:

| Before | After | Purpose |
|---|---|---|
| `f` | `calculatePayableAmountVnd` | Calculates the amount due |
| `x` | `orderLines` | Collection of order lines |
| `y` | `includeDeliveryFee` | Whether to add the delivery fee |
| `z` | `payableAmountVnd` | Accumulated amount and result |
| `i` | `orderLine` | Current element in the iteration |

Both `Before` and `After` use the same delivery-fee constant, `Math.addExact`, and `Math.multiplyExact`. Therefore, the change under examination is **naming**, not calculations or overflow handling. The example checks a few specific cases; real projects should run the full suite, including important boundary cases.

## How Can Names Accurately Express Intent?

### 1. Method Names Should Express Actions or Questions

~~~java
// Mơ hồ: tên không thể hiện kết quả.
void handle(Order order);

// Rõ hơn nếu nghiệp vụ thực sự là xác nhận đơn.
void confirmOrder(Order order);

// Boolean method có thể đọc như một câu hỏi.
boolean isEligibleForFreeShipping(Order order);
~~~

A method named `validateOrder` needs a clear contract: does it return a `boolean`, a list of errors, or throw an exception? If it **throws on invalid input**, `requireValidOrder` may communicate that behavior more accurately; if it returns a `boolean`, `isOrderValid` may be clearer. Choose according to the actual contract.

For JavaBeans, JPA, serialization libraries, or getter-based frameworks, prefixes such as `get`, `is`, and `set` may be part of technical conventions. Renaming must respect those contracts and framework rules.

### 2. Data Names Should Convey Business Meaning and Units

~~~java
long delay = 5_000;                 // 5.000 giây hay mili giây?
long retryDelayMillis = 5_000;      // Mili giây.

int count = 3;                      // Đếm cái gì?
int failedLoginAttempts = 3;        // Số lần đăng nhập thất bại.
~~~

In new code, a domain type can reduce ambiguity further, for example `Duration.ofSeconds(5)`. However, **changing from `long` to `Duration` changes the data type**, not just the name; it should be a separate refactoring step with suitable tests.

### 3. Boolean Names Must Avoid Ambiguity

~~~java
boolean active;              // Ai đang hoạt động?
boolean userAccountActive;   // Tài khoản người dùng đang hoạt động.

boolean send;                // Gửi thứ gì, lúc nào?
boolean sendConfirmationEmail;
~~~

Avoid double negatives such as `isNotDisabled` when `isEnabled` expresses the intent directly. However, **reversing a flag's meaning** (changing `disabled` to `enabled` and inverting its value) is not a simple rename because `true` now means something different; all usage logic must be updated and thoroughly tested.

### 4. Balance Concision with Sufficient Context

`customerId` is better than `c`, but `customerIdOfTheCustomerCurrentlyBeingProcessed` is excessively long. In a short loop, `index` or `item` may be enough; in a method handling `orders`, `invoices`, and `receipts`, `currentOrder` is clearer than `item`.

The broader the scope (public classes, public APIs, long-lived fields), the more self-explanatory names should be. A short but contextually clear name is fine in a very local expression.

## A Safe Renaming Process in Java Projects

~~~mermaid
flowchart TD
    A["Hiểu mục đích và hợp đồng hiện tại"] --> B["Chọn tên diễn đạt nghiệp vụ đúng"]
    B --> C["Dùng IDE Rename Symbol để cập nhật tham chiếu"]
    C --> D["Xem diff: chỉ các định danh mong muốn thay đổi?"]
    D --> E["Compile và chạy unit tests"]
    E --> F{"Có API / dữ liệu / cấu hình phụ thuộc tên?"}
    F -- "Có" --> G["Kiểm tra consumer, reflection, serialization, migration"]
    F -- "Không" --> H["Hoàn tất refactor"]
    G --> H
~~~

1. **Read the method/class being renamed:** Understand its meaning, conditions, results, and side effects. Do not guess from its old name.
2. **Choose the new name:** Express the actual action, object, unit, or state and follow the project's domain vocabulary.
3. **Use the IDE's Rename refactoring:** IntelliJ IDEA commonly uses **Shift+F6**, and VS Code with Java tooling often uses **F2**. Symbol-aware reference updates are safer than global string replacement.
4. **Inspect the diff and call sites:** Review constructors, method references, overrides/implementations, tests, annotations, configuration, and documentation. Check multiple modules when the symbol crosses module boundaries.
5. **Compile and test:** Compare behavior before and after. For public APIs and persistence, verify consumers and real data contracts.
6. **Keep changes small:** Separating naming refactors from business-logic edits makes commits easier to review, debug, and revert (commit only when the project's workflow requires it).

IDE-assisted local-variable renaming is usually low risk. Renaming public methods, properties, or externally referenced classes may require a compatibility/migration plan.

## Important Boundary: Source Names May Differ from Runtime Names

| Scenario | What can break | Mitigation |
|---|---|---|
| Rename a method in a public interface | Other projects may fail to compile; binary clients may fail at linkage | Update consumers, version the API, or maintain adapters/deprecated methods as policy requires |
| Rename a field in a JSON payload | The API contract changes and clients expecting the old field fail | Preserve the wire name with annotations (such as `@JsonProperty`) or use versioned migration |
| Rename a JPA entity property | Mapping, property-based queries, or convention-driven schema names may change | Check `@Column`, derived queries, JPQL, migrations, and integration tests |
| Rename `@Bean`, `@Qualifier`, or a component | Name-based dependency injection may fail to resolve the right bean | Check bean configuration and context-startup tests |
| Rename a method/field referenced through reflection | Old string literals are not necessarily updated by Rename Symbol | Find reflection calls and names in config/tests; update and execute them |
| Rename a public file/class/package | Builds, imports, resources/configuration, or callers may break | Use IDE refactoring and inspect tests, build scripts, and external references |

Example of renaming a Java field while preserving a public JSON contract (in a project using Jackson):

~~~java
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PaymentResponse {
    private long payableAmountVnd;

    @JsonProperty("total")
    public long getPayableAmountVnd() {
        return payableAmountVnd;
    }
}
~~~

Here, the JSON name `"total"` is applied **to the getter** to preserve the serialized field name. The snippet only illustrates output serialization; deserialization also requires an appropriate constructor/setter or creator configuration. This is merely a mapping example: when a real application uses different setters, constructors, records, serialization strategies, or visibility rules, verify actual JSON input/output in tests. This Jackson import belongs in a project with the corresponding dependency and is **not part of the standalone Java file above**.

In Java, renaming an `@Override` method must remain consistent with the interface/superclass contract; when the interface comes from an external library, its signature cannot be changed arbitrarily while preserving correct implementation.

## When Is a Better Name Insufficient?

Sometimes the problem goes beyond naming. For example:

~~~java
void processOrder(Order order);
~~~

If a method checks inventory, charges a payment, updates an order, and sends email, renaming it `processValidatedOrderAndChargeAndSendEmail` still does not create clear responsibilities. Consider **Extract Method**, **Extract Class**, or responsibility-based collaborators with separately named steps. These are different refactorings and should be assessed and tested separately.

Likewise, `calculatePayableAmountVnd(orderLines, true)` still forces a reader at the **call site** to remember what `true` means. Good parameter names improve declarations, but Java lacks named arguments. For more complex cases, consider an explicit method or an enum representing the business choice. Changing a signature or introducing an enum is a separate API change, not merely a rename.

## Self-Check Exercises

**Exercise 1 — Name the intent:** After checking actual behavior, propose meaningful names for the following snippets:

~~~java
boolean a = invoice.getDueDate().isBefore(today) && !invoice.isPaid();
int n = cart.getItems().size();
long t = priceCents * quantity;
~~~

Suggested names: `isInvoiceOverdue` (if the business rule requires both overdue and unpaid), `cartItemCount`, and `lineAmountCents`.

**Exercise 2 — Preserve behavior:** In `RenameForIntentDemo.java`, rename `After.calculatePayableAmountVnd` to `calculateOrderPayableAmountVnd` using Rename Symbol, verify that the call in `main` changes, and rerun the program. The console must still print `PASS`.

**Exercise 3 — Identify risks:** An API returns JSON with the field `"total"`. Propose a way to rename the Java field to `payableAmountVnd` while existing clients still receive `"total"`; write a serialization test if the project uses Jackson.

**Self-assessment:** Explain why changing `flag` to `includeDeliveryFee` is renaming, whereas replacing `boolean includeDeliveryFee` with `ShippingMethod shippingMethod` redesigns the contract. Identify tests proving that totals remain unchanged.

A successful rename lets readers understand **what an object does, what its data means, and what a condition represents** right where the name is used; code retains its expected behavior, and contracts beyond source code have been considered.

</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-003">Extracting Methods and Classes</a>

<details>
<summary>View details</summary>

# Extracting Methods and Classes (Extract Method / Extract Class) in Java OOP

## 1. Why Extract Code?

As a method grows, it often accumulates multiple jobs: validating data, calculating values, updating state, generating notifications, and calling external services. When a class contains too many distinct responsibility groups, even a small change forces developers to read, modify, and test the entire class.

**Refactoring** improves internal structure while **preserving observable behavior**. Two foundational techniques are:

- **Extract Method:** Select a cohesive block of statements, move it into a method whose name expresses its purpose, and replace the original block with a call to that method.
- **Extract Class:** Move related data and behavior with a distinct responsibility into a new class; the original class delegates through a clear API.

The techniques can be combined: first extract steps from a long method, then identify methods that belong to another class's responsibility and move them there.

**Expected outcome:** More readable code, clearer responsibilities, and easier reuse and testing, while preserving promised return values, exceptions, side effects, and business rules.

| Criterion | Extract Method | Extract Class |
|---|---|---|
| Signal | A method performs several semantically distinct steps | A class has several groups of reasons to change |
| Action | Move code into a clearly named method | Move related fields/methods into another object |
| Scale | Mostly reorganizes a class's internals | Redistributes responsibilities among objects |
| Example | Extract calculateSubtotal and applyDiscount | Extract PricingPolicy and ReceiptFormatter from CheckoutService |
| Common pitfalls | Too many trivial methods, excessive parameters, changed side-effect order | Meaningless classes, exposed data, circular dependencies, excessive fragmentation |

## 2. Signals That Extraction May Help

Useful signals, not absolute rules:

1. Understanding one business operation requires reading many lines.
2. Different blocks in the same method have distinct purposes: *validate → calculate → format → persist*.
3. One class changes for unrelated reasons, such as changes to pricing rules **or** invoice presentation.
4. Several methods share a group of fields but hardly use the rest of the class; they form a distinct cohesive group.
5. Testing one small rule requires constructing many unrelated dependencies.

Length is only a **signal**. A clear 40-line method describing a linear algorithm may be easier to maintain than 10 fragmented methods. Aim for greater **cohesion**, lower coupling, and clear business meaning and data flow.

## 3. Extract Method: A Step-by-Step Approach

Suppose a checkout method contains all order logic:

~~~java
// Phác thảo cấu trúc ban đầu:
CheckoutResult checkout(Order order) {
    // (1) Kiểm tra đơn hàng
    // (2) Cộng giá trị từng dòng
    // (3) Tính giảm giá VIP
    // (4) Tính phí vận chuyển
    // (5) Định dạng chuỗi biên nhận
    // (6) Trả kết quả
}
~~~

We can name the individual calculation steps:

~~~java
// Phác thảo sau Extract Method:
CheckoutResult checkout(Order order) {
    long subtotal = calculateSubtotal(order);
    long discount = calculateDiscount(subtotal, order.vip);
    long payable = Math.subtractExact(subtotal, discount);
    long shipping = calculateShipping(payable);
    long total = Math.addExact(payable, shipping);
    String receipt = formatReceipt(subtotal, discount, shipping, total);
    return new CheckoutResult(
            subtotal, discount, shipping, total, receipt);
}
~~~

Methods such as calculateDiscount or calculateShipping can remain private with clear inputs and outputs. If a block reads or modifies too many local variables, determine which values it actually needs and returns rather than turning locals into shared fields accessible from everywhere.

Safe Extract Method procedure:

1. Run tests or establish existing input/output scenarios as a behavioral baseline.
2. Select a block with a distinct purpose; name it after **what it does**, not merely the technical operation.
3. Identify read variables, updated variables, return values, exceptions, and side effects.
4. Create the method, move the exact block, pass required dependencies as parameters, and replace the original code with a call.
5. Compile and rerun tests immediately after each small step.
6. Extract more only if intent becomes clearer; stop when extraction makes the flow harder to trace.

For example, **calculateShipping** expresses intent better than **processStep4**. Avoid passing a large object to use one field when a specific value makes the contract clearer; also avoid 10-parameter lists by choosing a parameter object that genuinely represents a concept.

## 4. Extract Class: Separate Responsibilities

After extracting methods, suppose CheckoutService has two groups:

- **Pricing:** Subtotals, VIP discounts, shipping fees, and totals.
- **Presentation:** Formatting receipts for display.

These groups change for different reasons. Move the first to **PricingPolicy**, the second to **ReceiptFormatter**, and retain **CheckoutService** as the coordinator.

~~~mermaid
flowchart TB
    subgraph BEFORE["Trước"]
        A["CheckoutService: kiểm tra + tính giá + định dạng"]
    end
    subgraph AFTER["Sau"]
        B["CheckoutService: điều phối"] --> C["PricingPolicy: quy tắc tiền"]
        B --> D["ReceiptFormatter: trình bày"]
    end
~~~

This applies the **Single Responsibility Principle (SRP)**: each class should have one coherent group of reasons to change. SRP does not mean exactly one method per class. Separation can also encourage abstraction-based dependencies when implementations need substitution, but not every class requires an interface.

## 5. Complete Java Program: Comparing Before and After Refactoring

Both versions preserve the same business requirements:

- An order has at least one line. SKU is nonblank, quantities are positive, and unit prices are nonnegative.
- **subtotal** is the sum of unit price multiplied by quantity; integer VND avoids floating-point inaccuracies.
- VIP customers with subtotals of at least **500.000 VND** get a **10% subtotal** discount, rounded down using integer division.
- Shipping is **0 VND** when the post-discount amount reaches **700.000 VND**; otherwise it is **30.000 VND**.
- **total = subtotal − discount + shipping**.
- Receipts use the fixed string format shown in the example.
- Calculations use Math.addExact, multiplyExact, and subtractExact so overflows do not silently alter results.

Save everything as **OrderRefactoringDemo.java**. This standalone **Java 11+** program has no external dependencies. LegacyCheckout combines business logic in one method; the refactored version separates PricingPolicy, ReceiptFormatter, and CheckoutService.

~~~java
import java.util.List;
import java.util.Objects;

public class OrderRefactoringDemo {
    static final long VIP_MIN_SUBTOTAL = 500_000L;
    static final long FREE_SHIPPING_MIN = 700_000L;
    static final long SHIPPING_FEE = 30_000L;

    static final class Item {
        final String sku;
        final long unitPriceVnd;
        final int quantity;

        Item(String sku, long unitPriceVnd, int quantity) {
            this.sku = sku;
            this.unitPriceVnd = unitPriceVnd;
            this.quantity = quantity;
        }
    }

    static final class Order {
        final List<Item> items;
        final boolean vip;

        Order(List<Item> items, boolean vip) {
            this.items = items;
            this.vip = vip;
        }
    }

    static final class CheckoutResult {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;
        final String receipt;

        CheckoutResult(long subtotal, long discount, long shipping,
                       long total, String receipt) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
            this.receipt = receipt;
        }
    }

    // TRƯỚC: một phương thức phải xử lý nhiều trách nhiệm.
    static final class LegacyCheckout {
        CheckoutResult checkout(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.unitPriceVnd < 0 || item.quantity <= 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineTotal = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineTotal);
            }

            long discount = order.vip && subtotal >= VIP_MIN_SUBTOTAL
                    ? subtotal / 10 : 0L;
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = payable >= FREE_SHIPPING_MIN
                    ? 0L : SHIPPING_FEE;
            long total = Math.addExact(payable, shipping);

            String receipt = "subtotal=" + subtotal
                    + ",discount=" + discount
                    + ",shipping=" + shipping
                    + ",total=" + total;

            return new CheckoutResult(
                    subtotal, discount, shipping, total, receipt);
        }
    }

    // KẾT QUẢ TÍNH TOÁN: dữ liệu cho lớp trình bày.
    static final class Pricing {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;

        Pricing(long subtotal, long discount, long shipping, long total) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
        }
    }

    // Extract Class: gom các quy tắc định giá liên quan.
    static final class PricingPolicy {
        Pricing calculate(Order order) {
            long subtotal = calculateSubtotal(order);
            long discount = calculateDiscount(subtotal, order.vip);
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = calculateShipping(payable);
            long total = Math.addExact(payable, shipping);

            return new Pricing(subtotal, discount, shipping, total);
        }

        // Extract Method: đọc và kiểm tra dữ liệu, rồi cộng tiền.
        private long calculateSubtotal(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.unitPriceVnd < 0 || item.quantity <= 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineTotal = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineTotal);
            }
            return subtotal;
        }

        private long calculateDiscount(long subtotal, boolean vip) {
            return vip && subtotal >= VIP_MIN_SUBTOTAL
                    ? subtotal / 10 : 0L;
        }

        private long calculateShipping(long payable) {
            return payable >= FREE_SHIPPING_MIN ? 0L : SHIPPING_FEE;
        }
    }

    // Extract Class: cách trình bày thay đổi độc lập với quy tắc giá.
    static final class ReceiptFormatter {
        String format(Pricing pricing) {
            return "subtotal=" + pricing.subtotal
                    + ",discount=" + pricing.discount
                    + ",shipping=" + pricing.shipping
                    + ",total=" + pricing.total;
        }
    }

    // SUT sau refactor: đọc như một quy trình điều phối.
    static final class CheckoutService {
        private final PricingPolicy pricingPolicy;
        private final ReceiptFormatter formatter;

        CheckoutService(PricingPolicy pricingPolicy,
                        ReceiptFormatter formatter) {
            this.pricingPolicy = Objects.requireNonNull(pricingPolicy);
            this.formatter = Objects.requireNonNull(formatter);
        }

        CheckoutResult checkout(Order order) {
            Pricing pricing = pricingPolicy.calculate(order);
            return new CheckoutResult(
                    pricing.subtotal,
                    pricing.discount,
                    pricing.shipping,
                    pricing.total,
                    formatter.format(pricing));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkEquivalent(
            LegacyCheckout oldVersion,
            CheckoutService newVersion,
            Order input,
            long expectedTotal) {
        CheckoutResult oldResult = oldVersion.checkout(input);
        CheckoutResult newResult = newVersion.checkout(input);

        check(oldResult.subtotal == newResult.subtotal, "subtotal changed");
        check(oldResult.discount == newResult.discount, "discount changed");
        check(oldResult.shipping == newResult.shipping, "shipping changed");
        check(oldResult.total == newResult.total, "total changed");
        check(oldResult.receipt.equals(newResult.receipt),
                "receipt changed");
        check(newResult.total == expectedTotal, "incorrect total");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        LegacyCheckout oldVersion = new LegacyCheckout();
        CheckoutService newVersion =
                new CheckoutService(new PricingPolicy(),
                                    new ReceiptFormatter());

        // subtotal = 600.000; VIP giảm 60.000;
        // sau giảm 540.000; phí 30.000 => 570.000.
        Order vipOrder = new Order(List.of(
                new Item("BOOK", 250_000L, 2),
                new Item("PEN", 100_000L, 1)), true);
        checkEquivalent(oldVersion, newVersion, vipOrder, 570_000L);

        // Không VIP: 100.000 + phí 30.000 = 130.000.
        Order normalOrder = new Order(
                List.of(new Item("PEN", 100_000L, 1)), false);
        checkEquivalent(oldVersion, newVersion, normalOrder, 130_000L);

        // VIP: subtotal 800.000, giảm 80.000,
        // sau giảm 720.000, miễn phí vận chuyển.
        Order freeShippingOrder = new Order(
                List.of(new Item("BOOK", 400_000L, 2)), true);
        checkEquivalent(oldVersion, newVersion,
                freeShippingOrder, 720_000L);

        // Cả hai phiên bản từ chối quantity không hợp lệ.
        Order invalid = new Order(
                List.of(new Item("BOOK", 100_000L, 0)), false);
        expectInvalid(() -> oldVersion.checkout(invalid));
        expectInvalid(() -> newVersion.checkout(invalid));

        System.out.println("3 equivalent cases + invalid input: PASS");
    }
}
~~~

Compile and run:

~~~bash
javac OrderRefactoringDemo.java
java OrderRefactoringDemo
~~~

Expected output:

~~~text
3 equivalent cases + invalid input: PASS
~~~

This example **compares more than totals**: it checks subtotal, discount, shipping, total, and receipt strings, and verifies that both versions reject the same invalid input categories. For refactorings with side effects or external systems, add tests for observable effects at those boundaries.

**Example limitations:** The program models pure in-memory pricing without repositories, transactions, email delivery, or high volume. In real applications, test database state, meaningful side-effect ordering, thread safety, and integration failures before claiming behavior is preserved.

## 6. Identifying Responsibilities After Extraction

| Component | Current responsibility | Possible reason to change |
|---|---|---|
| Order / Item | Order and line-item data | Data contracts |
| PricingPolicy | Input validation and pricing, discounts, fees | Sales policies |
| Pricing | Calculated pricing result | Pricing data structure |
| ReceiptFormatter | Convert pricing results to a string | Display requirements |
| CheckoutService | Invoke PricingPolicy, then ReceiptFormatter | Coordinated business workflow |

Passing PricingPolicy and ReceiptFormatter through a constructor is simple **dependency injection**. Spring is not required. If multiple pricing/formatting variants are needed, consider matching interfaces; with just one implementation, concrete classes may be sufficient.

**When should a method stay in its original class?** When it uses most of that class's state and closely serves its core responsibility. Extract Class should move genuinely related behavior and data together rather than create a "Utils" class containing unrelated static methods.

## 7. Risks to Examine Before Declaring a Refactoring Successful

### Changing Execution Order or External Effects

Moving methods in the pure calculation example carries little risk. However, changing the order *charge payment → decrement inventory → log/send email* can alter business behavior. Refactorings involving transactions, locks, retries, or events must preserve promised invariants and transaction boundaries.

### Changing When Exceptions Are Thrown

Moving validation to another class may cause errors to surface earlier or later. For example, if the first line overflows and a later line is invalid, validating the entire cart up front changes the observable exception. Where exceptions or side effects are contractual, preserve ordering and write appropriate tests before moving logic.

### Exposing Data Internals

Do not make private fields public merely to let a new class access them. Use immutable value objects, meaningful query methods, or transfer state ownership to the responsible class.

### Introducing Circular Dependencies

It makes sense for CheckoutService to depend on PricingPolicy; if PricingPolicy must call back into CheckoutService, the responsibility split may be unclear. Favor one-way dependency direction rather than mutual references just to access helpers.

### Extracting Too Much

A class containing a single forwarding line is not necessarily valuable. Making every addition a class or method increases fragmentation. Extract according to business meaning, cohesion, and reasons to change—not to maximize file counts.

### Accidentally Changing Performance or Data Flow

Moving a method can introduce collection copies, caches with different lifetimes, or additional dependency calls. If performance, identity, ordering, concurrency, or idempotency are contractual, add corresponding checks. Watch Java reference semantics, mutable objects, and data races when moving state into shared classes.

## 8. Testing Before and After Refactoring

A practical sequence:

1. **Characterization tests:** Record existing behavior in important scenarios, especially where tests are missing.
2. **Small Extract Method steps:** Move one block and rerun the related tests.
3. **Responsibility-based Extract Class:** Move methods/fields and update dependencies in constructors and callers.
4. **Check equivalence:** Compare results, exceptions, state, and meaningful side effects.
5. **Run unit and integration tests:** Validate database, API, and transaction boundaries against real integrations where needed.
6. **Review diff scope:** Separate business-policy changes from refactoring to make regressions easier to detect.

When existing code has known defects, do not silently change behavior while claiming a "pure refactoring." Fix the bug separately with tests specifying the new expected behavior.

For example, if a pricing policy must change from a 10% discount to 15%, make that change **after** a behavior-preserving refactoring and test it separately. When a commit both extracts classes and changes rules, a failing test is harder to diagnose.

## 9. Practice Exercises

**Exercise 1 — Extract Method:** In LegacyCheckout, extract subtotal, discount, shipping-fee, and formatting calculations. Rerun the same tests after each step and explain each method's inputs and outputs.

**Exercise 2 — Extract Class:** Move the ReceiptFormatter responsibility separately and verify that changing output formatting does not require editing PricingPolicy. Explain how this improves cohesion.

**Exercise 3 — Boundary cases:** Add tests for VIP subtotals of exactly 500.000, payable amounts of exactly 700.000, empty orders, negative prices, quantities of 0, and values exceeding the long limit. Run both versions to check equivalence.

**Exercise 4 — Detect behavior changes:** Deliberately calculate shipping on the pre-discount subtotal instead of the post-discount payable amount. Construct an input where the results differ so a test catches the regression.

**Exercise 5 — Redesign:** If discount policies vary by country or customer type, consider a DiscountPolicy interface. Explain when Strategy is preferable to adding more if branches in PricingPolicy and when an interface would be premature.

## 10. Conclusion

**Extract Method** gives meaningful names to steps so the main method reads more clearly. **Extract Class** groups data and behavior by responsibility so components can evolve more independently. Both qualify as refactorings only when **the observable contract remains unchanged**. Make small, domain-named changes, verify before and after, and stop when the separation improves understanding.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-004">Moving Behavior to the Responsible Object</a>

<details>
<summary>View details</summary>

# Moving Behavior to the Responsible Object in Java OOP

## 1. The Problem: Data Lives in One Class, Behavior in Another

In object-oriented programming, objects should be responsible for behavior directly related to the state and rules they own. A warning sign appears when class A repeatedly retrieves class B's data and performs calculations or decisions that naturally belong to B.

For example, a checkout service iterates over order lines, retrieves unit prices and quantities, multiplies them, adds totals, and applies an order discount. It knows too much about the order's internal structure.

This code smell is commonly called **Feature Envy**: a method is excessively interested in another object's data. A suitable refactoring is often **Move Method — relocating behavior to the object that owns the relevant information and rules**. Sometimes a large method must first be split into smaller behaviors that can move to different objects.

The goals are:
- Move line amount calculations to **OrderLine**, which knows the unit price and quantity.
- Move the total and fixed order-level discount rule to **Order**, which owns the line items and discount information.
- Keep payment-gateway calls in **CheckoutService**, whose responsibility is coordinating external interactions.
- Preserve results, errors, and tested contracts throughout the refactoring.

**There is no rule that "every method must live in the class storing the data."** Place behavior according to domain responsibility, cohesion, coupling, and expected changes.

## 2. Examining the Design Before Refactoring

The following simplified example uses two legacy-style data classes. All pricing logic lives in a service:

~~~java
import java.util.List;

record LegacyLine(String sku, long priceVnd, int quantity) {}

record LegacyOrder(
        String id,
        boolean vip,
        List<LegacyLine> lines) {}

final class LegacyCheckoutService {
    long calculatePayable(LegacyOrder order) {
        long subtotal = 0;

        for (LegacyLine line : order.lines()) {
            subtotal += line.priceVnd() * line.quantity();
        }

        if (order.vip()) {
            return subtotal - subtotal / 10;
        }
        return subtotal;
    }
}
~~~

This intentionally simplified code illustrates responsibility placement; it does not yet validate data or detect overflow. The example rule grants VIP customers **10% of the subtotal** as a discount, rounded down to whole VND.

The issue is not loop syntax. LegacyCheckoutService knows how to price line items, sum an order, and apply discounts. When OrderLine changes its calculation or the order gains a rule, the service must change even though its primary role should be checkout coordination.

A common symptom is a chain of data queries:

~~~text
service → order.lines() → line.priceVnd()
                        → line.quantity()
                        → tính toán ngoài đối tượng dữ liệu
~~~

Desired design:

~~~text
CheckoutService → Order.payableVnd()
                      ├── Order.subtotalVnd()
                      │      └── OrderLine.subtotalVnd()
                      └── Quy tắc giảm giá của đơn
~~~

The difference is that the caller asks **the object to do its own work** instead of retrieving its data and doing that work itself. This is often described by *Tell, Don't Ask*.

## 3. Identifying the Right Owner for Behavior

| Behavior | Appropriate owner | Why |
| --- | --- | --- |
| Multiply unit price by quantity | OrderLine | Both data fields and line-item invariants live there |
| Sum order lines | Order | Order owns the list of OrderLine objects |
| Calculate amount due under the order's fixed VIP discount | Order | The rule is tied to the order's VIP state in this example |
| Invoke a payment API | CheckoutService / PaymentGateway | Side effects belong to process coordination and infrastructure |
| Export an invoice as PDF or JSON | Formatter / Presenter / Serializer | External representation is distinct from pricing rules |
| Calculate campaign/coupon discounts with complex variations | A dedicated DiscountPolicy or PricingService | Those rules have lifecycles and data sources independent of Order |

**Key question:** Which object needs the least external knowledge while owning enough information and responsibility to perform the behavior correctly? If behavior spans several aggregates or performs I/O, a domain/application service may be more appropriate than an entity method.

~~~mermaid
classDiagram
    class OrderLine {
        -String sku
        -long unitPriceVnd
        -int quantity
        +subtotalVnd() long
    }
    class Order {
        -String id
        -boolean vip
        -List~OrderLine~ lines
        +subtotalVnd() long
        +payableVnd() long
    }
    class CheckoutService {
        -PaymentGateway gateway
        +checkout(Order) boolean
    }
    class PaymentGateway {
        <<interface>>
        +charge(String, long) boolean
    }
    Order "1" *-- "1..*" OrderLine
    CheckoutService --> Order : yêu cầu số tiền
    CheckoutService --> PaymentGateway : yêu cầu thanh toán
~~~

Here, Order–OrderLine expresses ownership in the domain model. Order retains an immutable copy of the list so callers cannot modify its contents through the list supplied to the constructor.

## 4. After Refactoring: Runnable Java 17 Example

The example uses JDK 17, Maven, and JUnit 5. Each public class belongs in its own file.

~~~text
move-behavior-oop/
├── pom.xml
└── src/
    ├── main/java/vn/example/
    │   ├── OrderLine.java
    │   ├── Order.java
    │   ├── PaymentGateway.java
    │   ├── CheckoutService.java
    │   └── Main.java
    └── test/java/vn/example/
        ├── OrderTest.java
        └── CheckoutServiceTest.java
~~~

### 4.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>vn.example</groupId>
    <artifactId>move-behavior-oop</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Move Line-Item Calculations into OrderLine

**src/main/java/vn/example/OrderLine.java**

~~~java
package vn.example;

/** Một dòng hàng có số lượng dương và đơn giá không âm. */
public record OrderLine(String sku, long unitPriceVnd, int quantity) {
    public OrderLine {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (unitPriceVnd < 0) {
            throw new IllegalArgumentException(
                    "unitPriceVnd must not be negative");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
    }

    /** Thành tiền theo VND; báo lỗi nếu vượt miền long. */
    public long subtotalVnd() {
        return Math.multiplyExact(unitPriceVnd, (long) quantity);
    }
}
~~~

Important details:

- The record preserves immutable state (record components are final).
- The quantity > 0 and unitPriceVnd >= 0 rules are enforced when creating a line item.
- OrderLine answers "what is this line item's amount?" without another class fetching its fields and multiplying them.
- Math.multiplyExact throws ArithmeticException when a product exceeds the long range; ordinary multiplication can silently wrap into positive or negative values.

We allow a unit price of 0 for complimentary items; this is **a contract choice** in the example, not a universal rule.

### 4.3. Move Totals and the VIP Rule into Order

**src/main/java/vn/example/Order.java**

~~~java
package vn.example;

import java.util.List;
import java.util.Objects;

public final class Order {
    private final String id;
    private final boolean vip;
    private final List<OrderLine> lines;

    public Order(String id, boolean vip, List<OrderLine> lines) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("order must have lines");
        }

        this.id = id;
        this.vip = vip;
        // Tạo snapshot bất biến; List.copyOf cũng từ chối phần tử null.
        this.lines = List.copyOf(lines);
    }

    public String id() {
        return id;
    }

    public long subtotalVnd() {
        long result = 0L;
        for (OrderLine line : lines) {
            result = Math.addExact(result, line.subtotalVnd());
        }
        return result;
    }

    /** VIP giảm 10%; tiền giảm làm tròn xuống VND. */
    public long payableVnd() {
        long subtotal = subtotalVnd();
        long discount = vip ? subtotal / 10 : 0L;
        return subtotal - discount;
    }
}
~~~

Order coordinates its line items to determine **its own value**. CheckoutService does not need a publicly exposed lines list to iterate. Before adding getters that expose internal state, consider their purpose: query APIs for display can be legitimate, but they should not encourage duplicating pricing rules in other classes.

In payableVnd(), integer division rounds the discount down. For a subtotal of 99 VND, VIP discount is 9 VND, leaving 90 VND due. If business rules adopt different rounding, tests should explicitly cover the change.

### 4.4. Keep Coordination in CheckoutService

**src/main/java/vn/example/PaymentGateway.java**

~~~java
package vn.example;

@FunctionalInterface
public interface PaymentGateway {
    /** true nếu chấp nhận thanh toán, false nếu từ chối. */
    boolean charge(String orderId, long amountVnd);
}
~~~

**src/main/java/vn/example/CheckoutService.java**

~~~java
package vn.example;

import java.util.Objects;

public final class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public boolean checkout(Order order) {
        Objects.requireNonNull(order, "order");
        return gateway.charge(order.id(), order.payableVnd());
    }
}
~~~

CheckoutService retains one clear responsibility: coordinating payment. It contains no multiplication, subtotal loops, or VIP branches. In unit tests, a different PaymentGateway implementation can be injected instead of a real payment integration.

**src/main/java/vn/example/Main.java**

~~~java
package vn.example;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Order order = new Order(
                "DH-100",
                true,
                List.of(
                        new OrderLine("BOOK", 100_000, 2),
                        new OrderLine("PEN", 30_000, 1)));

        PaymentGateway demoGateway = (id, amount) -> {
            System.out.println(
                    "Payment request: " + id + " = " + amount + " VND");
            return true;
        };

        CheckoutService service = new CheckoutService(demoGateway);

        System.out.println("Subtotal: " + order.subtotalVnd());
        System.out.println("Payable: " + order.payableVnd());
        System.out.println("Accepted: " + service.checkout(order));
    }
}
~~~

Run from the project directory:

~~~bash
mvn test
mvn -q -DskipTests package
java -cp target/classes vn.example.Main
~~~

Expected output:

~~~text
Subtotal: 230000
Payable: 207000
Payment request: DH-100 = 207000 VND
Accepted: true
~~~

In the demo, PaymentGateway only prints information and returns true; it does not perform a real payment. Order determines the amount payable, while the coordinator communicates only with the payment gateway.

## 5. Writing Tests to Protect Behavior When Moving Methods

True refactoring preserves externally observable behavior under **the chosen contract**. Before moving code in an existing project, establish characterization tests for current values and branches. Object-level and workflow-level tests must continue passing after the move.

**src/test/java/vn/example/OrderTest.java**

~~~java
package vn.example;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {
    @Test
    void subtotalComesFromOrderLines() {
        Order order = new Order("O-1", false, List.of(
                new OrderLine("A", 100_000, 2),
                new OrderLine("B", 30_000, 1)));

        assertEquals(230_000L, order.subtotalVnd());
        assertEquals(230_000L, order.payableVnd());
    }

    @Test
    void vipDiscountIsAppliedOnceAtOrderLevel() {
        Order order = new Order("O-2", true, List.of(
                new OrderLine("A", 100_000, 2),
                new OrderLine("B", 30_000, 1)));

        assertEquals(230_000L, order.subtotalVnd());
        assertEquals(207_000L, order.payableVnd());
    }

    @Test
    void fractionalDiscountIsRoundedDownInVnd() {
        Order order = new Order("O-3", true, List.of(
                new OrderLine("A", 99, 1)));

        assertEquals(99L, order.subtotalVnd());
        assertEquals(90L, order.payableVnd());
    }

    @Test
    void orderDoesNotChangeWhenInputListChanges() {
        List<OrderLine> input = new ArrayList<>();
        input.add(new OrderLine("A", 100, 1));

        Order order = new Order("O-4", false, input);
        input.add(new OrderLine("B", 200, 1));

        assertEquals(100L, order.payableVnd());
    }

    @Test
    void validatesItsOwnInvariants() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderLine("X", -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderLine("X", 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderLine(" ", 10, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Order("O-5", false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Order("", false,
                        List.of(new OrderLine("X", 1, 1))));
    }

    @Test
    void rejectsOverflowInsteadOfSilentlyWrapping() {
        OrderLine line = new OrderLine("X", Long.MAX_VALUE, 2);
        assertThrows(ArithmeticException.class, line::subtotalVnd);

        Order order = new Order("O-6", false, List.of(
                new OrderLine("A", Long.MAX_VALUE, 1),
                new OrderLine("B", 1, 1)));

        assertThrows(ArithmeticException.class, order::subtotalVnd);
    }
}
~~~

**src/test/java/vn/example/CheckoutServiceTest.java**

~~~java
package vn.example;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {
    @Test
    void passesComputedAmountToGatewayExactlyOnce() {
        List<String> calls = new ArrayList<>();
        PaymentGateway gateway = (id, amount) -> {
            calls.add(id + ":" + amount);
            return true;
        };

        Order order = new Order("DH-100", true, List.of(
                new OrderLine("BOOK", 100_000, 2),
                new OrderLine("PEN", 30_000, 1)));

        CheckoutService service = new CheckoutService(gateway);

        assertTrue(service.checkout(order));
        assertEquals(List.of("DH-100:207000"), calls);
    }

    @Test
    void preservesPaymentRejection() {
        List<Long> charged = new ArrayList<>();
        PaymentGateway gateway = (id, amount) -> {
            charged.add(amount);
            return false;
        };

        Order order = new Order("DH-101", false, List.of(
                new OrderLine("PEN", 30_000, 2)));

        assertFalse(new CheckoutService(gateway).checkout(order));
        assertEquals(List.of(60_000L), charged);
    }

    @Test
    void nullOrderIsRejectedBeforeCallingGateway() {
        List<String> calls = new ArrayList<>();
        PaymentGateway gateway = (id, amount) -> {
            calls.add(id);
            return true;
        };

        CheckoutService service = new CheckoutService(gateway);

        assertThrows(NullPointerException.class,
                () -> service.checkout(null));
        assertTrue(calls.isEmpty());
    }
}
~~~

These are **9 @Test methods** (6 for Order and 3 for CheckoutService). With the Maven structure above, the suite is expected to run 9 tests without failures/errors.

Order-level tests show that responsibilities moved to the right place and calculations remain correct. CheckoutService tests confirm that the external workflow still uses the right amount and order ID and avoids gateway calls in invalid cases. Tests need no access to Order's private fields.

| Test | Responsibility verified | Possible defect detected |
| --- | --- | --- |
| subtotalComesFromOrderLines | Order sums amounts from OrderLine | Missing a line or multiplying by the wrong quantity |
| vipDiscountIsAppliedOnceAtOrderLevel | Apply the 10% discount once to the whole order | Duplicate discount or wrong scope |
| fractionalDiscountIsRoundedDownInVnd | Preserve contractual rounding | A 1 VND boundary error |
| orderDoesNotChangeWhenInputListChanges | Encapsulation and immutable snapshots | External changes modifying the order |
| validatesItsOwnInvariants | Invariants enforced by the data owner | Invalid data reaching later workflow stages |
| rejectsOverflowInsteadOfSilentlyWrapping | Overflow-checked calculations | Negative line amounts from overflow |
| passesComputedAmountToGatewayExactlyOnce | Preserve coordination after refactoring | Sending an old amount or invoking the gateway repeatedly |
| preservesPaymentRejection | Preserve result semantics | Changing false into true |
| nullOrderIsRejectedBeforeCallingGateway | Avoid side effects for null input | Calling dependencies with invalid data |

## 6. Safe Refactoring Steps in an Existing System

Use small, verifiable changes:

1. **Locate the behavior.** Find methods that read many fields on another object. Determine which data drives their calculations/decisions and whether they perform I/O.
2. **Establish the old contract.** Write or preserve tests for normal cases, boundaries, errors, rounding, and important callers. For monetary calculations, test overflow and currency units.
3. **Create a method on the target object.** Start with OrderLine.subtotalVnd(), letting the old method delegate to it so each move can be verified.
4. **Move aggregation logic.** Add Order.subtotalVnd() and then Order.payableVnd(). Remove pricing calculations from the coordinator.
5. **Update callers.** Have CheckoutService ask order.payableVnd() instead of iterating over order.lines() and checking VIP state.
6. **Test each step.** Preserve results, interaction counts, exceptions, and rounding rules throughout the move.
7. **Retire old entry points.** For public APIs, temporarily retain a delegating adapter during migration; remove duplicate calculations once callers have migrated and compatibility no longer requires them. For private APIs, delete them after confirming there are no callers.
8. **Reassess the design.** Check coupling, cohesion, object lifecycles, data mutation ownership, and whether the target method really understands the business rule.

Prefer incremental steps over moving a large code block at once. Java IDEs support *Move Instance Method*, but automated tools do not fully understand business boundaries: tests and API review remain necessary.

~~~mermaid
flowchart TD
    A["Phát hiện Feature Envy"] --> B["Xác định dữ liệu và chủ sở hữu quy tắc"]
    B --> C["Chốt test hành vi hiện tại"]
    C --> D["Chuyển phép tính nhỏ vào OrderLine"]
    D --> E["Chuyển tổng hợp vào Order"]
    E --> F["Đổi CheckoutService thành điều phối"]
    F --> G["Chạy test và rà soát caller"]
    G --> H{"Giữ hợp đồng?"}
    H -->|Không| I["Sửa sai lệch, kiểm tra biên"]
    I --> G
    H -->|Có| J["Dọn phương thức trùng và kết thúc"]
~~~

## 7. Connections to OOP Principles

**Encapsulation.** An object can protect invariants by operating on data it owns. Callers need not know whether line items are stored in an ArrayList, immutable structure, or lazy-loaded collection.

**High Cohesion.** Line amounts are calculated beside unit prices and quantities. Order totals belong with the complete order. Related changes tend to remain localized in the appropriate class.

**Low Coupling.** CheckoutService depends only on Order's concise business API—id() and payableVnd()—rather than individual line-item getters and VIP rules. Moving many lines to another object without reducing how much data callers must inspect does not meaningfully reduce coupling.

**Single Responsibility Principle (SRP).** Changes to line-item values belong to OrderLine, basic order-total rules belong to Order, and payment-workflow changes belong to CheckoutService. SRP does not mean one method per class.

**Information Expert (a GRASP principle).** Assign responsibility to an object with the information needed to perform it. This guides method placement but must be balanced against transaction, security, authorization, performance, and domain boundaries.

**Tell, Don't Ask.** Invoke meaningful behavior such as order.payableVnd() rather than retrieving all data and inferring the result. However, query APIs for UIs, reports, or serialization are legitimate; not every getter is a code smell.

## 8. When Should You NOT Move a Method?

| Situation | Why not move it hastily? | Alternative |
| --- | --- | --- |
| Behavior uses several peer domain objects | No natural single owner for the entire rule | Dedicated domain service / policy |
| Algorithm depends on network APIs, databases, or email | An entity would acquire I/O dependencies and complicate transactions | Application service or adapter |
| Promotional rules change frequently and require multiple strategies | Packing every condition into Order makes the class bloated | DiscountPolicy / Strategy |
| Screen formatting or file export | Domain objects become coupled to presentation | Presenter / formatter |
| Moving causes circular dependencies | Cohesion may decrease and comprehension worsen | Extract a collaborator or revisit boundaries |
| A method reads extensive data from two large aggregates | May over-fetch data or violate transaction boundaries | Query service, domain service, or dedicated DTO |
| Logic genuinely depends on source-object state | Moving it makes the destination depend back on the source | Keep it at the source or extract a smaller component |

A practical rule: **move behavior that shares a reason to change, not every method that happens to use many getters**.

For example, if discounts depend on campaign codes across multiple systems, moving all campaign logic into Order would couple it to coupon APIs. A better choice may be for PricingPolicy to calculate the discount, Order to maintain order invariants, and CheckoutService to coordinate the necessary steps.

## 9. Extension Exercises

**Exercise 1 — Find misplaced behavior.** Suppose ReportService iterates over OrderLine objects to count products. Distinguish two needs:
- For the total quantity in **one order**, consider Order.totalQuantity().
- For the total quantity sold **over a period across multiple orders**, a report/query service may be the right owner.

**Exercise 2 — Refactor a cart.** Create CartItem(price, quantity) and Cart(items). Initially, CartService calculates totals; then move per-line calculations into CartItem and aggregation into Cart. Test empty carts, invalid quantities, overflow, and modifications to the supplied list.

**Exercise 3 — Changing discount policies.** Add a DiscountPolicy interface with a method that calculates discounts. Design Order to aggregate amounts while a dedicated object handles discount policy. Specify which policy is supplied at construction or pricing time, and avoid storing prices that become stale.

**Exercise 4 — API migration.** If another module still calls PriceService.calculatePayable(order), temporarily retain the old method delegating to order.payableVnd(), mark it deprecated when appropriate, migrate callers, and remove it once compatibility impact is checked.

**Self-assessment:** After refactoring, behavior primarily belongs to the object whose data it uses; coordinators know fewer internal details; tests preserve external behavior; and no new circular dependencies or domain-object I/O coupling appear.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-005">Encapsulating Fields and Internal Structures</a>

<details>
<summary>View details</summary>

# Encapsulating Fields and Internal Structures in Java OOP

> **Scope:** Refactoring — *Encapsulate Field*, *Encapsulate Collection*, and *Hide Representation*. The goal is for objects to protect their own valid state and expose a stable API to callers.

## 1. The Practical Problem: A `private` Field Is Not Enough

In object-oriented programming, **encapsulation** means an object controls how its state is read and changed. Clients should know **what an object can do**, not **how it stores its data**.

Two situations that undermine encapsulation:

~~~java
class Order {
    public final List<OrderLine> lines = new ArrayList<>();
}
~~~

`final` prevents reassigning the **reference** `lines`; it **does not** prevent `order.lines.clear()`, `add()`, or mutation of elements inside the list.

~~~java
class Order {
    private final List<OrderLine> lines = new ArrayList<>();

    public List<OrderLine> getLines() {
        return lines; // Vẫn rò rỉ tham chiếu nội bộ!
    }
}
~~~

Even though the field is `private`, the client can still modify the list directly through the getter:

~~~java
order.getLines().clear();
~~~

This is **representation exposure**: the data structure representing internal state is exposed, letting other code modify it without going through the object's rules.

### Learning Outcomes

After this lesson, you should be able to:

- Recognize exposed fields, mutable collections, mutable elements, and aliased references that leak state.
- Apply `Encapsulate Field` by keeping fields `private` and exposing meaningful operations rather than generating getters/setters for every field.
- Hide `ArrayList`, `HashMap`, or arrays behind a **behavioral contract** independent of the underlying storage structure.
- Distinguish **immutable snapshots**, **unmodifiable views**, and **deep copies**.
- Write tests proving clients cannot modify internal state through returned references.
- Refactor carefully, preserve promised public behavior, and handle API compatibility when necessary.

## 2. Why Encapsulate Internal Structures?

Suppose `ShoppingCart` has these rules:

1. Each SKU appears at most once.
2. An SKU must be non-`null`, nonempty, and not whitespace-only.
3. Unit prices (in cents) are nonnegative; quantities are positive for existing lines.
4. Changing a quantity to `0` removes the line rather than storing a line with quantity `0`.
5. The total is the sum of `unitPriceCents × quantity` across lines; it must not return a negative or incorrect result due to `long` overflow.

If clients can call `cart.lines.add(...)` or assign `cart.lines.get(0).quantity = -5`, they can break all these rules without `ShoppingCart` knowing.

An **invariant** is a condition that must hold in every observable object state after a successful public operation. The cart API should provide `putLine`, `changeQuantity`, `removeLine`, and `totalCents` rather than letting clients freely mutate its `Map`/`List`.

| Design | Protects invariants? | Key point |
|---|---|---|
| `public List<Line> lines` | No | Clients mutate the collection directly |
| `private List<Line> lines` + getter returning the original reference | No | Syntax changes, but representation remains exposed |
| Getter returning `Collections.unmodifiableList(lines)` | Prevents edits through the view only | A **live** view still reflects source changes; mutable elements remain editable |
| Getter returning `List.copyOf(lines)` | Prevents structural edits to the snapshot | **Shallow** copy: mutable elements can still change |
| Snapshot containing **immutable value objects** | Usually safe for reading | Clients cannot modify the list or elements through returned references |
| Rule-checked mutation API | Can enforce invariants | Every valid change passes through a controlled method |

**Encapsulation does not imply total immutability:** A cart may still change through **permitted** public operations. What matters is that no shortcut bypasses validation and business rules.

## 3. Runnable Java 17 Example: From Leaky Cart to Safe API

Create one `EncapsulationDemo.java` file with JDK 17 or later. The example includes an **intentionally unsafe** version to demonstrate the consequences and an improved version to test.

~~~java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class EncapsulationDemo {

    // BEFORE: client có thể sửa cả danh sách lẫn đối tượng nằm trong đó.
    static final class MutableLine {
        public String sku;
        public long unitPriceCents;
        public int quantity;

        MutableLine(String sku, long unitPriceCents, int quantity) {
            this.sku = sku;
            this.unitPriceCents = unitPriceCents;
            this.quantity = quantity;
        }
    }

    static final class LeakyCart {
        public final List<MutableLine> lines = new ArrayList<>();

        long totalCents() {
            long sum = 0;
            for (MutableLine line : lines) {
                sum += line.unitPriceCents * line.quantity;
            }
            return sum;
        }
    }

    // AFTER: value object bất biến, dùng để trả kết quả đọc.
    // String, long, int ở đây không để lộ mutable component.
    public record LineSnapshot(
            String sku, long unitPriceCents, int quantity) {
        public LineSnapshot {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku must not be blank");
            }
            if (unitPriceCents < 0) {
                throw new IllegalArgumentException("unitPriceCents must be >= 0");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be > 0");
            }
        }
    }

    static final class ShoppingCart {
        // Có thể đổi Map thành cấu trúc khác mà API không đổi.
        // LinkedHashMap giữ thứ tự chèn cho ví dụ.
        private final Map<String, LineSnapshot> lines = new LinkedHashMap<>();

        // Upsert: SKU mới thì thêm; SKU cũ thì thay giá và số lượng.
        public void putLine(String sku, long unitPriceCents, int quantity) {
            LineSnapshot next = new LineSnapshot(sku, unitPriceCents, quantity);
            lines.put(next.sku(), next);
        }

        // Chỉ thay số lượng; quantity = 0 xóa dòng.
        public void changeQuantity(String sku, int quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("quantity must be >= 0");
            }
            LineSnapshot current = lines.get(sku);
            if (current == null) {
                throw new NoSuchElementException("Unknown SKU: " + sku);
            }
            if (quantity == 0) {
                lines.remove(sku);
                return;
            }
            lines.put(sku,
                new LineSnapshot(sku, current.unitPriceCents(), quantity));
        }

        public boolean removeLine(String sku) {
            return lines.remove(sku) != null;
        }

        // Snapshot tách khỏi Map nội bộ; các element đều bất biến.
        public List<LineSnapshot> lines() {
            return List.copyOf(lines.values());
        }

        public long totalCents() {
            long result = 0;
            for (LineSnapshot line : lines.values()) {
                long itemTotal = Math.multiplyExact(
                    line.unitPriceCents(), line.quantity());
                result = Math.addExact(result, itemTotal);
            }
            return result;
        }

        public int lineCount() {
            return lines.size();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException");
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        // Chứng minh lỗi: client sửa state mà không qua validation.
        LeakyCart old = new LeakyCart();
        MutableLine leaked = new MutableLine("BOOK", 100, 2);
        old.lines.add(leaked);
        check(old.totalCents() == 200, "initial old total");
        leaked.quantity = -3; // alias ngoài giỏ hàng làm hỏng invariant
        check(old.totalCents() == -300, "expected broken old total");
        System.out.println("LEAK: client changed old cart total to -300");

        ShoppingCart cart = new ShoppingCart();
        cart.putLine("BOOK", 100, 2);
        cart.putLine("PEN", 99, 1);
        check(cart.totalCents() == 299, "initial safe total");
        check(cart.lineCount() == 2, "initial line count");

        List<LineSnapshot> snapshot = cart.lines();
        expectUnsupported(snapshot::clear);
        check(snapshot.get(0).quantity() == 2, "snapshot initial quantity");

        // Thao tác hợp lệ đi qua API có kiểm soát.
        cart.changeQuantity("BOOK", 3);
        check(cart.totalCents() == 399, "safe updated total");
        check(snapshot.get(0).quantity() == 2,
            "old snapshot must not change after cart update");
        check(cart.lines().get(0).quantity() == 3,
            "new snapshot must reflect new state");

        // Input sai bị chặn và không làm thay đổi state.
        expectIllegalArgument(() -> cart.putLine("BAD", -1, 1));
        expectIllegalArgument(() -> cart.putLine("X", 100, 0));
        expectIllegalArgument(() -> cart.putLine("  ", 100, 1));
        expectIllegalArgument(() -> cart.changeQuantity("BOOK", -1));
        check(cart.lineCount() == 2 && cart.totalCents() == 399,
            "invalid operations must not corrupt cart");

        // Đổi số lượng về 0 sẽ xóa dòng, không tạo dòng không hợp lệ.
        cart.changeQuantity("BOOK", 0);
        check(cart.lineCount() == 1, "remove by quantity zero");
        check(cart.totalCents() == 99, "total after removal");

        System.out.println("PASS: cannot modify exposed snapshot");
        System.out.println("PASS: invariants enforced by public methods");
        System.out.println("PASS: old snapshot is independent");
        System.out.println("Final total (cents): " + cart.totalCents());
    }
}
~~~

Compile and run:

~~~bash
javac EncapsulationDemo.java
java EncapsulationDemo
~~~

Expected output:

~~~text
LEAK: client changed old cart total to -300
PASS: cannot modify exposed snapshot
PASS: invariants enforced by public methods
PASS: old snapshot is independent
Final total (cents): 99
~~~

**Why Is the New Version Safer?**

1. `lines` is `private`, so clients cannot add/delete lines through field access.
2. `putLine` and `changeQuantity` are the **only entry points** for line changes in this model. Validation occurs **before** state updates.
3. `LineSnapshot` is a record with `String`, `long`, and `int` components; it contains no mutable-object references. The cart creates a new record when changing quantity.
4. `List.copyOf(lines.values())` creates an unmodifiable snapshot. Old snapshots do not change when `ShoppingCart` is updated internally.
5. `Math.multiplyExact` and `Math.addExact` detect overflow by throwing `ArithmeticException`, preventing silently incorrect negative `long` results.

The example does not address **concurrency**: `LinkedHashMap` is not thread-safe. If multiple threads modify the cart, restrict ownership per request/transaction or synchronize at the correct boundary; immutable snapshots do not automatically make the whole `ShoppingCart` thread-safe.

## 4. Three Commonly Confused Concepts: Copy, View, and Immutable Element

Consider a `List<Person>` where `Person` has a setter:

~~~java
final class Person {
    private String name;
    Person(String name) { this.name = name; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
}
~~~

| Statement | Can the returned list's structure be changed? | Are later source changes visible? | Can `Person` be mutated through the reference? |
|---|---|---|---|
| `return people;` | Yes | Yes, same object | Yes |
| `return Collections.unmodifiableList(people);` | Not through the view | **Yes** (live view) | **Yes** |
| `return new ArrayList<>(people);` | Yes, but only in the copy | Later source additions/removals are not visible | **Yes** |
| `return List.copyOf(people);` | No | Generally a structural snapshot of a mutable source | **Yes** |
| `return people.stream().map(p -> new ImmutablePerson(p.getName())).toList();` | Not through the returned list | No, when elements are immutable | No |

`List.copyOf` copies a mutable collection's structure but **does not deep-copy** elements. If an element is mutable, both source and copy still reference the same object. `Collections.unmodifiableList` only wraps a list; its original owner may still change it, and the view will reflect those changes.

`Stream.toList()` (Java 16+) produces an unmodifiable list, but **also does not automatically deep-copy its elements**.

### Use Real Defensive Copies for Mutable Elements

If `Person` must remain mutable, copy elements on **output**:

~~~java
public List<Person> peopleSnapshot() {
    return people.stream()
        .map(p -> new Person(p.getName()))
        .toList();
}
~~~

And copy them on **input**:

~~~java
public void addPerson(Person incoming) {
    java.util.Objects.requireNonNull(incoming);
    people.add(new Person(incoming.getName()));
}
~~~

This way, mutating `incoming` after `addPerson` or changing a `Person` returned by `peopleSnapshot` does not affect internal state, **provided** names are immutable `String` values and the class contains only the fields being copied. Nested objects, arrays, collections, and mutable `Date` values may require deeper copies of owned components. No universal clone function safely handles every object graph.

## 5. Applying Encapsulate Field Correctly

Consider a public field:

~~~java
class BankAccount {
    public long balanceCents;
}
~~~

Mechanically changing it to `private` plus a setter is insufficient:

~~~java
class BankAccount {
    private long balanceCents;

    public long getBalanceCents() { return balanceCents; }
    public void setBalanceCents(long balanceCents) {
        this.balanceCents = balanceCents; // vẫn cho phép caller gán âm
    }
}
~~~

If the contract is "the balance must remain nonnegative," the API should expose **valid actions**:

~~~java
final class BankAccount {
    private long balanceCents;

    BankAccount(long openingBalanceCents) {
        if (openingBalanceCents < 0) {
            throw new IllegalArgumentException("Negative opening balance");
        }
        this.balanceCents = openingBalanceCents;
    }

    long balanceCents() {
        return balanceCents; // primitive: trả theo giá trị, an toàn
    }

    void deposit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Deposit must be positive");
        }
        balanceCents = Math.addExact(balanceCents, amountCents);
    }

    void withdraw(long amountCents) {
        if (amountCents <= 0 || amountCents > balanceCents) {
            throw new IllegalArgumentException("Invalid withdrawal");
        }
        balanceCents -= amountCents;
    }
}
~~~

This lets clients increase/decrease balances only under valid conditions. Transferring between accounts requires an appropriate transactional operation; merely calling `withdraw` and then `deposit` does not guarantee atomicity if either step fails.

### Choose APIs According to the Nature of State

- Read-only configuration fields: use `private final` with a safe getter or constructor arguments.
- Constrained business values: prefer `deposit`, `withdraw`, `changeQuantity`, `rename`, and `activate` over `setAnything`.
- Object-owned collections: expose snapshots and validate mutations through `add/remove/update`.
- Computed values: prefer a `totalCents()` method rather than letting clients update a `total` field that may become inconsistent.
- DTOs that transfer data between layers/APIs: records or JavaBeans with setters may be appropriate; not every DTO must be a rich domain model.

**Key point:** The goal is not "as few getters/setters as possible." An API should reflect permitted object operations and the invariants that must be maintained.

## 6. Hiding Data Structures Reduces Implementation Coupling

Compare these two APIs:

~~~java
// Client phụ thuộc vào cấu trúc lưu trữ.
Map<String, LineSnapshot> getInternalMap();

// Client phụ thuộc vào khả năng cần dùng.
void putLine(String sku, long priceCents, int quantity);
void changeQuantity(String sku, int quantity);
List<LineSnapshot> lines();     // snapshot đọc
long totalCents();
~~~

With the second design, the implementation can switch from `LinkedHashMap` to a more suitable structure, add an SKU index, cache totals, or store other records while callers continue using the public contract.

~~~mermaid
classDiagram
    class CartClient {
      +renderCart()
      +updateQuantity()
    }
    class ShoppingCart {
      -Map~String,LineSnapshot~ lines
      +putLine(String,long,int)
      +changeQuantity(String,int)
      +removeLine(String) boolean
      +lines() List~LineSnapshot~
      +totalCents() long
    }
    class LineSnapshot {
      <<record>>
      +sku() String
      +unitPriceCents() long
      +quantity() int
    }
    CartClient --> ShoppingCart : gọi API công khai
    ShoppingCart o--> LineSnapshot : chứa dữ liệu bất biến
~~~

**Hiding implementation details** does not mean clients know nothing about the data. Clients may need SKU names, prices, and quantities for legitimate business purposes; they need not know whether those values are stored in an `ArrayList`, `LinkedHashMap`, or database table.

## 7. Safe Refactoring in a Running Project

1. **Identify data ownership:** Who may create, hold, modify, and discard collections or objects? Find every existing mutation path: public fields, getters, setters, constructor aliases, callbacks, and serialization.
2. **Document the contract first:** Does the field allow null or duplicates? Does order matter? Which exception types are promised? Which operations must preserve data?
3. **Add characterization/regression tests:** Cover valid data, invalid inputs, boundaries, and side effects clients actually expect.
4. **Move updates into the state owner:** For example, `addLine`, `removeLine`, and `changeQuantity`. Validate before mutating, keeping state valid even when exceptions occur.
5. **Restrict field access:** Make fields `private` where possible, migrating clients to the new API in controlled steps.
6. **Protect inputs and outputs:** Copy incoming collections when taking ownership; return suitable snapshots or views; for mutable elements, use safe copies or value objects.
7. **Retire old shortcuts:** Stop returning mutable references and exposing arbitrary setters that break invariants. For published public APIs, plan deprecation/migration or compatible adapters.
8. **Run tests and review diffs:** Check invariants, concurrency contracts where applicable, performance with large collections, and serialization/API compatibility.

~~~mermaid
flowchart TD
    A["Quan sát alias / field rò rỉ"] --> B["Xác định invariant và contract hiện có"]
    B --> C["Viết test bảo vệ hành vi"]
    C --> D["Tạo API thao tác có kiểm soát"]
    D --> E["Ẩn field và defensive copy"]
    E --> F{"Test regression + bất biến PASS?"}
    F -->|Không| G["Sửa nguyên nhân rồi chạy lại"]
    G --> F
    F -->|Có| H["Đổi client, dọn API cũ, review"]
~~~

### Compatibility Concerns

- **API/source compatibility:** Changing a `public` field to `private` breaks client code accessing it. Published libraries need migration plans and versioning.
- **Collection contracts:** If clients are promised **ordering**, switching from `LinkedHashMap` to `HashMap` and returning a list may change observable order. If order is not contractual, avoid tests that unnecessarily impose one.
- **ORM/JPA/serialization:** Frameworks may require constructors, access strategies, or annotations. Maintain framework compatibility rather than exposing public fields for convenience.
- **Performance:** `List.copyOf` creates snapshots when needed; frequent snapshots of very large collections can be costly. Consider paged queries, controlled iterators, or suitable immutable data structures without sacrificing invariants.
- **Thread safety:** Returning a safe snapshot does not automatically make collection updates thread-safe. Depending on context, use synchronization, immutable aggregates, or transactions.
- **JSON representation:** DTOs may differ from domain models. Adapters/mappers should convert between them without leaking domain storage structures into public payloads.

## 8. Testing Encapsulation: Prove Callers Cannot Break State

Tests should verify **behavior**, not just whether a field is marked `private`.

| Test case | Action | Expected result |
|---|---|---|
| Do not expose mutable lists | Receive `lines()` and call `clear()` | Blocked; cart remains unchanged |
| Independent snapshots | Take snapshot A, update the cart, then take B | A retains old state; B reflects new state |
| Do not expose mutable elements | Attempt to modify a returned element | No direct mutation path; copy mutable DTOs |
| Validate before writing | Call `putLine("", 100, 1)` | Throw `IllegalArgumentException`; total and lineCount remain unchanged |
| Quantity invariant | Call `changeQuantity("BOOK", 0)` | Remove BOOK; do not store quantity = 0 |
| Invalid operation | Call `changeQuantity("UNKNOWN", 2)` | Throw `NoSuchElementException`; existing data stays unchanged |
| Calculation overflow | Provide values large enough to overflow | Never return a wrapped total; report a detectable error |
| Contract stability | Replace the internal `Map` implementation | Valid client scenarios still produce correct results |

Use JUnit 5 (JUnit Jupiter), placing tests in the same package as `EncapsulationDemo`:

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShoppingCartTest {
    @Test
    void returnedCollectionCannotMutateCart() {
        var cart = new EncapsulationDemo.ShoppingCart();
        cart.putLine("BOOK", 100, 2);

        List<EncapsulationDemo.LineSnapshot> before = cart.lines();
        assertThrows(UnsupportedOperationException.class, before::clear);

        cart.changeQuantity("BOOK", 3);
        assertEquals(2, before.get(0).quantity());
        assertEquals(3, cart.lines().get(0).quantity());
        assertEquals(300, cart.totalCents());
    }

    @Test
    void failedValidationDoesNotCorruptState() {
        var cart = new EncapsulationDemo.ShoppingCart();
        cart.putLine("BOOK", 100, 2);

        assertThrows(IllegalArgumentException.class,
            () -> cart.putLine("PEN", -1, 2));
        assertThrows(IllegalArgumentException.class,
            () -> cart.changeQuantity("BOOK", -4));

        assertEquals(1, cart.lineCount());
        assertEquals(200, cart.totalCents());
    }

    @Test
    void removingByZeroKeepsInvariant() {
        var cart = new EncapsulationDemo.ShoppingCart();
        cart.putLine("BOOK", 100, 2);
        cart.changeQuantity("BOOK", 0);

        assertTrue(cart.lines().isEmpty());
        assertEquals(0, cart.totalCents());
    }
}
~~~

The tests rely on demo classes in the same package (nested classes are not declared `public`). Real projects should place each type in an appropriate file and choose access modifiers based on package/module APIs.

Also test **input-object boundaries**. If a constructor accepts `List<MutableLine>` and stores the reference directly, callers can still change `MutableLine` objects or the list afterward, even if getters perform defensive copies.

## 9. Common Refactoring Mistakes

**Mistake 1 — "Private fixes everything."** Getters returning the original `List`/`Map` still expose data. Examine all **reference paths** into and out of the object.

**Mistake 2 — Adding setters for every field.** Unrestricted setters for `balance`, `items`, and `status` may permit invalid states. Prefer valid domain operations.

**Mistake 3 — Assuming `final` makes an object immutable.** A `private final List<T>` can still change contents when the owner calls `add/remove`; that is valid when controlled. If the reference leaks, outside code can modify it too.

**Mistake 4 — Confusing an unmodifiable view with a snapshot.** `Collections.unmodifiableList(source)` blocks edits through the wrapper but still reflects changes in `source`; `List.copyOf(source)` creates a structural snapshot (while sharing element references).

**Mistake 5 — Copying a list but not its elements.** `new ArrayList<>(people)` does not deep-copy `Person`. Calling `peopleCopy.get(0).setName("X")` may modify internal data when both lists share the same element.

**Mistake 6 — Assuming records are deeply immutable.** Records containing `List`, `Date`, or arrays only have final fields; elements and nested objects may be mutable. Make defensive copies in constructors and accessors as needed.

**Mistake 7 — Forgetting atomicity during mutation.** Updating state before validation or potentially failing logic can leave an object partially changed. Calculate and validate first, then commit; multiple resources may require a transaction.

**Mistake 8 — Breaking a library's public API without a plan.** Changing a `public field` to private is not automatically a compatible refactoring at the library boundary. API compatibility tests and migration guidance may be necessary.

## 10. Practice Exercises

**Exercise 1 — Identify the Exposure:**

~~~java
class Library {
    private final List<StringBuilder> titles = new ArrayList<>();

    void add(StringBuilder title) { titles.add(title); }
    List<StringBuilder> getTitles() { return List.copyOf(titles); }
}
~~~

Describe **two paths** that unintentionally change internal titles: modifying `StringBuilder` after passing it into `add`, and modifying a `StringBuilder` returned by `getTitles`. Try fixing the design with `List<String>` (using immutable `String` elements) or by copying values on input/output.

**Exercise 2 — Replace Setter APIs:** An `Employee` exposes `setSalaryCents(long)` publicly. Define salary invariants and propose domain-specific methods (`adjustSalary` or `changeSalary` with validation and context-appropriate authorization). Test negative amounts and values exceeding business limits.

**Exercise 3 — Cart Lines:** Change `ShoppingCart.lines()` to return a live unmodifiable view instead of a snapshot. Write a test distinguishing **whether A remains unchanged after an update**, then decide which behavior matches the contract.

**Exercise 4 — Change Internal Representation:** Replace `LinkedHashMap` with another structure while keeping `putLine`, `changeQuantity`, `removeLine`, and `totalCents`. If result ordering was never promised, do not make clients depend on the internal order.

**Exercise 5 — Null, Races, and Overflow:** Extend tests for null SKUs, missing SKUs, large monetary values, and concurrent calls. Distinguish validation defects from thread-safety requirements that need to be added to the contract.

### Completion Criteria

You can identify state leaks through fields, getters, constructors, or mutable elements; design mutation APIs that maintain invariants; choose appropriate **snapshots/views/copies**; and prove with Java tests that clients cannot arbitrarily corrupt internal structures. When refactoring libraries or running systems, also check public API, framework, and concurrency compatibility.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-006">Replacing Type-Based Conditionals with Polymorphism</a>

<details>
<summary>View details</summary>

# Replacing Type-Based Conditionals with Polymorphism (Replace Conditional with Polymorphism) — Java OOP

## 1. The Problem: Why Are Many Type-Based Branches Hard to Maintain?

Suppose a sales application supports three delivery modes: STANDARD (regular delivery), EXPRESS (fast delivery), and PICKUP (customer collection). Each has different fee and delivery-time rules.

Initial implementations often inspect the type with a switch or chain of if/else conditions:

~~~java
switch (request.type) {
    case STANDARD: /* tính phí giao thường */ break;
    case EXPRESS:  /* tính phí giao nhanh */ break;
    case PICKUP:   /* tự nhận hàng */ break;
}
~~~

Such a block is not automatically a defect. Problems arise when **the same type-based decision** repeats across operations (fee calculation, delivery estimates, request processing, notification output), requiring edits in many places for each new type. Related branches are easily missed, and one class accumulates behavior for many variants.

**Replace Conditional with Polymorphism** is a refactoring technique that moves type-varying behavior into implementations of a shared contract. Callers ask an object to perform an operation, and Java selects the corresponding method through **dynamic dispatch**.

> The objective is to reorganize code **while preserving the observable contract**: valid inputs, results, exceptions, and promised side effects. Encountering a switch does not automatically justify a class hierarchy.

## 2. Comparing Structures Before and After

| Aspect | Type-based conditionals | Polymorphism |
|---|---|---|
| Behavior selection | Caller uses if/else, switch, or instanceof | Caller invokes a common method on an interface/superclass |
| Rule location | Numerous branches in one large class | Each implementation owns its rules |
| Adding a type | Edit each relevant switch | Add an implementation and register it at object selection/creation boundaries |
| Testing | One class contains many branches | Shared contract tests and tests for each variant |
| Extensibility | Easy to overlook branches | Depends on registration design and type structure |
| Cost | Fewer classes and simplicity with a few branches | More classes/objects and the need for a sensible interface |

This technique often supports the **Open/Closed Principle**: extend behavior through new implementations while limiting edits to stable business code. It does not eliminate type selection entirely: inputs may still contain type codes, which must be mapped to suitable objects at a boundary.

~~~mermaid
flowchart LR
    C["Client"] --> B["DeliveryService"]
    B --> P["DeliveryPolicy interface"]
    S["StandardPolicy"] -.-> P
    E["ExpressPolicy"] -.-> P
    K["PickupPolicy"] -.-> P
~~~

In this model, DeliveryService does not know each variant's fee formula. It only uses the DeliveryPolicy interface.

## 3. Runnable Java 11 Example: From Switch to Polymorphism

### Business Contract

An order has a nonnegative goods value (VND) and a positive integer weight (kg). Whole-kilogram weights are assumed to focus on refactoring rather than real-world measurements.

| Type | Shipping-fee rule | Estimated delivery days |
|---|---|---:|
| STANDARD | If order value is at least 500.000 VND: 0; otherwise: 20.000 + 5.000 × kg | 5 |
| EXPRESS | 45.000 + 10.000 × kg | 1 |
| PICKUP | 0 VND | 0 (customer collects the order) |

The service returns a DeliveryQuote containing feeVnd and etaDays. The legacy and refactored versions must produce **identical results** for all valid inputs without changing promised validation rules.

Save the complete program below in **DeliveryRefactoringDemo.java**. It uses Java 11 or later and requires neither Maven nor Spring nor a testing library.

~~~java
import java.util.Map;
import java.util.Objects;

public class DeliveryRefactoringDemo {
    enum DeliveryType {
        STANDARD, EXPRESS, PICKUP
    }

    static final class DeliveryRequest {
        final DeliveryType type;
        final long orderValueVnd;
        final int weightKg;

        DeliveryRequest(DeliveryType type,
                        long orderValueVnd,
                        int weightKg) {
            if (type == null || orderValueVnd < 0 || weightKg <= 0) {
                throw new IllegalArgumentException("Invalid delivery request");
            }
            this.type = type;
            this.orderValueVnd = orderValueVnd;
            this.weightKg = weightKg;
        }
    }

    static final class DeliveryQuote {
        final long feeVnd;
        final int etaDays;

        DeliveryQuote(long feeVnd, int etaDays) {
            this.feeVnd = feeVnd;
            this.etaDays = etaDays;
        }
    }

    // TRƯỚC: mọi quy tắc tính phí và thời gian ở một switch.
    static final class LegacyDeliveryService {
        DeliveryQuote quote(DeliveryRequest request) {
            Objects.requireNonNull(request, "request");

            switch (request.type) {
                case STANDARD:
                    long standardFee =
                            request.orderValueVnd >= 500_000L
                            ? 0L
                            : Math.addExact(20_000L,
                                  Math.multiplyExact(
                                      5_000L, (long) request.weightKg));
                    return new DeliveryQuote(standardFee, 5);

                case EXPRESS:
                    long expressFee = Math.addExact(45_000L,
                            Math.multiplyExact(
                                10_000L, (long) request.weightKg));
                    return new DeliveryQuote(expressFee, 1);

                case PICKUP:
                    return new DeliveryQuote(0L, 0);

                default:
                    throw new IllegalStateException("Unsupported type");
            }
        }
    }

    // SAU: một hợp đồng chung cho mọi loại giao hàng.
    interface DeliveryPolicy {
        DeliveryQuote quote(DeliveryRequest request);
    }

    static final class StandardPolicy implements DeliveryPolicy {
        @Override
        public DeliveryQuote quote(DeliveryRequest request) {
            long fee = request.orderValueVnd >= 500_000L
                    ? 0L
                    : Math.addExact(20_000L,
                          Math.multiplyExact(
                              5_000L, (long) request.weightKg));
            return new DeliveryQuote(fee, 5);
        }
    }

    static final class ExpressPolicy implements DeliveryPolicy {
        @Override
        public DeliveryQuote quote(DeliveryRequest request) {
            long fee = Math.addExact(45_000L,
                    Math.multiplyExact(
                        10_000L, (long) request.weightKg));
            return new DeliveryQuote(fee, 1);
        }
    }

    static final class PickupPolicy implements DeliveryPolicy {
        @Override
        public DeliveryQuote quote(DeliveryRequest request) {
            return new DeliveryQuote(0L, 0);
        }
    }

    // SAU: service điều phối, không chứa công thức theo loại.
    static final class DeliveryService {
        private final Map<DeliveryType, DeliveryPolicy> policies;

        DeliveryService(Map<DeliveryType, DeliveryPolicy> policies) {
            Objects.requireNonNull(policies, "policies");
            this.policies = Map.copyOf(policies);
            for (DeliveryType type : DeliveryType.values()) {
                if (!this.policies.containsKey(type)) {
                    throw new IllegalArgumentException(
                            "Missing policy for " + type);
                }
            }
        }

        DeliveryQuote quote(DeliveryRequest request) {
            Objects.requireNonNull(request, "request");
            // Đây là tra cứu chính sách, không phải các nhánh tính phí.
            DeliveryPolicy policy = policies.get(request.type);
            return policy.quote(request); // Dynamic dispatch
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static void assertScenario(
            LegacyDeliveryService before,
            DeliveryService after,
            DeliveryRequest request,
            long expectedFee,
            int expectedDays) {

        DeliveryQuote oldQuote = before.quote(request);
        DeliveryQuote newQuote = after.quote(request);

        // Kiểm tra yêu cầu nghiệp vụ bằng kỳ vọng độc lập.
        check(oldQuote.feeVnd == expectedFee,
                "Old fee differs from requirement");
        check(oldQuote.etaDays == expectedDays,
                "Old ETA differs from requirement");
        check(newQuote.feeVnd == expectedFee,
                "New fee differs from requirement");
        check(newQuote.etaDays == expectedDays,
                "New ETA differs from requirement");

        // Đồng thời xác nhận hành vi cũ/sau refactor tương đương.
        check(oldQuote.feeVnd == newQuote.feeVnd,
                "Fee changed after refactoring");
        check(oldQuote.etaDays == newQuote.etaDays,
                "ETA changed after refactoring");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) {
        LegacyDeliveryService before = new LegacyDeliveryService();

        DeliveryService after = new DeliveryService(Map.of(
                DeliveryType.STANDARD, new StandardPolicy(),
                DeliveryType.EXPRESS, new ExpressPolicy(),
                DeliveryType.PICKUP, new PickupPolicy()
        ));

        // Dưới ngưỡng: 20.000 + 5.000 × 2 = 30.000.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.STANDARD,
                                    499_999L, 2),
                30_000L, 5);

        // Đúng ngưỡng miễn phí.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.STANDARD,
                                    500_000L, 2),
                0L, 5);

        // Giao nhanh: 45.000 + 10.000 × 3 = 75.000.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.EXPRESS,
                                    100_000L, 3),
                75_000L, 1);

        // Tự đến lấy: không tính phí và không có ngày giao.
        assertScenario(before, after,
                new DeliveryRequest(DeliveryType.PICKUP,
                                    0L, 1),
                0L, 0);

        // Các quy tắc dữ liệu không hợp lệ vẫn được giữ.
        expectInvalid(() ->
                new DeliveryRequest(DeliveryType.STANDARD, -1L, 1));
        expectInvalid(() ->
                new DeliveryRequest(DeliveryType.EXPRESS, 100L, 0));
        expectInvalid(() ->
                new DeliveryRequest(null, 100L, 1));

        System.out.println("4 scenarios + 3 invalid inputs: PASS");
    }
}
~~~

Compile and run in the file's directory:

~~~bash
javac DeliveryRefactoringDemo.java
java DeliveryRefactoringDemo
~~~

When all checks pass:

~~~text
4 scenarios + 3 invalid inputs: PASS
~~~

The program deliberately verifies behavior **in two ways**: comparing before/after results and comparing them against independent business expectations. Comparing versions alone is insufficient because both could implement the same incorrect formula.

### What Changed, and What Remained the Same?

| Concern | Before refactoring | After refactoring |
|---|---|---|
| Standard-delivery formula | STANDARD branch in LegacyDeliveryService | StandardPolicy.quote |
| Express-delivery formula | EXPRESS branch | ExpressPolicy.quote |
| Customer pickup | PICKUP branch | PickupPolicy.quote |
| Policy selection | Switch on enum | Map from enum → DeliveryPolicy |
| Variant-specific execution | Execute the selected branch | Call policy.quote; JVM selects implementation |
| Free-shipping threshold at 500.000 | Unchanged | Unchanged |
| Results and input-validation rules | Original contract | Unchanged |

### Why Is This Polymorphism?

The policy variable has type **DeliveryPolicy**, but the actual object may be StandardPolicy, ExpressPolicy, or PickupPolicy. Calling policy.quote(request) causes the JVM to execute the overriding method on the concrete class. DeliveryService need not know which formula runs.

This is **runtime polymorphism** through an interface and method overriding, not merely a switch rewritten as three private methods. If all three formulas stay in one class with enum-based branching, type-specific behavior has not been distributed to objects.

## 4. Refactoring Steps for a Real Project

1. **Locate type-based conditions.** Find branches examining the same discriminator, such as an enum, string code, or instanceof. Determine which branches represent the same kind of behavior.
2. **Write characterization tests.** Record current results for important inputs: common and rare types, price thresholds, failures, and meaningful side effects.
3. **Extract a shared contract.** For example, DeliveryPolicy.quote(request). Name methods after business responsibilities rather than creating an interface simply to hide a switch.
4. **Extract per-type implementations.** Move formulas and variant-specific logic into StandardPolicy, ExpressPolicy, and PickupPolicy one at a time, compiling and testing after each move.
5. **Centralize implementation selection.** Use a factory, DI container, or Map registry at the object creation/selection boundary. Business code receives or looks up DeliveryPolicy only.
6. **Remove obsolete conditionals.** Avoid maintaining the old switch alongside the new policy system for long, because their behavior can diverge.
7. **Run regression and integration tests.** Validate results, exceptions, collaborator contracts, transactions, and external resources where the real application uses them.

~~~mermaid
sequenceDiagram
    participant C as Client
    participant S as DeliveryService
    participant R as Registry
    participant P as DeliveryPolicy cụ thể
    C->>S: quote(request)
    S->>R: get(request.type)
    R-->>S: policy
    S->>P: quote(request)
    P-->>S: DeliveryQuote
    S-->>C: DeliveryQuote
~~~

## 5. Choosing Interfaces, Abstract Classes, Subclasses, or Strategy

**Interface + implementation**, as in the example, suits variants with a shared contract but different formulas and little common state/behavior to inherit.

**Abstract class + subclass** fits when genuine shared invariants and default processing steps exist, such as common input validation. Avoid moving too much conditional logic back into the base class.

**Strategy pattern (favoring composition)** fits when behavior is selected by configuration or changes at runtime rather than being intrinsic to an entity's type. STANDARD/EXPRESS/PICKUP are delivery policies chosen per request, so they do not necessarily require Order subclasses.

**Enum constants with their own methods** are another form of polymorphism in Java:

~~~java
enum SimpleDelivery {
    STANDARD {
        @Override long fee(int kg) {
            return 20_000L + 5_000L * kg;
        }
    },
    EXPRESS {
        @Override long fee(int kg) {
            return 45_000L + 10_000L * kg;
        }
    };

    abstract long fee(int kg);
}
~~~

This short example **illustrates syntax**, not the full fee contract in section 3 (it omits value-based free shipping, input validation, and overflow handling). Enum-specific methods work well for fixed variants with short logic. Complex policies, external dependencies, or module-level registration of new implementations often favor separate Strategy classes for flexibility and testing.

## 6. Common Mistakes

| Mistake | Why it misses the goal | How to address it |
|---|---|---|
| Creating an interface while the service still switches on types to call concrete classes | Callers still know details and require branch edits | Move selection into a factory/registry and call a shared contract |
| Using instanceof and casting for every variant | Subtype coupling remains | Invoke polymorphic methods; retain type checks only when truly needed at boundaries |
| Every policy inspects request.type before deciding what to do | Conditions are duplicated across classes | Let the registry select policies; each policy handles its own rule |
| Adding a huge superclass to share unrelated details | High coupling; changes to the parent affect all types | Extract a concise abstraction and favor composition |
| Moving code while changing business formulas | Behavior preservation becomes hard to prove | Refactor and change policies in separately tested steps |
| Adding a subtype but forgetting registry registration | Runtime failure on the new type | Test complete registration and fail fast at startup |
| Treating every switch as a code smell | Many classes for simple, stable conditions | Keep a switch when it remains clear and rarely changes |
| Testing only that a policy method was called once | Does not prove shipping fees are correct | Assert feeVnd, etaDays, and independent boundary cases |

### Can New Types Be Added Without Editing Any Existing Code?

Not absolutely. With the example's enum and Map architecture, adding SAME_DAY still requires defining an enum constant, creating SameDayPolicy, and registering it in the Map. The important point is that **DeliveryService.quote needs no new formula or branch**. If requirements call for dynamic plugin registration, a DI container/factory may discover implementations, at the cost of configuration, duplicate checks, and missing-policy handling.

### Should Variant-Specific Fields Enter the Shared Interface?

Do not force every variant to hold unused fields merely for uniformity. If each type needs very different input, consider specialized requests, behavioral abstractions, or explicit validation boundaries. Interfaces should express real common capabilities, not methods that implementations must override only to throw UnsupportedOperationException.

### Concerns with Stateful Objects

The example's policies are stateless and can be shared safely. If an implementation writes mutable fields while serving multiple requests, consider object lifetime, thread safety, and synchronization. Refactoring must not accidentally turn per-request state into shared singleton state.

## 7. Testing to Prove Behavior Has Not Changed

Replacing switches with polymorphism must not lose boundary cases. Test at these levels:

- **DeliveryService contract tests:** Each type returns correct fees and ETA; STANDARD handles the 499.999/500.000 threshold; invalid inputs are rejected; new types are fully registered.
- **Per-policy unit tests:** Focus on each formula and overflow. For example, ExpressPolicy.quote with large weights must use exact long arithmetic or throw ArithmeticException on overflow.
- **Integration tests for external systems:** Verify that the factory/DI selects the correct policy and that real adapters perform payment/delivery requests in a controlled testing environment.

Example JUnit 5 tests for the standard-delivery boundary (assuming the program above resides in the same package):

~~~java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardPolicyTest {
    @Test
    void freeShippingAppliesAtThreshold() {
        DeliveryRefactoringDemo.DeliveryPolicy policy =
                new DeliveryRefactoringDemo.StandardPolicy();

        var below = policy.quote(
                new DeliveryRefactoringDemo.DeliveryRequest(
                    DeliveryRefactoringDemo.DeliveryType.STANDARD,
                    499_999L, 2));

        var atThreshold = policy.quote(
                new DeliveryRefactoringDemo.DeliveryRequest(
                    DeliveryRefactoringDemo.DeliveryType.STANDARD,
                    500_000L, 2));

        assertEquals(30_000L, below.feeVnd);
        assertEquals(5, below.etaDays);
        assertEquals(0L, atThreshold.feeVnd);
        assertEquals(5, atThreshold.etaDays);
    }
}
~~~

Use JUnit Jupiter in the test scope to run the example. Note that policy.quote receives DeliveryRequest containing a type to illustrate an existing-system contract, but StandardPolicy does not inspect that field; DeliveryService selects the correct policy by type. A mature design might pass only the data each policy needs, avoiding the impression that policies must always check the enum.

## 8. When Should You NOT Apply This Refactoring?

A short switch in **one place** for classifying status codes, logging, or simple mappings is often easier to understand than an entire hierarchy. For a fixed set of types whose behavior rarely changes, the cost of interfaces, factories, and files may exceed their benefits.

Likewise, do not replace checks based on **values or state** with subtypes merely because they use if. For instance, the free-shipping threshold (orderValueVnd >= 500.000) is a rule **inside StandardPolicy**, not a new delivery type. Polymorphism selects *families of behavior* by type; business conditions inside each variant can and should remain.

Before refactoring, ask three questions:

1. Does the condition recur across behaviors and often require synchronized updates?
2. Do variants share a contract clear enough for callers to use without knowing concrete classes?
3. Can existing tests protect results, failures, and side effects when code moves to different implementations?

If all three answers are yes, Replace Conditional with Polymorphism often provides substantial value.

## 9. Exercises

**Exercise 1 — Add SAME_DAY:** Assume same-day delivery costs 80.000 + 12.000 × kg with ETA = 0 days (within the day). Add DeliveryType.SAME_DAY, implement SameDayPolicy, and register it. Write independent tests for the exact formula without adding a branch to DeliveryService.quote.

**Exercise 2 — Detect regressions:** Deliberately change the STANDARD free-shipping rule to greater than 500.000 instead of greater than or equal to. Run the program; the case exactly at 500.000 must fail.

**Exercise 3 — Recognize duplicated conditions:** If another class switches on DeliveryType to format a service description, consider including display names in DeliveryPolicy. Explain when adding formatting responsibilities to a policy would reduce cohesion and propose a separate collaborator if appropriate.

**Exercise 4 — Verify registration:** Remove one policy from the Map and confirm that the DeliveryService constructor rejects incomplete configuration with IllegalArgumentException before accepting requests.

**Exercise 5 — Choose a technique:** For four status codes that only change the color of a UI label, compare one short switch with four classes. Choose the less complex approach and explain why polymorphism is not always beneficial.

## 10. Key Takeaways

**Replace Conditional with Polymorphism** moves type-dependent behavior to the objects responsible for it. Callers use a common contract, and Java dispatches to the right implementation. This is **refactoring**, so behavior must be preserved; do not confuse architectural restructuring with changing business policy. Choose polymorphism when it genuinely reduces repeated branches, improves cohesion, lowers coupling, and enables controlled extension.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-007">Replacing Inheritance with Composition</a>

<details>
<summary>View details</summary>

# Replacing Inheritance with Composition (Replace Inheritance with Composition) in Java OOP

## 1. The Problem to Solve

**Inheritance** lets one class extend another using `extends`. It is useful when their classification genuinely satisfies an **is-a** relationship: a subclass object can be used anywhere the superclass is accepted while honoring its behavioral contract.

In practice, a class sometimes extends another simply to **reuse a few methods**. The subclass then becomes coupled to the parent's API, state, and changes. A parent-class change may unexpectedly affect it, and the subclass may need to override methods unrelated to its own responsibilities.

**Replace Inheritance with Composition** changes a design from "class A extends class B to borrow behavior" to "class A **contains an object** providing the needed capability and **delegates** work to it."

Goals:
- Separate **business responsibilities** from **execution mechanisms**.
- Reduce dependency on superclass implementation details.
- Make implementations configurable through interfaces.
- Enable unit testing with fakes/stubs without activating real external systems.
- Preserve observable behavior during refactoring, except for explicitly planned API changes.

This **does not prohibit inheritance**. Inheritance remains appropriate for stable classifications where subclasses satisfy the superclass contract. The goal is to choose a mechanism that fits the object's responsibility.

## 2. Comparing Inheritance and Composition

| Aspect | Inheritance | Composition |
| --- | --- | --- |
| Model relationship | **is-a**: a specialization of the parent | **has-a/uses-a**: owns or uses a collaborator |
| Java | `class Child extends Parent` | Reference field: `private final Capability capability;` |
| Reuse | Inherit the parent's methods and mechanisms | Invoke methods on an injected object |
| Coupling | Often tightly bound to superclass structure/contracts | Can depend on a small interface |
| Replacing implementations | Often requires another subclass or inheritance changes | Supply a different implementation at construction |
| Testing | May bring along superclass behavior | Inject fakes/mocks at collaboration boundaries |
| Costs | Fewer delegates; clear when the is-a relationship is real | Adds fields, constructors, delegation, and lifecycle decisions |

Note: In Java, **class inheritance** through `extends` and **interface implementation** through `implements` impose different forms of coupling. An object can `implements MessageChannel` to fulfill a contract while another uses `MessageChannel` through composition.

## 3. Before Refactoring: An Alert Class Inherits from an Email Sender

Suppose a system must send critical alerts to operations staff. Its first version uses email:

~~~java
class EmailSender {
    public void send(String address, String text) {
        System.out.println("EMAIL -> " + address + ": " + text);
    }
}

// Kế thừa chỉ để tận dụng phương thức send().
class CriticalAlert extends EmailSender {
    public void notifyOperator(String address, String incident) {
        send(address, "[CRITICAL] " + incident);
    }
}
~~~

This works while email is the only channel, but `CriticalAlert extends EmailSender` misrepresents the domain: **an alert is not a kind of email sender**. An alert merely *uses* a delivery mechanism.

Adding SMS, webhooks, or internal notifications can lead to separate alert subclasses for every channel. The alert also inherits the entire public/protected EmailSender API, including future methods unrelated to the alert's responsibility.

If a subclass must override parent methods to throw `UnsupportedOperationException` or weaken postconditions expected by superclass clients, it risks violating the **Liskov Substitution Principle (LSP)**. Not all inheritance violates LSP; the problem is failure to substitute under the contract.

Visualize the before-and-after structure:

~~~mermaid
classDiagram
    class EmailSender {
        +send(address, text)
    }
    class CriticalAlertOld {
        +notifyOperator(address, incident)
    }
    EmailSender <|-- CriticalAlertOld : kế thừa để dùng send

    class AlertService {
        -MessageChannel channel
        +sendCritical(recipient, incident)
    }
    class MessageChannel {
        <<interface>>
        +send(recipient, body)
    }
    class EmailChannel {
        +send(recipient, body)
    }
    class SmsChannel {
        +send(recipient, body)
    }
    class RetryingChannel {
        -MessageChannel delegate
        +send(recipient, body)
    }
    AlertService o--> MessageChannel : chứa và ủy quyền
    MessageChannel <|.. EmailChannel
    MessageChannel <|.. SmsChannel
    MessageChannel <|.. RetryingChannel
    RetryingChannel o--> MessageChannel : bọc
~~~

## 4. After Refactoring: Composition Through an Interface

Separate two responsibilities:
- `AlertService` understands the business: what constitutes a critical alert and how its message is composed.
- `MessageChannel` handles message delivery. Email, SMS, and retry-capable channels are alternative implementations.

`AlertService` **has a** `MessageChannel`; it **is not a** `MessageChannel`.

The delegated workflow:

~~~mermaid
sequenceDiagram
    actor Operator as Ứng dụng
    participant A as AlertService
    participant C as MessageChannel
    Operator->>A: sendCritical(recipient, incident)
    A->>A: Kiểm tra dữ liệu và tạo nội dung
    A->>C: send(recipient, "[CRITICAL] ...")
    C-->>A: Trả về hoặc ném lỗi
    A-->>Operator: Hoàn thành hoặc truyền lỗi
~~~

### 4.1. Project Structure

The example uses **Java 17, Maven, and JUnit Jupiter 5**. Production classes are provided as separate Java files so they can be copied and run.

~~~text
composition-demo/
├── pom.xml
└── src/
    ├── main/java/vn/example/
    │   ├── MessageChannel.java
    │   ├── AlertService.java
    │   ├── EmailChannel.java
    │   ├── SmsChannel.java
    │   ├── DeliveryException.java
    │   ├── RetryingChannel.java
    │   └── Main.java
    └── test/java/vn/example/
        └── AlertServiceTest.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>vn.example</groupId>
    <artifactId>composition-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Define the Message-Delivery Contract

**src/main/java/vn/example/MessageChannel.java**

~~~java
package vn.example;

@FunctionalInterface
public interface MessageChannel {
    /**
     * Gửi nội dung tới người nhận. Có thể ném RuntimeException nếu lỗi.
     * Trả về bình thường nghĩa là kênh đã xử lý yêu cầu theo hợp đồng.
     */
    void send(String recipient, String body);
}
~~~

A small interface is enough for AlertService. It does not require the alert class to know SMTP, HTTP endpoints, or SMS formatting. In a real project, the contract must specify whether `send` means provider acceptance, queueing, or actual device delivery; those guarantees differ.

### 4.3. Let the Business Object Delegate to a Collaborator

**src/main/java/vn/example/AlertService.java**

~~~java
package vn.example;

import java.util.Objects;

public final class AlertService {
    private final MessageChannel channel;

    public AlertService(MessageChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    public void sendCritical(String recipient, String incident) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is blank");
        }
        if (incident == null || incident.isBlank()) {
            throw new IllegalArgumentException("incident is blank");
        }

        channel.send(recipient, "[CRITICAL] " + incident);
    }
}
~~~

Important points:
1. Constructor injection supplies dependencies during application assembly.
2. `final` keeps the collaborator reference fixed for AlertService's lifetime, preventing unexpected replacement mid-operation.
3. Public methods express **business behavior** instead of exposing the entire delivery-channel API.
4. Validation happens **before calling the channel**, so invalid inputs have no external side effects.
5. AlertService knows no concrete implementation; it only requires the channel to fulfill `send`.

### 4.4. Email and SMS Implementations

To run without external accounts, the following classes **print to the console**. They are illustrative adapters, not real email/SMS senders.

**src/main/java/vn/example/EmailChannel.java**

~~~java
package vn.example;

public final class EmailChannel implements MessageChannel {
    @Override
    public void send(String recipient, String body) {
        System.out.println("EMAIL -> " + recipient + ": " + body);
    }
}
~~~

**src/main/java/vn/example/SmsChannel.java**

~~~java
package vn.example;

public final class SmsChannel implements MessageChannel {
    @Override
    public void send(String recipient, String body) {
        System.out.println("SMS -> " + recipient + ": " + body);
    }
}
~~~

Both follow the same interface without becoming superclasses of AlertService. To add another channel, create an implementation instead of making the business class inherit from a new transport type.

### 4.5. Combine Capabilities: A Wrapper That Retries Classified Failures

Suppose temporary failures should trigger retries. Inheritance can lead to classes such as `RetryingEmailChannel`, `RetryingSmsChannel`, and `LoggingRetryingEmailChannel`, creating a combinatorial explosion of variants.

With composition, create a **wrapper (decorator)** implementing the same interface while containing another `MessageChannel`. The wrapper retries only when the delegate throws `DeliveryException`.

**src/main/java/vn/example/DeliveryException.java**

~~~java
package vn.example;

/** Trong ví dụ, biểu thị lỗi có thể thử lại. */
public final class DeliveryException extends RuntimeException {
    public DeliveryException(String message) {
        super(message);
    }
}
~~~

**src/main/java/vn/example/RetryingChannel.java**

~~~java
package vn.example;

import java.util.Objects;

public final class RetryingChannel implements MessageChannel {
    private final MessageChannel delegate;
    private final int maxAttempts;

    public RetryingChannel(MessageChannel delegate, int maxAttempts) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void send(String recipient, String body) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                delegate.send(recipient, body);
                return;
            } catch (DeliveryException failure) {
                if (attempt == maxAttempts) {
                    throw failure;
                }
            }
        }
    }
}
~~~

These can be composed as needed:
- `new RetryingChannel(new EmailChannel(), 3)`
- `new RetryingChannel(new SmsChannel(), 3)`
- `new AlertService(new EmailChannel())`
- `new AlertService(new RetryingChannel(new SmsChannel(), 3))`

**Count attempts correctly:** `maxAttempts = 3` means at most **3 total sends**, not an initial send plus three retries. This wrapper lacks backoff, timeouts, and idempotency policies; it only demonstrates behavior composition.

### 4.6. Composition Root: Selecting Implementations

**src/main/java/vn/example/Main.java**

~~~java
package vn.example;

public final class Main {
    public static void main(String[] args) {
        MessageChannel reliableEmail =
                new RetryingChannel(new EmailChannel(), 3);
        AlertService emailAlerts = new AlertService(reliableEmail);

        AlertService smsAlerts =
                new AlertService(new SmsChannel());

        emailAlerts.sendCritical(
                "ops@example.com", "Database is unavailable");
        smsAlerts.sendCritical(
                "+84900000000", "API latency is high");
    }
}
~~~

Run in the directory containing `pom.xml`:

~~~bash
mvn test
mvn -q -DskipTests package
java -cp target/classes vn.example.Main
~~~

Expected demo output:

~~~text
EMAIL -> ops@example.com: [CRITICAL] Database is unavailable
SMS -> +84900000000: [CRITICAL] API latency is high
~~~

Concrete choices belong in the **composition root**, where the object graph is assembled at startup. AlertService has no `if (channelType.equals("SMS"))` branch and does not construct `new EmailChannel()` inside business methods.

## 5. Unit Tests: Verify Substitutability and Retry Rules

Test behavior with in-memory fake implementations, without connecting to real email or SMS services.

**src/test/java/vn/example/AlertServiceTest.java**

~~~java
package vn.example;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AlertServiceTest {
    record Sent(String recipient, String body) {}

    private static final class RecordingChannel implements MessageChannel {
        final List<Sent> messages = new ArrayList<>();

        @Override
        public void send(String recipient, String body) {
            messages.add(new Sent(recipient, body));
        }
    }

    private static final class FlakyChannel implements MessageChannel {
        final int failuresBeforeSuccess;
        int calls;
        int successfulSends;

        FlakyChannel(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public void send(String recipient, String body) {
            calls++;
            if (calls <= failuresBeforeSuccess) {
                throw new DeliveryException("Temporary error");
            }
            successfulSends++;
        }
    }

    @Test
    void sendsCriticalMessageThroughInjectedChannel() {
        RecordingChannel channel = new RecordingChannel();
        AlertService alerts = new AlertService(channel);

        alerts.sendCritical("ops@example.com", "Disk full");

        assertEquals(
                List.of(new Sent("ops@example.com", "[CRITICAL] Disk full")),
                channel.messages);
    }

    @Test
    void invalidInputDoesNotTouchTheChannel() {
        RecordingChannel channel = new RecordingChannel();
        AlertService alerts = new AlertService(channel);

        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical(" ", "Disk full"));
        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical("ops@example.com", ""));
        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical(null, "Disk full"));
        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical("ops@example.com", null));

        assertTrue(channel.messages.isEmpty());
    }

    @Test
    void retryingChannelEventuallySucceeds() {
        FlakyChannel flaky = new FlakyChannel(2);
        AlertService alerts = new AlertService(
                new RetryingChannel(flaky, 3));

        alerts.sendCritical("ops@example.com", "Database down");

        assertEquals(3, flaky.calls);
        assertEquals(1, flaky.successfulSends);
    }

    @Test
    void retryingChannelStopsAfterAttemptLimit() {
        FlakyChannel flaky = new FlakyChannel(10);
        AlertService alerts = new AlertService(
                new RetryingChannel(flaky, 2));

        assertThrows(DeliveryException.class,
                () -> alerts.sendCritical(
                        "ops@example.com", "Database down"));

        assertEquals(2, flaky.calls);
        assertEquals(0, flaky.successfulSends);
    }

    @Test
    void unexpectedExceptionIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        MessageChannel broken = (recipient, body) -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("Bad configuration");
        };

        AlertService alerts =
                new AlertService(new RetryingChannel(broken, 5));

        assertThrows(IllegalArgumentException.class,
                () -> alerts.sendCritical("ops@example.com", "Disk full"));
        assertEquals(1, calls.get());
    }

    @Test
    void dependenciesAndRetryLimitMustBeValid() {
        assertThrows(NullPointerException.class,
                () -> new AlertService(null));
        assertThrows(NullPointerException.class,
                () -> new RetryingChannel(null, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryingChannel(new RecordingChannel(), 0));
    }
}
~~~

Run:

~~~bash
mvn test
~~~

With the files above, JUnit provides **6 `@Test` methods**. The tests need no network and can deliberately simulate success, transient failures, and non-retryable failures.

| Test | What it verifies | Benefit of composition |
| --- | --- | --- |
| sendsCriticalMessageThroughInjectedChannel | Correct message and recipient reach the collaborator | Replace real email with a fake without changing AlertService |
| invalidInputDoesNotTouchTheChannel | Invalid input is blocked before side effects | Business rules remain independent of implementation |
| retryingChannelEventuallySucceeds | Fails 2 times and succeeds on attempt 3 | The wrapper can decorate any implementation |
| retryingChannelStopsAfterAttemptLimit | A maximum of 2 calls is respected | Retry policy can be tested independently |
| unexpectedExceptionIsNotRetried | Non-retryable failures do not trigger repeated calls | Clear failure-policy boundaries |
| dependenciesAndRetryLimitMustBeValid | Objects cannot be created with missing required dependencies | Constructors establish invariants |

Because AlertService accepts a `MessageChannel`, the same business behavior can be tested with email, SMS, or fake channels. Adapters talking to external services still need **separate integration tests** for real APIs, configuration, and failures.

## 6. Steps for Refactoring Inheritance into Composition

Do not remove `extends` before understanding clients and public contracts. A safe sequence:

1. **Confirm the current relationship.** Ask "Is AlertService really an EmailSender?" and "does it inherit for classification or just to use `send`?"
2. **Establish existing behavior.** Test messages, parameters, errors, and send counts. In particular, check whether callers rely on using the subclass as its superclass.
3. **Find the minimal dependency.** If the subclass only needs `send(recipient, body)`, introduce `MessageChannel` rather than exposing all of EmailSender's API.
4. **Introduce composition.** Add `private final MessageChannel channel` and a constructor receiving the interface. During gradual migration, retain a short delegating method for old APIs if needed.
5. **Replace inherited calls.** Change `send(...)` to `channel.send(...)`. Regression tests must still pass.
6. **Migrate callers.** Code that previously created `new CriticalAlert()` now supplies an appropriate implementation. If an API accepts `EmailSender` and clients pass `CriticalAlert`, assess transitional adapters; removing `extends` is a **type-compatibility change**.
7. **Remove unnecessary inheritance.** After checking callers and compatibility, remove `extends` and overrides that existed solely for the superclass.
8. **Verify design and behavior.** Compile, run unit and necessary integration tests, check public APIs, and confirm implementation selection through configuration.

~~~mermaid
flowchart TD
    A["Lớp con extends lớp cha chỉ để dùng một chức năng"] --> B["Khoanh vùng hợp đồng và các caller"]
    B --> C["Viết test hồi quy"]
    C --> D["Tạo interface cho chức năng cần dùng"]
    D --> E["Tiêm đối tượng qua constructor"]
    E --> F["Đổi lời gọi kế thừa thành ủy quyền"]
    F --> G["Chuyển caller và kiểm tra tính tương thích"]
    G --> H{"Test và hợp đồng đạt?"}
    H -->|Không| I["Chỉnh sai lệch hoặc adapter chuyển tiếp"]
    I --> G
    H -->|Có| J["Bỏ extends dư thừa, rà soát thiết kế"]
~~~

### 6.1. When Is a Compatibility Adapter Needed?

For example, if a public library exposes `CriticalAlert extends EmailSender`, external code may write:

~~~java
// Ví dụ từ API cũ:
EmailSender sender = new CriticalAlert();
~~~

Once CriticalAlert becomes a class containing MessageChannel, that line **no longer compiles**. This is not an algorithmic defect but a breaking change for callers. Determine binary/source compatibility requirements, plan versioning, or supply a public adapter during migration.

In an internal codebase where all callers can be changed, migration may be simpler. For a published API, altering inheritance relationships requires more scrutiny than renaming a private method.

## 7. How Do Strategy, Decorator, and Dependency Injection Relate?

**Composition** is the relationship in which one object contains or uses another. It is the underlying mechanism.

**Strategy pattern** uses interchangeable implementations of the same interface. Here, `EmailChannel` and `SmsChannel` are alternative delivery strategies selected when constructing AlertService.

**Decorator pattern** wraps an implementation with another implementation of the same interface to extend behavior. `RetryingChannel` implements `MessageChannel` while also containing a `MessageChannel`, adding retries without modifying EmailChannel/SmsChannel.

**Dependency Injection (DI)** supplies collaborators externally. `new AlertService(new SmsChannel())` already uses constructor injection; composition does not require a DI framework.

| Question | Relevant concept |
| --- | --- |
| How does object A use the capabilities of B? | Composition + delegation |
| How can email or SMS be selected for the same need? | Strategy |
| How can retries or logging be added without modifying the base channel? | Decorator |
| Who constructs and supplies the channel to AlertService? | Dependency injection / composition root |

These patterns **can work together** rather than exclude one another. `AlertService` receives a Strategy through its constructor; that Strategy may itself be a Decorator wrapping another adapter.

## 8. When Is Inheritance Still Appropriate?

Inheritance adds value when subclasses are genuine specializations of their superclass, honor a stable contract, and do not unexpectedly strengthen preconditions or weaken postconditions. Examples include internal hierarchies with documented extension hooks or stable framework base classes. Even then, shared superclass-contract tests may be warranted.

Conversely, if subclasses override many methods to do nothing, throw exceptions, or rely on superclass `protected` fields to maintain consistent state, revisit the design.

A practical decision guide:

| Signal | Preferred direction |
| --- | --- |
| "A is a B" and can substitute for B under its contract | Inheritance may fit |
| "A has a B" or "A uses B's service" | Composition |
| Different implementations are selected by environment | Composition through an interface |
| Logging, retries, and metrics need to be layered | Composition/Decorator |
| Subclasses proliferate for every feature combination | Separate and compose independent capabilities |
| Changing a superclass unexpectedly breaks many subclasses | Reduce superclass coupling; consider composition |
| One small algorithm exists with no need for substitution | Keep it directly in the class; avoid needless abstractions |

**Do not mechanically create an interface for every class.** If a simple pure calculation has no meaningful alternative implementation or boundary needing isolation, an interface and delegate object only increase reading overhead.

## 9. Common Conversion Mistakes

### 9.1. Replacing Extends with a Field While Exposing the Whole API

~~~java
// Ví dụ cách chuyển chưa tốt:
class PoorAlertService {
    private final EmailChannel email = new EmailChannel();

    void sendRaw(String recipient, String body) {
        email.send(recipient, body);
    }
}
~~~

This uses composition syntactically but remains bound to a concrete `EmailChannel`, and its public API still exposes raw sending instead of alert business behavior. If multiple channels are truly needed, receiving `MessageChannel` through the constructor gives a better separation.

### 9.2. Turning a Service into a "God Object"

Composition does not justify one class containing dozens of dependencies and making every decision. If AlertService calculates prices, authorizes users, schedules tasks, and sends emails, its primary problem is still too much responsibility. Separate use cases, policies, and adapters where needed.

### 9.3. Blindly Retrying Non-Idempotent Operations

The example retries only channel `DeliveryException` failures. In real services, the provider may accept the initial request while the response is lost; retrying can send duplicate emails/SMS messages. Critical actions such as payments require idempotency keys, durable state, queries/reconciliation, backoff, and explicit retry rules. Composition helps **locate** a policy in the right component; it does not automatically make that policy safe.

### 9.4. Confusing Composition with Interface Implementation

`EmailChannel implements MessageChannel` uses an interface to declare a contract. `AlertService` composes a `MessageChannel` because it has a `channel` field. Both relationships can coexist and serve different purposes.

### 9.5. Ignoring Lifecycle and Thread Safety

If multiple threads share a stateful collaborator, verify that the collaborator is concurrency-safe. `private final` fixes the **reference**, not the immutability or thread safety of the referenced object.

## 10. Guided Practice

**Exercise 1 — Add a Webhook Channel.** Implement `WebhookChannel implements MessageChannel` as a demo that records URLs and messages in a list. Use `new AlertService(new WebhookChannel(...))`, verify the alert content, and leave AlertService unchanged.

**Exercise 2 — Add LoggingChannel.** Write a decorator around `MessageChannel` that counts calls. Try `new LoggingChannel(new RetryingChannel(new EmailChannel(), 3))` and `new RetryingChannel(new LoggingChannel(new EmailChannel()), 3)`. Explain why wrapper order can produce different counts.

**Exercise 3 — Assess Retry Tests.** Create a fake that fails on attempt 1 and succeeds on attempt 2. Verify that maxAttempts = 1 propagates the failure, while maxAttempts = 2 succeeds. Ensure `IllegalArgumentException` is never retried.

**Exercise 4 — Identify Relationships.** Consider: "Car uses Engine," "SavingsAccount is an Account," "OrderService uses EmailSender," and "CSV exporter is a Formatter." For each, distinguish genuine business/data relationships from substitutable capabilities; explain when `extends`, `implements`, or a composed field is appropriate.

**Exercise 5 — Assess the Public API.** Find callers using subclass instances as their superclass type before refactoring. Document calls that rely on inheritance and propose adapters/migration paths before removing extends.

### Post-Refactoring Checklist

- [ ] Each class has a role that can be explained as one clear responsibility.
- [ ] Consumers depend on small interfaces without unnecessary knowledge of concrete adapter internals.
- [ ] Inherited methods inconsistent with the consumer's semantics are not exposed.
- [ ] Success, failure, validation, and retry-limit cases are tested.
- [ ] Callers and public contracts have been checked for compatibility.
- [ ] Real external side effects are covered by integration tests at infrastructure boundaries.
- [ ] Composition is used because it improves clarity, not merely to avoid the keyword `extends`.

**Expected outcome:** You can replace alert-delivery channels or add retries through **object composition** without editing business classes or adding subclass hierarchies for every feature combination.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-008">Consolidating or Removing Unnecessary Abstractions</a>

<details>
<summary>View details</summary>

# Consolidating or Removing Unnecessary Abstractions in OOP

> **Topic:** Refactoring — simplifying Java design by consolidating intermediary classes, interfaces, or methods that no longer have clear responsibilities while preserving the behavior clients rely on.

Good OOP design is not measured by **the number of interfaces, classes, or patterns**. An abstraction is useful when it names a meaningful concept, hides variation, protects responsibility boundaries, or supports genuinely needed substitution.

When a class merely forwards all calls, an interface duplicates an implementation's API without providing a distinct contract, or a hierarchy exists only for speculative future features, the system can become harder to understand. Removing these layers may involve **Inline Class**, **Remove Middle Man**, **Collapse Hierarchy**, or **Inline Method**, depending on the structure being simplified.

**Important:** Not every interface with one implementation is redundant. A single-implementation interface may still define an **I/O boundary**, library contract, plugin mechanism, or architecturally necessary substitution point. Evaluate its purpose and real change costs before deleting it.

## 1. Learning Objectives

After this lesson, you should be able to:

- Distinguish **valuable abstractions** from **forwarding layers that add no value**.
- Recognize `Middle Man`, `Speculative Generality`, redundant hierarchies, forwarding-only wrappers, and excessively extracted methods.
- Choose `Inline Class`, `Remove Middle Man`, `Collapse Hierarchy`, `Inline Method`, or parameter/adapter simplification appropriately.
- Refactor incrementally and validate outcomes with **regression/contract tests**.
- Assess risks involving published APIs, DI containers, frameworks, serialization, or actual extension needs that depend on abstractions.
- Recognize when to **retain an interface** despite currently having just one implementation.

## 2. What Problem Does a Valuable Abstraction Solve?

Consider a payment system with an interface:

~~~java
interface PaymentGateway {
    PaymentResult charge(PaymentRequest request);
}
~~~

The application may currently use one payment gateway, but `PaymentGateway` can still be worth keeping if it separates business code from the provider, defines failure contracts, establishes a network I/O boundary, or permits sandbox testing.

By contrast:

~~~java
interface GreetingPort {
    String greeting(String name);
}

final class GreetingPortImpl implements GreetingPort {
    private final GreetingEngine engine = new GreetingEngine();

    @Override
    public String greeting(String name) {
        return engine.greeting(name); // không có logic, chính sách hoặc hợp đồng riêng
    }
}

final class GreetingEngine {
    String greeting(String name) {
        return "Xin chào, " + name;
    }
}
~~~

If `GreetingPort` and `GreetingPortImpl` are used internally in one place, do not form a stable API, have no genuine substitution need, and serve no architectural boundary, they could be simplified to:

~~~java
final class Greeter {
    String greeting(String name) {
        return "Xin chào, " + name;
    }
}
~~~

This is a **conditional example**. If `GreetingPort` is used by a public library, DI module, plugin, or external client, removing it may break compilation or compatibility. First inspect the API's real consumers.

| Question | Reasons to retain | Reasons to consolidate |
|---|---|---|
| Are there **multiple valid behaviors** to substitute? | Real Strategy/plugin/adapter choices | One path with no requested variants |
| Is there an **infrastructure boundary**? | APIs, databases, networks, message brokers, time, file systems | Pure forwarding wrappers within one module |
| Does the abstraction define an **independent contract**? | Distinct behavior, error, permission, or invariant guarantees | Same signatures and semantics as the wrapped class |
| Does it **reduce coupling**? | Domain code need not know frameworks/vendors | Readers traverse extra files without any coupling reduction |
| Are there **external clients**? | Published, serialized, reflected, or type-name-based DI usage | Only private/internal call sites that can change together |
| Are there **independent reasons to change**? | The classes evolve separately | Both always change together without forming a useful boundary |
| Is there a **real testing benefit**? | Isolated external side effects and independent contract tests | Mocking a forwarding class and duplicating the same tests |

**Do not use rigid rules** such as "one implementation means delete the interface," "merge every class shorter than 20 lines," or "all delegates are redundant." An abstraction's value lies in responsibility and contract stability.

## 3. Common Signs of Excessive Abstraction

### Middle Man — An Intermediary That Mostly Delegates

~~~java
final class OrderFacade {
    private final OrderService service;

    OrderFacade(OrderService service) {
        this.service = service;
    }

    long total(Order order) {
        return service.total(order);
    }
}
~~~

If a facade adds no API meaning, authorization checks, multi-collaborator coordination, exception translation, or stable boundary, clients may call `OrderService` directly. However, a facade is valuable when it represents a **use case** or hides real complexity.

### Speculative Generality — Preparing for an Imaginary Future

For example, adding `AbstractReportFactoryBuilderBase`, `ReportFactoryProvider`, and three interface layers solely because "more formats may be added later" can complicate onboarding and debugging when only one report type exists and no changes are required.

### Fragmented Responsibility — Many Classes Sharing One Small Job

A chain such as `TaxInputWrapper → TaxCalculatorFacade → TaxCalculatorImpl` may merely pass the same two parameters and return one integer. Without a meaningful reason for separation, moving logic into the class that clearly owns the responsibility shortens the path.

### Hierarchies Without Meaningful Polymorphism

~~~java
class BaseOrderPrinter {
    String format(Order order) {
        return order.toString();
    }
}
final class DefaultOrderPrinter extends BaseOrderPrinter { }
~~~

If a subclass adds no behavior, has no extension contract, and exists only to introduce another class name, consider removing that inheritance level after checking dependent clients.

### Tiny Methods That Do Not Clarify Intent

~~~java
long add(long a, long b) { return a + b; }
long sum(long a, long b) { return add(a, b); }
~~~

A method that only calls another method may be worth inlining. However, names such as `taxableAmount` and `validateAccess` can express **domain meaning** or a centralized control point, making them valuable even when short.

## 4. Runnable Java Example: Removing Two Unnecessary Forwarding Layers

**Context:** A checkout service calculates shipping fees and discounts. The legacy code has three layers:

1. `CheckoutFacade` only delegates `quote`.
2. `PricingAdapter` implements `PricingPort` but only calls `PricingEngine`.
3. `PricingEngine` actually validates inputs and calculates prices.

In this example, neither intermediary introduces network/database/plugin boundaries or additional policies. The internal system has one client of the `Checkout` API. Keep that abstraction as **the client-facing contract**, but replace the internal forwarding chain with `SimplifiedCheckout` performing the business logic directly.

### Contract to Preserve

- `subtotalCents` is an amount in cents from `0` through `1_000_000`, inclusive.
- VIP customers receive a **10%** discount (`subtotal / 10`, rounded down) and free shipping.
- Standard customers receive no discount. Orders of **100.000 cents or more** ship free; below that, shipping costs **15.000 cents**.
- `totalCents = subtotalCents - discountCents + shippingCents`.
- Out-of-range inputs throw `IllegalArgumentException`.
- Quoting has no side effects and returns stable results for the same input.

Create `AbstractionRemovalDemo.java` with JDK **17 or later**:

~~~java
import java.util.List;
import java.util.Random;

public class AbstractionRemovalDemo {
    private static final long MAX_SUBTOTAL = 1_000_000L;
    private static final long FREE_SHIPPING_AT = 100_000L;
    private static final long SHIPPING_FEE = 15_000L;

    // Hợp đồng công khai với client trong ví dụ.
    interface Checkout {
        Invoice quote(long subtotalCents, boolean vip);
    }

    // Record: so sánh giá trị bằng equals(), không cần setter.
    record Invoice(long discountCents, long shippingCents, long totalCents) {}

    private static void validate(long subtotalCents) {
        if (subtotalCents < 0 || subtotalCents > MAX_SUBTOTAL) {
            throw new IllegalArgumentException("Invalid subtotalCents");
        }
    }

    // BEFORE: một interface nội bộ không có biến thể thực tế.
    interface PricingPort {
        Invoice calculate(long subtotalCents, boolean vip);
    }

    // Lớp chứa logic nghiệp vụ ở thiết kế cũ.
    static final class PricingEngine {
        Invoice calculate(long subtotalCents, boolean vip) {
            validate(subtotalCents);
            long discount = vip ? subtotalCents / 10 : 0;
            long shipping = (vip || subtotalCents >= FREE_SHIPPING_AT)
                ? 0 : SHIPPING_FEE;
            return new Invoice(
                discount, shipping, subtotalCents - discount + shipping
            );
        }
    }

    // Chỉ chuyển tiếp, không chuyển đổi dữ liệu/hợp đồng/ngoại lệ.
    static final class PricingAdapter implements PricingPort {
        private final PricingEngine engine = new PricingEngine();

        @Override
        public Invoice calculate(long subtotalCents, boolean vip) {
            return engine.calculate(subtotalCents, vip);
        }
    }

    // Facade này cũng chỉ forward vào adapter.
    static final class LegacyCheckout implements Checkout {
        private final PricingPort port = new PricingAdapter();

        @Override
        public Invoice quote(long subtotalCents, boolean vip) {
            return port.calculate(subtotalCents, vip);
        }
    }

    // AFTER: gộp logic nội bộ vào class thực hiện use case.
    // Hợp đồng Checkout mà client sử dụng không đổi.
    static final class SimplifiedCheckout implements Checkout {
        @Override
        public Invoice quote(long subtotalCents, boolean vip) {
            validate(subtotalCents);
            long discount = vip ? subtotalCents / 10 : 0;
            long shipping = (vip || subtotalCents >= FREE_SHIPPING_AT)
                ? 0 : SHIPPING_FEE;
            return new Invoice(
                discount, shipping, subtotalCents - discount + shipping
            );
        }
    }

    record Case(long subtotal, boolean vip, Invoice expected) {}

    // Oracle độc lập: expected value được ghi rõ theo yêu cầu.
    private static final List<Case> CASES = List.of(
        new Case(0, false, new Invoice(0, 15_000, 15_000)),
        new Case(0, true, new Invoice(0, 0, 0)),
        new Case(99_999, false, new Invoice(0, 15_000, 114_999)),
        new Case(100_000, false, new Invoice(0, 0, 100_000)),
        new Case(100_001, false, new Invoice(0, 0, 100_001)),
        new Case(250_001, true, new Invoice(25_000, 0, 225_001)),
        new Case(1_000_000, true, new Invoice(100_000, 0, 900_000))
    );

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // Bộ regression test áp cho bất kỳ implementation của Checkout.
    static void verifyContract(String label, Checkout service) {
        for (Case c : CASES) {
            Invoice actual = service.quote(c.subtotal(), c.vip());
            require(c.expected().equals(actual),
                label + " differs at " + c + "; actual=" + actual);

            require(actual.discountCents() >= 0,
                label + ": negative discount");
            require(actual.totalCents() ==
                    c.subtotal() - actual.discountCents()
                        + actual.shippingCents(),
                label + ": invalid total");
        }

        expectInvalid(() -> service.quote(-1, false));
        expectInvalid(() -> service.quote(MAX_SUBTOTAL + 1, true));
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    // Differential test dùng nhiều input cố định seed.
    static void compareBeforeAfter(Checkout before, Checkout after) {
        Random random = new Random(219L);
        for (int i = 0; i < 1_000; i++) {
            long subtotal = random.nextInt(1_000_001);
            boolean vip = random.nextBoolean();
            Invoice oldResult = before.quote(subtotal, vip);
            Invoice newResult = after.quote(subtotal, vip);
            require(oldResult.equals(newResult),
                "Behavior changed for subtotal=" + subtotal + ", vip=" + vip);
        }
    }

    public static void main(String[] args) {
        Checkout oldDesign = new LegacyCheckout();
        Checkout newDesign = new SimplifiedCheckout();

        verifyContract("legacy", oldDesign);
        verifyContract("simplified", newDesign);
        compareBeforeAfter(oldDesign, newDesign);

        System.out.println("PASS: legacy contract");
        System.out.println("PASS: simplified contract");
        System.out.println("PASS: before/after comparison");
        System.out.println(newDesign.quote(250_001, true));
    }
}
~~~

Compile and run:

~~~bash
javac AbstractionRemovalDemo.java
java AbstractionRemovalDemo
~~~

Expected output:

~~~text
PASS: legacy contract
PASS: simplified contract
PASS: before/after comparison
Invoice[discountCents=25000, shippingCents=0, totalCents=225001]
~~~

The old and new classes coexist **only for demonstration and comparison in the demo file**. In a real project, after validation and migrating all call sites, remove unused `PricingPort`, `PricingAdapter`, `PricingEngine`, and `LegacyCheckout` layers. Do not maintain parallel production paths without a specific compatibility or migration reason.

### Before and After Refactoring

~~~mermaid
flowchart LR
    subgraph Before["Trước: bốn lớp/interface trên đường gọi"]
      A["Client"] --> B["Checkout"]
      B --> C["LegacyCheckout"]
      C --> D["PricingPort"]
      D --> E["PricingAdapter"]
      E --> F["PricingEngine"]
    end
    subgraph After["Sau: một hợp đồng và một implementation"]
      G["Client"] --> H["Checkout"]
      H --> I["SimplifiedCheckout"]
    end
~~~

The improvement is a **clearer execution path**: readers no longer open multiple classes to find where pricing is calculated. Calls through `Checkout`, the returned `Invoice` type, and input/output rules remain unchanged.

## 5. Choosing the Right Consolidation or Removal Technique

| Technique | When to use it | Example | Risks to check |
|---|---|---|---|
| **Inline Method** | A method only calls another and adds no meaning | `int calc() { return addTax(); }` adds no contract | The method name may encode useful domain language |
| **Inline Class** | A class does too little and has no distinct responsibility | `AddressFormatterHelper` used only by `AddressFormatter` | Frameworks or external clients may reference the class |
| **Remove Middle Man** | A class mostly consists of pure delegation | `Facade → Adapter → Engine` forwarding chain | Delegates may protect security, transaction, or I/O boundaries |
| **Collapse Hierarchy** | Superclass/subclass differ little in behavior/responsibility | `DefaultPrinter extends BasePrinter` without extra logic | Polymorphic behavior, binary/source compatibility, overrides |
| **Remove Unused Abstraction** | An interface/abstract class/factory has no remaining legitimate consumers | An interface only for a removed feature | Plugins, reflection, DI configuration, external clients |
| **Simplify Parameter Object** | An object only wraps a value without invariants or distinct meaning | `NameWrapper` with no policies or reuse | Could be a value object protecting units/validation |
| **Replace Hierarchy with Composition** | Complex inheritance only reuses a few behaviors | Many subclasses combining two dimensions of variation | Does not always **reduce** abstraction; assess separately |

**Do not merge independent responsibilities just to reduce file counts.** If `PricingEngine` serves multiple use cases or has its own responsibility, retain it and remove only the redundant adapter. Simplify in ways that improve *cohesion*, do not worsen *coupling*, and keep testing straightforward.

## 6. How Do Inline Class and Collapse Hierarchy Differ?

### Inline Class: Return a Small Responsibility to Its Proper Owner

Suppose:

~~~java
final class NameJoiner {
    String fullName(String first, String last) {
        return first + " " + last;
    }
}

final class CustomerLabel {
    private final NameJoiner joiner = new NameJoiner();

    String label(String first, String last) {
        return joiner.fullName(first, last);
    }
}
~~~

If `NameJoiner` has only this one caller, is not reused, has no I/O dependency, and does not encapsulate a shared formatting rule, it could be inlined:

~~~java
final class CustomerLabel {
    String label(String first, String last) {
        return first + " " + last;
    }
}
~~~

But if the entire system must follow localized name-display rules, independent formatting is an important responsibility. In that case, retain a service/type expressing the proper contract.

### Collapse Hierarchy: Remove an Inheritance Level Without Distinction

Before:

~~~java
class BaseFormatter {
    String format(int value) {
        return Integer.toString(value);
    }
}

final class DefaultFormatter extends BaseFormatter {
    // Không override, không thêm trạng thái hoặc invariant.
}
~~~

If no consumer needs to distinguish `BaseFormatter` from `DefaultFormatter`, they can be replaced by one class:

~~~java
final class Formatter {
    String format(int value) {
        return Integer.toString(value);
    }
}
~~~

This does not mean every empty subclass must disappear: a subclass might be a **marker type**, discovered through framework annotations, preserve a compatibility contract, or have a confirmed upcoming extension requirement. Inspect clients, annotations, and configuration before changing the hierarchy.

## 7. Identify Real Boundaries Before Deleting Abstractions

Similar-looking forwarding layers may serve very different purposes:

~~~java
interface Clock {
    java.time.Instant now();
}
~~~

This may be a useful boundary for testing time-dependent behavior without waiting for the real clock. If `Clock` isolates domain logic from the system clock, removing it can make testing harder.

~~~java
interface CustomerRepository {
    Customer findById(long id);
}
~~~

This may be a boundary between **domain/application** and **database**, even with just one JDBC implementation. Removing the interface could pull SQL/infrastructure into code that should not depend on it.

By contrast:

~~~java
final class IdEchoAdapter {
    long pass(long id) {
        return id;
    }
}
~~~

If this is merely a meaningless layer with no validation, transformation, boundary, or semantics, there is no clear architectural reason to retain it.

**Quick Decision Table:**

| Boundary type | Retain when… | Consider consolidating when… |
|---|---|---|
| Domain ↔ database/network | Hides side effects, permits infrastructure substitution and stable tests | An intermediary merely renames methods and the contract already lives elsewhere |
| Use case ↔ domain | Coordinates transactions, authorization, or several collaborators | A use case simply forwards without distinct meaning |
| API ↔ DTO/domain | Handles validation, versioning, mapping, or serialization | A wrapper performs no conversion and is not a public API |
| Strategy/Plugin | Real variants or an extension contract exist | Only a speculative future with no consumers |
| Value object | Enforces units, constraints, or domain meaning | A wrapper only renames a primitive without semantics |
| Module facade | Hides several subsystems and stabilizes APIs | The facade forwards 100% of calls without hiding details |

## 8. Safe Refactoring: Remove Redundancy While Preserving Contracts

1. **Map the current call path.** Draw or trace calls from the public API to the business rules. Mark forwarding-only classes/interfaces and identify their clients.
2. **Find the reason for each layer.** Inspect dependencies, tests, DI configuration, annotations, visibility, framework integration, and extension points. Mark boundaries worth keeping.
3. **Establish public behavior.** Write specification-based tests for valid inputs, boundaries, exceptions, outputs, and side effects, incorporating special cases from real defects.
4. **Choose a small transformation.** Remove one delegate before considering whether to merge an engine; do not delete a whole hierarchy in one large commit.
5. **Migrate call sites consistently.** Invoke the surviving abstraction directly, retain API names that must stay stable, and prevent old and new paths from diverging.
6. **Run compiler, unit, regression, and integration tests.** For DI/reflection, verify application startup, bean configuration, serialization, and runtime routing.
7. **Measure actual impact.** Evaluate steps needed to locate logic, classes edited for new requirements, testability, and review clarity. Removing a redundant layer can be more valuable than reducing line counts.
8. **Clean dead code.** Once consumers are absent, remove obsolete imports, classes, interfaces, configuration, and implementation-detail tests while retaining behavioral tests.

~~~mermaid
flowchart TD
    A["Tìm các tầng chỉ forward hoặc hierarchy thừa"] --> B["Xác định client và vai trò contract"]
    B --> C{"Có ranh giới/biến thể/lợi ích thật?"}
    C -->|Có| D["Giữ abstraction, cải thiện naming/API nếu cần"]
    C -->|Không| E["Chốt test hành vi và điểm tương thích"]
    E --> F["Inline/gộp một tầng"]
    F --> G["Chạy compile + regression/integration tests"]
    G --> H{"Hợp đồng và client còn đúng?"}
    H -->|Không| I["Sửa hoặc hoàn tác bước refactor"]
    I --> G
    H -->|Có| J["Xóa đường đi dư, review thiết kế"]
~~~

### Sequence When External Clients Use the Public API

If `PricingPort` is public in a library, immediately changing client calls may be a **breaking change**. A safer approach is:

- Keep a **compatibility adapter** during migration and mark it `@Deprecated` when appropriate.
- Publish the new API, document migration, and update known call sites.
- Check source/binary compatibility and release versions; remove old APIs only in a release allowing breaking changes.
- Verify whether DI configuration, plugins, reflection, and serialized data reference old type names.

In an internal application built as a unit, all call sites may be migrated more directly, but tests and runtime checks are still required.

## 9. Testing Refactoring Correctness

When **the goal is design change rather than functional change**, tests should focus on *client-observable outcomes* instead of class or method counts.

| Test category | Example | What it protects |
|---|---|---|
| Specification test | `quote(100_000, false)` qualifies for free shipping | Business correctness independently of old code |
| Boundary test | 0; 99.999; 100.000; 100.001; 1.000.000 | No branch/condition is lost during inlining |
| Negative test | -1; 1.000.001 | Validation and error types remain correct |
| Differential test | 1.000 random inputs with a fixed seed | Detect differences between old and new paths |
| Side-effect test | No extra I/O/charges beyond the contract | Removing intermediaries adds no side effects |
| Integration test | Start DI and invoke the API through the transport | Type removal does not break wiring/runtime |
| Client compilation | Compile dependent modules, if any | Avoid unintentional public-signature breakage |
| Performance check | Measure affected flows when SLAs exist | Consolidation adds no unnecessary computation/I/O |

### JUnit 5 Example

If the project declares `org.junit.jupiter:junit-jupiter` and tests reside in the same package as the demo class, add `CheckoutRefactoringTest.java`:

~~~java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CheckoutRefactoringTest {
    @Test
    void simplifiedImplementationKeepsBusinessContract() {
        var checkout = new AbstractionRemovalDemo.SimplifiedCheckout();

        assertEquals(
            new AbstractionRemovalDemo.Invoice(0, 0, 100_000),
            checkout.quote(100_000, false)
        );
        assertEquals(
            new AbstractionRemovalDemo.Invoice(25_000, 0, 225_001),
            checkout.quote(250_001, true)
        );
    }

    @Test
    void invalidInputsStillFail() {
        var checkout = new AbstractionRemovalDemo.SimplifiedCheckout();

        assertThrows(IllegalArgumentException.class,
            () -> checkout.quote(-1, false));
        assertThrows(IllegalArgumentException.class,
            () -> checkout.quote(1_000_001, true));
    }

    @Test
    void oldAndNewMatchOnRepresentativeCases() {
        AbstractionRemovalDemo.Checkout oldService =
            new AbstractionRemovalDemo.LegacyCheckout();
        AbstractionRemovalDemo.Checkout newService =
            new AbstractionRemovalDemo.SimplifiedCheckout();

        AbstractionRemovalDemo.verifyContract("old", oldService);
        AbstractionRemovalDemo.verifyContract("new", newService);
        AbstractionRemovalDemo.compareBeforeAfter(oldService, newService);
    }
}
~~~

Demo nested types have package-private access, so place tests in the same package. Production code should organize types and visibility according to the application's architecture.

**Do not rely solely on comparing old and new versions.** If the old version was already wrong, faithfully preserving its defect can still pass differential tests. Expected `Invoice` values in the demo are written **directly from the requirements** as an independent oracle.

## 10. Common Mistakes

**Deleting an interface simply because it has one implementation.** Implementation count is not a complete measure of value. An abstraction at a database, I/O, clock, or payment-gateway boundary may be important for protecting domain code.

**Merging classes based on line counts.** Two 10-line classes should remain separate if they have **independent reasons to change**. Conversely, a chain of 100-line classes doing nothing but forwarding may need simplification.

**Moving everything into a "God Class."** The objective is not to minimize classes at any cost. If consolidation leaves a class with unrelated responsibilities, maintainability deteriorates.

**Refactoring while changing business rules.** If totals change due to a new pricing policy, that is a functional change that must be documented separately. A refactoring preserves behavior only when the contract is verified unchanged.

**Neglecting reflection, DI, or APIs outside the module.** Code may compile but fail at runtime because bean names, annotations, constructor injection, or configuration referenced a removed class.

**Testing only structural details.** A test asserting that `PricingAdapter.calculate()` was called once becomes meaningless after removing `PricingAdapter`. Replace it with tests for `Checkout.quote()` and genuinely relevant side effects.

**Inlining methods by duplicating logic across clients.** Removing a shared method and copying its body into many callers can create `Duplicate Code` and increase change costs. Inline only when the logic belongs to the caller and does not scatter a common rule.

**Ignoring type and visibility constraints.** Moving methods between classes may expose private fields, broaden access, or affect overloads/generics. Use the compiler, tests, and access-modifier reviews.

## 11. Practice Exercises

**Exercise 1 — Classify:** Given `UserRepository` (DB contract), `UserRepositoryImpl` (real SQL), `UserRepositoryProxy` (forwarding only), and `UserService` (business logic), identify the likely middle man. Specify **evidence to verify** before removing it.

**Exercise 2 — Inline Class:** A `NamePrinter` used only by `GreetingService` joins names without any shared formatting rule. Rewrite the code without the helper class. Test normal, empty, and `null` inputs according to the current contract.

**Exercise 3 — Collapse Hierarchy:** `DefaultDiscountCalculator extends DiscountCalculator` but overrides nothing, and no client uses the subclass as a marker. Redesign it as one class and predict effects on constructors and `import` statements.

**Exercise 4 — Scoped Refactoring:** In `AbstractionRemovalDemo`, try removing only `PricingAdapter` while retaining `PricingEngine` if another use case reuses it. Assess whether the remaining class has a clearer responsibility.

**Exercise 5 — Boundary Defect:** Deliberately change `subtotalCents >= FREE_SHIPPING_AT` to `subtotalCents > FREE_SHIPPING_AT` in `SimplifiedCheckout`. Run regression tests, identify exactly which case detects the bug, and restore the correct condition.

**Exercise 6 — Real Review:** Find a long call chain in an application. Diagram it, marking which layers provide contracts, validation, transactions, authorization, I/O, mapping, or mere forwarding. Propose removing **at least one demonstrably redundant layer**, write behavioral tests first, and record the results after testing.

## 12. Completion Criteria

Before removing an abstraction, you should be able to answer three questions:

1. **What does it protect?** A contract, dependency boundary, real variant, or merely a forwarding call?
2. **What will consolidation improve?** Fewer steps to locate logic, fewer edit points, clearer responsibilities, or easier testing without new coupling?
3. **How do we know the refactoring is safe?** Specification, boundary, and exception tests plus checks of affected clients/APIs demonstrate that public behavior remains correct.

When evidence supports all three, **consolidating or removing unnecessary abstractions** makes OOP design more direct without sacrificing clarity or genuine extensibility.


</details>

- [Back to top](#back-to-top)

---

## <a id="refactoring-009">Improving Design Incrementally with Protective Tests</a>

<details>
<summary>View details</summary>

# Improving Design Incrementally with Protective Tests — Java OOP

## 1. Lesson Objectives

**Refactoring** reorganizes source-code internals to improve understandability, extensibility, and testability while **preserving promised observable behavior**. It may include extracting methods, moving responsibilities to appropriate classes, clarifying names, removing duplication, and reducing coupling.

Changing many things at once makes it hard to locate the source of failures. A safer approach uses a short cycle:

**Identify behavior → write protective tests → make a small design change → run tests → review code → repeat.**

A **protective test** (safety-net test) helps detect unintended changes to existing behavior. It cannot prove the entire system is correct, but it provides regression protection while restructuring code.

Do not confuse three kinds of change:

| Change type | Intent | What to verify |
|---|---|---|
| Refactoring | Change structure while preserving contracts | Behavioral tests pass before and after |
| Bug fix | Correct faulty behavior | A reproducing test fails before the fix and passes afterward |
| Feature change | Change or extend a contract | New tests cover requirements, existing regression tests still pass |

**Practical rule:** When a refactoring step unexpectedly fails a test, investigate and resolve the cause before continuing. Do not delete assertions just to make tests green.

## 2. What Should Protective Tests Cover?

A class's OOP contract may include:

- Return values: totals, processing status, and sorting results.
- Changed data: inventory, order state, and published counters.
- Specified exceptions/error signals: empty inputs, negative quantities, and duplicates.
- Meaningful side effects: notifications, repository writes, and payment-gateway calls.
- Boundary rules: exact discount thresholds, deadlines, 0, 1, empty inputs, and numeric limits.

By contrast, tests asserting private-helper call counts, private-field names, loop structure, or arbitrary collection implementations are often **overly coupled to implementation details**. Those details may legitimately change during refactoring.

**A test-review question:** If the algorithm changes completely but clients still receive the same results and promised effects, should the test continue to PASS? For ordinary business unit tests, usually yes.

## 3. When Legacy Code Has No Tests: Characterization Tests

Sometimes an old class needs improvement but lacks complete documentation. A **characterization test** records what it actually does, including surprises, so accidental behavior changes are detectable.

For example, a shipping-fee method currently grants free shipping at **700.000 VND or above**. Before extraction, record cases at 699.999 and 700.000 VND. If refactoring changes >= into >, the test detects the boundary error.

Note that current behavior is not always **desired** behavior. If an old bug is found, record it, clarify the requirement, and fix it separately with tests expressing the correct expectation. Do not freeze a defect merely because characterization tests pass.

Prioritize high-value scenarios: common paths, important thresholds, invalid inputs, side effects, and sensitive money/resources. Hundreds of tests are not needed before every one-line change, but risky code should not be refactored without verification.

## 4. End-to-End Example: Improving Checkout in Several Steps

Use one scenario to examine the structure **before and after**:

- An order contains line items. Each line has a product code, positive quantity, and nonnegative unit price in **whole VND**.
- **Subtotal** sums quantity × unit price; an order must contain at least one line.
- VIP customers receive a **10% subtotal** discount, rounded down using integer division, when subtotal is **at least 500.000 VND**.
- **Payable** = subtotal − discount.
- **Shipping** = 0 VND when payable is **at least 700.000 VND**; otherwise 30.000 VND.
- **Total** = payable + shipping.
- Receipts use a stable text format throughout the lesson.

The initial version combines validation, pricing, and receipt formatting in one method. The refactored version moves pricing into **PricingPolicy**, extracts **ReceiptFormatter**, and leaves **CheckoutService** to coordinate those collaborators.

~~~mermaid
flowchart LR
    subgraph Before["Trước"]
        L["LegacyCheckout: validate + calculate + format"]
    end
    subgraph After["Sau"]
        C["CheckoutService"] --> P["PricingPolicy: validate + calculate"]
        C --> F["ReceiptFormatter: format"]
    end
    T["Bộ kiểm thử hợp đồng"] --> L
    T --> C
~~~

Improvements proceed in this order:
1. Lock down outputs and failure cases with tests.
2. Extract pricing into clearly named methods.
3. Group related pricing methods into a PricingPolicy object.
4. Move formatting responsibility into ReceiptFormatter.
5. Supply CheckoutService collaborators through a constructor for a readable, replaceable workflow.
6. Run the same tests **after every step**. Remove legacy code only after confirming equivalence.

### Standalone Java 11 Program

Save everything in **SafeRefactoringDemo.java**; no external libraries are required. Both versions implement the Checkout interface. One test suite runs against both and compares results with **independently specified expectations**, preventing two equally incorrect implementations from being accepted as equivalent.

~~~java
import java.util.List;
import java.util.Objects;

public class SafeRefactoringDemo {
    static final long VIP_THRESHOLD = 500_000L;
    static final long FREE_SHIPPING_THRESHOLD = 700_000L;
    static final long SHIPPING_FEE = 30_000L;

    static final class Item {
        final String sku;
        final int quantity;
        final long unitPriceVnd;

        Item(String sku, int quantity, long unitPriceVnd) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPriceVnd = unitPriceVnd;
        }
    }

    static final class Order {
        final List<Item> items;
        final boolean vip;

        Order(List<Item> items, boolean vip) {
            this.items = items;
            this.vip = vip;
        }
    }

    static final class Quote {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;
        final String receipt;

        Quote(long subtotal, long discount,
              long shipping, long total, String receipt) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
            this.receipt = receipt;
        }
    }

    interface Checkout {
        Quote quote(Order order);
    }

    // TRƯỚC: một method phải kiểm tra, tính toán, định dạng.
    static final class LegacyCheckout implements Checkout {
        @Override
        public Quote quote(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.quantity <= 0
                        || item.unitPriceVnd < 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineAmount = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineAmount);
            }

            long discount = order.vip && subtotal >= VIP_THRESHOLD
                    ? subtotal / 10L : 0L;
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = payable >= FREE_SHIPPING_THRESHOLD
                    ? 0L : SHIPPING_FEE;
            long total = Math.addExact(payable, shipping);

            String receipt = "subtotal=" + subtotal
                    + ";discount=" + discount
                    + ";shipping=" + shipping
                    + ";total=" + total;

            return new Quote(
                    subtotal, discount, shipping, total, receipt);
        }
    }

    // Đối tượng giá trị trung gian: kết quả tính tiền đã hoàn tất.
    static final class Pricing {
        final long subtotal;
        final long discount;
        final long shipping;
        final long total;

        Pricing(long subtotal, long discount,
                long shipping, long total) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.shipping = shipping;
            this.total = total;
        }
    }

    // SAU bước tách: quy tắc giá được gom vào một lớp.
    static final class PricingPolicy {
        Pricing calculate(Order order) {
            long subtotal = calculateSubtotal(order);
            long discount = calculateDiscount(subtotal, order.vip);
            long payable = Math.subtractExact(subtotal, discount);
            long shipping = calculateShipping(payable);
            long total = Math.addExact(payable, shipping);

            return new Pricing(subtotal, discount, shipping, total);
        }

        private long calculateSubtotal(Order order) {
            if (order == null || order.items == null
                    || order.items.isEmpty()) {
                throw new IllegalArgumentException("Empty order");
            }

            long subtotal = 0L;
            for (Item item : order.items) {
                if (item == null || item.sku == null
                        || item.sku.isBlank()
                        || item.quantity <= 0
                        || item.unitPriceVnd < 0) {
                    throw new IllegalArgumentException("Invalid item");
                }

                long lineAmount = Math.multiplyExact(
                        item.unitPriceVnd, (long) item.quantity);
                subtotal = Math.addExact(subtotal, lineAmount);
            }
            return subtotal;
        }

        private long calculateDiscount(long subtotal, boolean vip) {
            return vip && subtotal >= VIP_THRESHOLD
                    ? subtotal / 10L : 0L;
        }

        private long calculateShipping(long payable) {
            return payable >= FREE_SHIPPING_THRESHOLD
                    ? 0L : SHIPPING_FEE;
        }
    }

    // SAU bước tách: định dạng là một trách nhiệm độc lập.
    static final class ReceiptFormatter {
        String format(Pricing pricing) {
            return "subtotal=" + pricing.subtotal
                    + ";discount=" + pricing.discount
                    + ";shipping=" + pricing.shipping
                    + ";total=" + pricing.total;
        }
    }

    // SAU: class điều phối sử dụng composition và constructor injection.
    static final class CheckoutService implements Checkout {
        private final PricingPolicy pricing;
        private final ReceiptFormatter formatter;

        CheckoutService(PricingPolicy pricing,
                        ReceiptFormatter formatter) {
            this.pricing = Objects.requireNonNull(pricing);
            this.formatter = Objects.requireNonNull(formatter);
        }

        @Override
        public Quote quote(Order order) {
            Pricing result = pricing.calculate(order);
            return new Quote(
                    result.subtotal,
                    result.discount,
                    result.shipping,
                    result.total,
                    formatter.format(result));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // Kỳ vọng nghiệp vụ độc lập với cả hai implementation.
    private static void expectQuote(
            Checkout checkout, Order order,
            long subtotal, long discount,
            long shipping, long total) {
        Quote actual = checkout.quote(order);
        String expectedReceipt = "subtotal=" + subtotal
                + ";discount=" + discount
                + ";shipping=" + shipping
                + ";total=" + total;

        check(actual.subtotal == subtotal, "Unexpected subtotal");
        check(actual.discount == discount, "Unexpected discount");
        check(actual.shipping == shipping, "Unexpected shipping");
        check(actual.total == total, "Unexpected total");
        check(expectedReceipt.equals(actual.receipt),
                "Unexpected receipt text");
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void expectOverflow(Runnable action) {
        try {
            action.run();
        } catch (ArithmeticException expected) {
            return;
        }
        throw new AssertionError("Expected ArithmeticException");
    }

    // CÙNG bộ test bảo vệ được chạy cho cả code cũ và code mới.
    private static void contractTests(Checkout checkout) {
        // Khách thường: 100.000 + 30.000 shipping = 130.000.
        expectQuote(checkout,
                new Order(List.of(new Item("PEN", 1, 100_000L)), false),
                100_000L, 0L, 30_000L, 130_000L);

        // VIP đúng ngưỡng giảm giá:
        // subtotal 500.000, discount 50.000,
        // payable 450.000, shipping 30.000 -> total 480.000.
        expectQuote(checkout,
                new Order(List.of(new Item("BOOK", 2, 250_000L)), true),
                500_000L, 50_000L, 30_000L, 480_000L);

        // VIP subtotal 800.000, discount 80.000,
        // payable 720.000 nên miễn phí shipping.
        expectQuote(checkout,
                new Order(List.of(new Item("BOOK", 2, 400_000L)), true),
                800_000L, 80_000L, 0L, 720_000L);

        // Khách thường, payable 699.999 vẫn chịu phí.
        expectQuote(checkout,
                new Order(List.of(new Item("A", 1, 699_999L)), false),
                699_999L, 0L, 30_000L, 729_999L);

        // Đúng ngưỡng 700.000: miễn phí, bắt lỗi đổi >= thành >.
        expectQuote(checkout,
                new Order(List.of(new Item("A", 1, 700_000L)), false),
                700_000L, 0L, 0L, 700_000L);

        expectInvalid(() -> checkout.quote(null));
        expectInvalid(() -> checkout.quote(new Order(List.of(), false)));
        expectInvalid(() -> checkout.quote(new Order(
                List.of(new Item("A", 0, 100L)), false)));
        expectInvalid(() -> checkout.quote(new Order(
                List.of(new Item("A", 1, -10L)), false)));

        // Math.addExact phải phát hiện tổng vượt phạm vi long.
        expectOverflow(() -> checkout.quote(new Order(List.of(
                new Item("A", 1, Long.MAX_VALUE),
                new Item("B", 1, 1L)), false)));
    }

    public static void main(String[] args) {
        Checkout legacy = new LegacyCheckout();
        Checkout refactored = new CheckoutService(
                new PricingPolicy(), new ReceiptFormatter());

        contractTests(legacy);
        contractTests(refactored);

        System.out.println(
                "PASS: same 5 business cases and 5 error cases in both versions");
    }
}
~~~

Run in a terminal from the file's directory:

~~~bash
javac SafeRefactoringDemo.java
java SafeRefactoringDemo
~~~

Output if all assertions pass:

~~~text
PASS: same 4 business cases and 5 error cases in both versions
~~~

For each implementation, the program has **5 pricing scenarios and 5 failure scenarios**: 4 invalid-input cases and 1 overflow case. Expectations are specified directly in tests rather than copied from legacy code, so the tests verify both equivalence and the stated business rules.

Math.multiplyExact, Math.addExact, and Math.subtractExact make overflow detectable. For currencies with fractional units, use an appropriate type such as BigDecimal and a documented rounding policy; the example intentionally uses whole VND for simplicity and consistency.

## 5. Concrete Improvement Sequence

| Step | Small change | Immediate verification |
|---|---|---|
| 0 | Rerun legacy code with protective tests | Stable passing tests and explicit expectations for important cases |
| 1 | Give subtotal, discount, and payable variables clear names | Results/exceptions unchanged |
| 2 | Extract calculateSubtotal from the large method | Invalid-input behavior, sums, and overflow unchanged |
| 3 | Extract calculateDiscount and calculateShipping | Thresholds at 500.000 and 700.000 preserved |
| 4 | Group related pricing methods in PricingPolicy | Values and promised error ordering unchanged |
| 5 | Extract ReceiptFormatter | Receipt format remains unchanged unintentionally |
| 6 | Inject dependencies into CheckoutService through its constructor | Callers still receive the same contractual Quote |
| 7 | Remove obsolete implementations and simplify names/visibility | Rerun tests, review diffs, and check integrations where needed |

~~~mermaid
flowchart TD
    A["Xác định hợp đồng và rủi ro"] --> B["Viết hoặc cập nhật test bảo vệ"]
    B --> C["Chọn một cải thiện thiết kế nhỏ"]
    C --> D["Biên dịch và chạy test"]
    D --> E{"Mọi test liên quan đều đạt?"}
    E -- "Không" --> F["Điều tra thay đổi hành vi hoặc test quá chặt"]
    F --> C
    E -- "Có" --> G["Review diff, đặt tên và trách nhiệm"]
    G --> H{"Còn cải thiện có ích?"}
    H -- "Có" --> C
    H -- "Không" --> I["Kết thúc refactor và kiểm thử tích hợp phù hợp"]
~~~

Note: The test suite may not protect branches that have never been exercised. Coverage reveals unexecuted code, but **100% line coverage does not guarantee correct assertions or coverage of every business rule**.

## 6. Designing Tests That Do Not Break Needlessly During Refactoring

**Verify public results and effects.** For pricing classes, focus on monetary amounts. For receipt senders, verify the right recipient, content, and timing under the contract.

**Name tests after behavior.** For example, vipDiscountStartsAtThreshold or failedPaymentDoesNotSendReceipt is clearer than testCallsCalculateDiscountTwice.

**Do not freeze irrelevant helper ordering.** Replacing for loops with Stream or HashMap with another structure should not fail business tests when contractual results and guarantees stay the same.

**Use test doubles for the right purpose.** Fake repositories test logic against in-memory data; stubs control payment-gateway results; mocks are appropriate when the external interaction itself is required behavior. They do not replace integration tests with real databases, queues, or APIs.

**Keep tests readable, independent, and repeatable.** Avoid real clocks, randomness, or networks in unit tests unless they are the subject of testing. Inject java.time.Clock, random generators, and collaborators to control inputs.

**Do not compare only old and new outputs.** If the legacy version contains a bug, both may return the same wrong result. Combine before/after comparisons with **independent expected values** derived from requirements and worked examples.

## 7. Refactoring Side Effects Requires Extra Care

The pricing example is nearly pure, but real methods often open transactions, write to databases, send emails, or call payment gateways.

If the original order was:
1. Validate and check inventory.
2. Charge payment.
3. Persist the order.
4. Request a confirmation email.

Extracting methods/classes **must not accidentally** change rules such as "do not email after failed payment" or move transaction boundaries. Test:
- Persisted state and consistency after success or failure.
- External calls that must or must not occur under business rules.
- Truly meaningful ordering, idempotency, and retries where required.
- Contracts between application services and real adapters through integration tests.

Illustrative Mockito + JUnit 5 example, assuming CheckoutService and the interfaces already exist in a real application:

~~~java
when(gateway.charge(orderId, amountVnd)).thenReturn(false);

CheckoutResult result = checkoutService.checkout(orderId);

assertEquals(CheckoutResult.PAYMENT_DECLINED, result);
verifyNoInteractions(receiptSender);
~~~

This is **meaningful interaction verification** because "do not send a receipt when payment fails" is contractual behavior. It differs from checking how many times an internal private helper runs.

## 8. Common Mistakes When Improving Design

| Mistake | Consequence | Mitigation |
|---|---|---|
| Changing business formulas while extracting classes | Difficult to distinguish regressions from feature changes | Separate the changes and test each one |
| Moving hundreds of lines at once | Hard to locate failures | Make small, independently verified transformations |
| Deleting failing tests without investigating | Regressions may be missed | Determine whether the test protects a contract or merely implementation details |
| Measuring only line coverage | False confidence in completeness | Add boundary and failure tests with specific assertions |
| Creating too many one-line classes/methods | Increased fragmentation and coupling | Extract according to actual responsibilities and reasons to change |
| Making private fields public to refactor faster | Breaks encapsulation | Pass data through suitable constructors, methods, or value objects |
| Changing validation order | Different exceptions or side effects | Document and test observable ordering when contractual |
| Assuming a fake perfectly simulates a database | Constraint, transaction, and concurrency defects go undetected | Add integration tests |
| Tests relying on current dates or random IDs | Unstable tests | Inject Clock/ID generators and use fixed values |

**Note on ordering and failures:** If input contains two separate defects, changing validation order can change which exception occurs first. This may be observable behavior; establish the requirements before declaring the reordering safe.

## 9. Organizing Refactoring Work in Real Code

For part of a running system, follow this process:

**Step A — Scope the work and establish a baseline.** Choose one clear responsibility, inspect callers, record existing tests, and identify behavior to preserve. For legacy code, write characterization tests based on current input/output or side effects. Confirm tests are stable before editing.

**Step B — Analyze change points.** A class bloated with validation, pricing, persistence, and presentation often has multiple reasons to change. Identify data flow, object lifecycles, and transaction boundaries. Choose the smallest extraction that improves clarity.

**Step C — Change one thing at a time.** Rename variables, extract methods, move methods, or extract classes. Complete a transformation, compile/test, then continue. Use IDE refactoring tools when convenient, but inspect diffs because the IDE cannot verify every business rule.

**Step D — Refine tests once architecture is clear.** Test public behavior without tightly coupling tests to new helpers. Organize unit/integration tests around responsibility boundaries. Avoid duplicating the entire suite just to test every extracted class.

**Step E — Final verification.** Confirm that no obsolete code path remains implicitly used, public APIs have only intended changes, tests pass, and side effects are verified. If behavior must change, handle it separately with tests expressing the new expectations.

There is no need to wait for the entire system to be "clean" before developing further. Effective refactoring consists of **small, purposeful, evidence-backed steps** near code that needs to change.

## 10. Practice Exercises

**Exercise 1 — Detect a Threshold Bug:** In PricingPolicy, change the free-shipping rule from greater than or equal to 700.000 to greater than 700.000. Add a standard-customer case with subtotal exactly 700.000, expecting shipping = 0. The test must detect the bug.

**Exercise 2 — Detect a Discount Bug:** Change the VIP threshold from greater than or equal to 500.000 to greater than 500.000. The test case with VIP subtotal 500.000 must FAIL.

**Exercise 3 — Extract Method:** Starting with LegacyCheckout, extract validation, subtotal, discount, and shipping logic, then rerun contractTests after every extraction. Record how errors and results remain unchanged.

**Exercise 4 — Extract Class:** Move receipt formatting into a separate class. Then change receipt-format requirements separately and explain why PricingPolicy does not need to change. That is a separate feature change, not a behavior-preserving refactoring.

**Exercise 5 — Characterization Test for a Legacy Bug:** Suppose the old system miscalculates fees for a special case. Write a test reproducing current behavior to protect extraction, then specify the correct expectation in a new bug-fix test and fix it separately.

**Exercise 6 — Integration Boundary:** Sketch a fake PaymentGateway and mock ReceiptSender; prove rejected payments never trigger receipt delivery. Identify additional integration tests needed to verify that the real payment adapter handles failure codes correctly.

## 11. Knowledge Self-Check

1. Why is comparing old and new implementations insufficient when the old one has a bug?
2. Why can tests asserting private-helper call counts prevent legitimate refactoring?
3. When is collaborator interaction order part of a contract?
4. After extracting PricingPolicy and ReceiptFormatter, why should CheckoutService remain the coordinator?
5. If a change causes a failing test, how can you distinguish a business regression from an overly implementation-coupled test?

**Key takeaway:** Protective tests provide fast feedback while improving structure, but the strength of that safety net depends on **which behaviors the tests actually verify**. Refactor in small steps, maintain clear OOP responsibility boundaries, check immediately after each change, and protect normal cases, edge cases, failures, and meaningful side effects.


</details>

- [Back to top](#back-to-top)

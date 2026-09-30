# Core Collection Contracts — Queue and Deque

The final part of the **Core Collection Contracts** milestone adds a stronger concern than storing or looking up values: **processing order**—which element leaves next, and which ends the caller may operate on. `Queue` and `Deque` model that concern directly.

For a set of orders waiting to be processed, indexes may be irrelevant. What matters is whether the earliest order leaves first, whether urgent work can be placed at the front, or whether priority determines the next item.

## <a id="queue-semantics">Queue Semantics</a>

`Queue<E>` models a structure whose primary operations are **inserting an element** and **examining/removing the head for processing**. In an ordinary FIFO queue, first in means first out:

```text
offer A → [A]
offer B → [A, B]
offer C → [A, B, C]
poll    → A
poll    → B
```

```java
Queue<Order> pending = new ArrayDeque<>();
pending.offer(orderA);
pending.offer(orderB);
pending.offer(orderC);

Order next = pending.poll(); // orderA
```

FIFO is the common model, but `Queue` does not require every implementation to use insertion order. `PriorityQueue` is the important counterexample: its head is determined by priority ordering.

When an API accepts `Queue<Order>`, callers can rely on queue operations such as `offer`, `poll`, and `peek`. The exact removal order depends on the selected queue implementation.

For an ordinary processing queue, `ArrayDeque` is often a strong implementation: it has efficient end operations, grows as needed in normal use, and rejects null elements.

Avoid null elements in queues even where a particular implementation may permit them, because `poll` and `peek` use null as the special result for an empty queue. Keeping “real data” separate from “no element” makes the API easier to reason about.

## <a id="deque-semantics">Deque Semantics</a>

`Deque<E>` means **double-ended queue**: elements can be added, examined, and removed at both ends.

```text
             Deque
     first ← [A][B][C] → last
             ↑       ↑
          operate   operate
          here      here
```

```java
Deque<Order> pending = new ArrayDeque<>();

pending.offerLast(normalOrder);
pending.offerFirst(urgentOrder);

Order first = pending.pollFirst();
Order last = pending.pollLast();
```

One contract can represent two common patterns.

### FIFO queue

```java
Deque<Order> queue = new ArrayDeque<>();
queue.offerLast(orderA);
queue.offerLast(orderB);

Order next = queue.pollFirst();
```

### LIFO stack

```java
Deque<Order> stack = new ArrayDeque<>();
stack.push(orderA);
stack.push(orderB);

Order latest = stack.pop(); // orderB
```

For new code, `Deque`/`ArrayDeque` is generally preferred to the legacy `Stack` class because the two-ended contract is explicit and does not inherit the older `Vector` design.

Since Java 21, `Deque` also extends `SequencedCollection`. That matches its defined first/last semantics and gives it the common sequenced vocabulary, including reversed views, alongside the specialized `Deque` methods.

Do not assume every `Deque` implementation has identical costs. `ArrayDeque` uses a resizable circular-array style representation; `LinkedList` uses linked nodes. Choose based on the workload and required characteristics, even when both satisfy the same interface.

## <a id="exception-vs-special-value">Exceptions vs Special Values</a>

`Queue` provides two method families for the same operations. They differ in how failure is reported:

| Intent | Throw on failure | Return special value |
| --- | --- | --- |
| insert | `add(e)` | `offer(e)` |
| remove head | `remove()` | `poll()` |
| inspect head | `element()` | `peek()` |

For an empty queue:

```java
Queue<Order> queue = new ArrayDeque<>();

Order a = queue.poll(); // null
Order b = queue.peek(); // null

// queue.remove();  // NoSuchElementException
// queue.element(); // NoSuchElementException
```

For a capacity-restricted queue, `add(e)` may throw `IllegalStateException` when an element cannot currently be added, while `offer(e)` returns `false`. Implementations such as `ArrayDeque` normally do not have that bounded-capacity failure in regular use, but the paired interface contract remains the same.

Choose the family according to the meaning of empty/full state:

- when “no element available” is normal, `poll/peek` are usually convenient;
- when emptiness violates an invariant and should fail immediately, `remove/element` may be clearer;
- when insertion can legitimately be refused, `offer` reports that outcome through its return value.

`Deque` has analogous pairs for each end, such as `addFirst/offerFirst`, `removeFirst/pollFirst`, and `getFirst/peekFirst`.

## <a id="priority-queue">PriorityQueue</a>

`PriorityQueue` still implements `Queue`, but its “next element” is determined by **priority ordering**, not insertion time.

```java
record Job(String name, int priority) {}

Queue<Job> jobs = new PriorityQueue<>(
        Comparator.comparingInt(Job::priority)
);

jobs.offer(new Job("normal", 50));
jobs.offer(new Job("urgent", 10));
jobs.offer(new Job("low", 90));

System.out.println(jobs.poll().name()); // urgent
```

With this comparator, the smallest priority number is at the head. Without a comparator, elements must provide a suitable natural ordering.

The implementation mental model is a **priority heap**:

```text
do not maintain every element in one fully sorted sequence
        ↓
maintain enough heap structure for the best-priority element to be at the head
        ↓
offer / poll restore the heap invariant
```

In the standard implementation, `offer` and `poll` are O(log n), while inspecting the head with `peek` is O(1).

One important trap: **iterating a `PriorityQueue` does not guarantee priority-sorted order**.

```java
for (Job job : jobs) {
    // Do not assume this order matches repeated poll().
}
```

If values must be consumed in priority order, repeatedly `poll` an appropriate queue, or copy the queue first when the original must be preserved:

```java
Queue<Job> copy = new PriorityQueue<>(jobs);
while (!copy.isEmpty()) {
    System.out.println(copy.poll());
}
```

Elements with equal priority also have no automatic FIFO tie-break. If tie order matters, add another comparator key such as a sequence number.

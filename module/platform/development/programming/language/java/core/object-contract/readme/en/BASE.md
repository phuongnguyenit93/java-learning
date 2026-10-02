# Object Contracts

This module focuses on the contracts that determine how an object is **identified, compared, hashed, represented, and ordered** in Java.

## Learning flow

1. why object contracts exist;
2. identity and equality;
3. `equals`;
4. `hashCode`;
5. the `equals`/`hashCode` contract;
6. `toString`;
7. `Comparable`;
8. `Comparator`;
9. a final contract-design model.

## Why learn this module?

An incorrect `equals` or `hashCode` implementation can break collection behavior even though the code compiles. Ordering also needs a contract aligned with domain requirements, not merely an implementation that happens to sort.

Classes and Objects provides the object foundation; Collections is where these contracts become especially visible. The end goal is intentional, testable equality, hashing, and ordering design.

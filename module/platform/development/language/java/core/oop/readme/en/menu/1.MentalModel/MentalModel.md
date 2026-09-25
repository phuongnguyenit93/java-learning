# OOP Mental Model

## <a id="oop-object-collaboration">Objects as collaborating state + behavior</a>
Object-oriented design models a system as objects with responsibilities, state, and behavior that collaborate through method calls. The useful question is not merely “what data fields exist?” but “which object owns this rule and protects this state?”

## <a id="oop-boundaries">Responsibility and boundaries</a>
A class boundary should hide implementation details and expose behavior that preserves invariants. Cohesion increases when related state and rules live together; coupling increases when many objects know internal details of each other. Good OOP aims for understandable responsibility boundaries rather than maximizing the number of classes.

## <a id="oop-vs-procedural">OOP vs procedural decomposition trade-off</a>
OOP organizes around objects/responsibilities; procedural decomposition organizes around operations and data flow. Neither is universally superior. Use the decomposition that keeps change localized and behavior understandable. Java supports both styles, and modern code often mixes them intentionally.

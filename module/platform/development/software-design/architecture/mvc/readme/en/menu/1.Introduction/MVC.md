# Model-View-Controller (MVC)

## <a id="mvc-what">1. What is MVC?</a>

Model-View-Controller is an architectural pattern that separates an interactive system into three conceptual responsibilities: **Model**, **View**, and **Controller**.

MVC is not a framework and does not inherently require HTTP.

## <a id="mvc-why">2. Why does MVC exist?</a>

When presentation, input handling, and domain or application state are mixed into one block of code, changing the UI or interaction flow can affect unrelated logic.

MVC introduces boundaries so those responsibilities can evolve more independently.

## <a id="mvc-roles">3. The three basic roles</a>

```text
input
  ↓
Controller
  ↓
Model
  ↓
View
```

This is only a mental model. How data returns to the View or how the Controller interacts with the Model depends on the concrete implementation.

## <a id="mvc-boundary">4. MVC does not mean Spring MVC</a>

Spring MVC is a web framework that uses MVC terminology and related ideas in HTTP request/response processing.

The canonical owner of the Spring MVC implementation remains `framework/spring-framework/web`.

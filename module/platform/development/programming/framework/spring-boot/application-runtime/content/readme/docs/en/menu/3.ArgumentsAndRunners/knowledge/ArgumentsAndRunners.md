<a id="back-to-top"></a>

# Application arguments and runners

## Menu
- [How Does `ApplicationArguments` Interpret Command-Line Input?](#application-arguments-model)
- [When Do `ApplicationRunner` and `CommandLineRunner` Execute?](#runner-timing)
- [How Do `ApplicationRunner` and `CommandLineRunner` Differ?](#applicationrunner-vs-commandlinerunner)
- [How Is Multiple-Runner Ordering Controlled?](#runner-ordering)
- [Which Startup Work Belongs in a Runner?](#runner-use-cases)
- [What Work Should Not Block the Runner Phase?](#runner-antipatterns)

## <a id="application-arguments-model">How Does `ApplicationArguments` Interpret Command-Line Input?</a>

<details>
<summary>Click for details</summary>

`ApplicationArguments` is Boot's parsed view of the raw `String[]` passed to `SpringApplication.run`. It keeps the original source arguments and separates option arguments such as `--mode=batch` from non-option arguments such as a positional file name. Code can ask for option names, test whether an option exists, and retrieve its values.

```java
@Component
class ImportRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("dry-run")) {
            // perform validation without committing work
        }
    }
}
```

Command-line options also participate in Boot's externalized configuration model, but property-source precedence belongs to the configuration module. Here the important point is that runners can consume the invocation itself without reparsing raw strings.

</details>

- [Back to top](#back-to-top)

---

## <a id="runner-timing">When Do `ApplicationRunner` and `CommandLineRunner` Execute?</a>

<details>
<summary>Click for details</summary>

Boot calls `ApplicationRunner` and `CommandLineRunner` after the `ApplicationContext` has been refreshed and `ApplicationStartedEvent` has been published. They run before `ApplicationReadyEvent` and before Boot marks readiness as `ACCEPTING_TRAFFIC`.

That timing makes runners a natural place for startup work that needs ordinary beans and must finish before the application is considered ready: validating an application-specific invariant, warming a small required cache, or executing a command-style workload in a non-web application.

The timing also creates responsibility. If a runner blocks for minutes, readiness is delayed for minutes. If a runner throws, startup does not complete normally. Treat the runner phase as part of startup, not as an invisible background thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="applicationrunner-vs-commandlinerunner">How Do `ApplicationRunner` and `CommandLineRunner` Differ?</a>

<details>
<summary>Click for details</summary>

Both runner interfaces occupy the same lifecycle phase; their main difference is the argument shape they receive. `CommandLineRunner.run(String... args)` receives the raw command-line strings. `ApplicationRunner.run(ApplicationArguments args)` receives Boot's parsed representation with option and non-option accessors.

Prefer `ApplicationRunner` when the code cares about command-line options as structured input. Prefer `CommandLineRunner` when the exact raw argument sequence is itself the useful contract. Neither interface is more asynchronous or "later" than the other.

Because they share timing and ordering rules, the choice should be driven by input semantics. Mixing both in one application is valid, but many runner beans can make startup order harder to reason about, so keep responsibilities cohesive.

</details>

- [Back to top](#back-to-top)

---

## <a id="runner-ordering">How Is Multiple-Runner Ordering Controlled?</a>

<details>
<summary>Click for details</summary>

When multiple runners exist, Spring's ordering contract applies. A runner can implement `Ordered` or use `@Order` so Boot invokes the collection in a deterministic relative order. Lower order values have higher precedence.

Ordering is useful when there is a real startup dependency, such as "load reference data before validating a derived index". It should not become a hidden workflow engine. If runner B cannot make sense without runner A, make that dependency obvious in naming, tests, or orchestration rather than scattering many numeric priorities.

If no explicit order is needed, prefer independent runners. If the startup dependency graph becomes large, reconsider whether the work belongs in dedicated application orchestration rather than a growing set of lifecycle callbacks.

</details>

- [Back to top](#back-to-top)

---

## <a id="runner-use-cases">Which Startup Work Belongs in a Runner?</a>

<details>
<summary>Click for details</summary>

Good runner work has three properties: it needs a fully refreshed context, it belongs to application startup, and readiness should wait for its successful completion. Examples include validating runtime prerequisites that cannot be checked earlier, executing a short one-time startup migration owned by the application, or running a command-mode task that intentionally completes and then exits.

Keep the work bounded and observable. Log a clear start/failure outcome, propagate fatal exceptions, and make repeated execution safe when the deployment model may restart the process.

For configuration validation that can happen during binding, use configuration validation instead. For bean construction invariants, use normal bean initialization. A runner is valuable because of its timing; it should not become a generic place to put any code that "runs on startup."

</details>

- [Back to top](#back-to-top)

---

## <a id="runner-antipatterns">What Work Should Not Block the Runner Phase?</a>

<details>
<summary>Click for details</summary>

A runner is a poor home for an endless polling loop, a long-lived message consumer, or CPU-heavy background processing. Those activities prevent the runner phase from finishing and therefore delay `ApplicationReadyEvent` and readiness. Starting unmanaged threads inside a runner also bypasses Boot-managed execution infrastructure and makes shutdown harder to reason about.

If work must continue after startup, submit it to an appropriate managed executor, scheduler, or technology-specific runtime component. If work can happen after traffic begins, do not force readiness to wait without a clear requirement.

Another smell is using a runner to repair missing configuration or swallow fatal initialization errors. Startup should fail clearly when the application cannot meet its required invariants; the failure path is part of the runtime contract.

</details>

- [Back to top](#back-to-top)

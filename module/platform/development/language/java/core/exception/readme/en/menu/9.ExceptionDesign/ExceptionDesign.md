# Exception Design

## <a id="exception-boundaries">Translate exceptions at abstraction boundaries</a>
Translate a lower-level exception when crossing into an abstraction whose callers should reason in different terms. A repository can turn a JDBC-specific failure into a repository/domain failure; a controller boundary can turn domain failure into an HTTP response. Preserve the cause and do not translate merely to rename the same semantics repeatedly.

## <a id="do-not-swallow">Do not swallow failures</a>
An empty catch or catch that only comments/logs and continues can leave the system in a state the caller believes succeeded. Swallow only when ignoring the failure is an explicit, safe policy and the loss of information is understood.

## <a id="logging-boundary">Logging once at the responsible boundary</a>
Logging and rethrowing the same exception at every layer creates duplicate stack traces without adding information. Add context through exception chaining and log where the application has enough context to decide severity, user impact, correlation information, and response policy.

## <a id="exception-as-control-flow">Avoid exceptions as normal control flow</a>
Exceptions are for exceptional contract outcomes, not ordinary branching such as “item not found in a collection” when absence is expected. Exception-based loops/parsing also obscure intent and can be expensive. Model expected alternatives explicitly when they are normal behavior.

## <a id="cleanup-and-recovery">Recovery vs cleanup vs propagation</a>
Cleanup releases resources; recovery restores or chooses a valid alternative; propagation delegates the decision upward. A catch block should know which role it is performing. Catching without the information or authority to recover usually means cleanup plus propagation is the correct design.

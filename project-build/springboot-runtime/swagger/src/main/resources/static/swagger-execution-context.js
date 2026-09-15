const ExecutionContextPlugin = function (system) {
    return {
        wrapComponents: {
            execute: (Original, system) => {
                const React = system.React;

                return function (props) {
                    const operation = props.operation;
                    const enabled = operation?.get?.("x-execution-context-enabled")
                        ?? operation?.["x-execution-context-enabled"];

                    if (!enabled) {
                        return React.createElement(Original, props);
                    }

                    const path = props.path;
                    const method = props.method;

                    if (!path || !method) {
                        return React.createElement(Original, props);
                    }

                    const action = (label, handler, className) => React.createElement(
                        "button",
                        {
                            type: "button",
                            className: `btn execution-context-action opblock-control__btn ${className || ""}`.trim(),
                            onClick: event => {
                                event.preventDefault();
                                event.stopPropagation();
                                handler(path, method);
                            }
                        },
                        label
                    );

                    return React.createElement(
                        React.Fragment,
                        null,
                        React.createElement(Original, props),
                        action("View last execution", viewLastExecution, "view"),
                        action("Export this execution", exportLastExecution, "export")
                    );
                };
            }
        }
    };
};

async function findLastExecution(path, method) {
    const basePath = window.swaggerBasePath || "";
    const params = new URLSearchParams({
        limit: "1",
        path: `${basePath}${path}`,
        httpMethod: method.toUpperCase()
    });

    let response = await fetch(`${basePath}/execution-context/api/executions?${params}`);
    if (!response.ok) {
        throw new Error(`Execution Context query failed (HTTP ${response.status})`);
    }

    let executions = await response.json();

    // Some applications expose requestURI without the servlet context path.
    // Fall back to the OpenAPI path before declaring that no execution exists.
    if (!executions.length && basePath) {
        params.set("path", path);
        response = await fetch(`${basePath}/execution-context/api/executions?${params}`);
        if (!response.ok) {
            throw new Error(`Execution Context query failed (HTTP ${response.status})`);
        }
        executions = await response.json();
    }

    return executions.length ? executions[0] : null;
}

async function viewLastExecution(path, method) {
    try {
        const execution = await findLastExecution(path, method);
        if (!execution) {
            window.alert("No execution has been captured for this operation yet.");
            return;
        }

        const basePath = window.swaggerBasePath || "";
        const target = `${basePath}/execution-context/explorer.html?executionId=${encodeURIComponent(execution.executionId)}`;
        window.open(target, "_blank", "noopener");
    } catch (error) {
        window.alert(error.message);
    }
}

async function exportLastExecution(path, method) {
    try {
        const execution = await findLastExecution(path, method);
        if (!execution) {
            window.alert("No execution has been captured for this operation yet.");
            return;
        }

        const basePath = window.swaggerBasePath || "";
        const url = `${basePath}/execution-context/api/executions/${encodeURIComponent(execution.executionId)}/export`;
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Execution Context export failed (HTTP ${response.status})`);
        }

        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = objectUrl;
        anchor.download = `execution-context-${execution.executionId}.zip`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(objectUrl);
    } catch (error) {
        window.alert(error.message);
    }
}

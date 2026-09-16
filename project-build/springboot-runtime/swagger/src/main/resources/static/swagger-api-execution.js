const ApiExecutionPlugin = function (system) {
    return {
        wrapComponents: {
            responses: (Original, system) => {
                const React = system.React;

                return function (props) {
                    const path = props.path;
                    const method = props.method;
                    const spec = system.specSelectors.specJson();
                    const operation = path && method && spec
                        ? spec.getIn(["paths", path, method.toLowerCase()])
                        : null;
                    const html = operation?.get?.("x-api-execution-html")
                        ?? operation?.["x-api-execution-html"];

                    if (html === undefined || html === null) {
                        return React.createElement(Original, props);
                    }

                    return React.createElement(
                        React.Fragment,
                        null,
                        React.createElement(
                            "div",
                            {className: "api-execution"},
                            React.createElement("h4", null, "Execution"),
                            React.createElement(
                                "div",
                                {
                                    className: "api-execution-html",
                                    dangerouslySetInnerHTML: {__html: html}
                                }
                            )
                        ),
                        React.createElement(Original, props)
                    );
                };
            }
        }
    };
};

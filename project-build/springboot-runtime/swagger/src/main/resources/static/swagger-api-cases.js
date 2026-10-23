// Authored learning scenarios supplied through OpenAPI's x-api-cases extension.
// Native request/response examples are also installed by the server customizer,
// so selecting an example under "Try it out" works without this visual plugin.
// Render cases in the Request Body content, after its header and before Examples.
const ApiCasesPlugin = function (system) {
    return {
        wrapComponents: {
            RequestBody: (Original, system) => {
                const React = system.React;

                const asPlain = value => value && typeof value.toJS === "function"
                    ? value.toJS() : value;

                return function (props) {
                    const spec = system.specSelectors.specJson();
                    // Swagger UI passes ["paths", path, method, "requestBody"].
                    const path = props.specPath?.get?.(1) ?? props.specPath?.[1];
                    const method = props.specPath?.get?.(2) ?? props.specPath?.[2];
                    const operation = path && method && spec
                        ? spec.getIn(["paths", path, method.toLowerCase()])
                        : null;
                    const cases = asPlain(operation?.get?.("x-api-cases")
                        ?? operation?.["x-api-cases"]);
                    if (!Array.isArray(cases) || cases.length === 0) {
                        return React.createElement(Original, props);
                    }

                    const pretty = value => JSON.stringify(value, null, 2) ?? "null";
                    // The YAML author writes detailDescription as HTML. Convert only
                    // safe structural tags into React elements (without attributes)
                    // instead of injecting arbitrary user-authored HTML into the DOM.
                    const safeTags = new Set([
                        "p", "div", "span", "br", "strong", "b", "em", "i",
                        "h2", "h3", "h4", "h5", "h6",
                        "ul", "ol", "li", "pre", "code",
                        "blockquote", "hr", "table", "thead", "tbody",
                        "tr", "th", "td"
                    ]);
                    const blockedTags = new Set([
                        "script", "style", "iframe", "object", "embed",
                        "svg", "math", "form", "input", "button", "textarea",
                        "link", "meta", "base"
                    ]);
                    const renderCaseHtml = html => {
                        const template = document.createElement("template");
                        template.innerHTML = html;
                        const renderNode = (node, key) => {
                            if (node.nodeType === 3) {
                                return node.textContent;
                            }
                            if (node.nodeType !== 1) {
                                return null;
                            }
                            const tag = node.tagName.toLowerCase();
                            if (blockedTags.has(tag)) {
                                return null;
                            }
                            const children = Array.from(node.childNodes)
                                .map((child, index) => renderNode(child, index));
                            return safeTags.has(tag)
                                ? React.createElement(tag, {key}, ...children)
                                : React.createElement(React.Fragment, {key}, ...children);
                        };
                        return React.createElement(
                            "div",
                            {className: "swagger-api-case-details"},
                            ...Array.from(template.content.childNodes)
                                .map((node, index) => renderNode(node, index))
                        );
                    };
                    return React.createElement(
                        React.Fragment, null,
                        React.createElement(
                            "section", {className: "swagger-api-cases"},
                            React.createElement("h4", null, "API Cases"),
                            React.createElement(
                                "p", {className: "swagger-api-cases-intro"},
                                "Choose a named request example in Try it out. ",
                                "Compare the actual response with the expected result below."
                            ),
                            ...cases.map(item => React.createElement(
                                "details", {key: item.id, className: "swagger-api-case"},
                                React.createElement(
                                    "summary", null,
                                    item.title,
                                    " — HTTP ", item.status
                                ),
                                typeof item.detailDescription === "string" &&
                                item.detailDescription.trim()
                                    ? renderCaseHtml(item.detailDescription)
                                    : item.description
                                        // Keep previously authored cases readable.
                                        ? React.createElement("p", null, item.description)
                                        : null,
                                React.createElement("strong", null, "Request"),
                                React.createElement("pre", null,
                                    pretty(item.input ?? {})),
                                React.createElement("strong", null, "Expected response"),
                                React.createElement("pre", null,
                                    pretty(item.response))
                            ))
                        ),
                        React.createElement(Original, props)
                    );
                };
            }
        }
    };
};

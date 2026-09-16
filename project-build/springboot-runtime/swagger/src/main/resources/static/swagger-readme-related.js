const compareSwaggerReadmeOperations = function (first, second) {
    function getEntryValue(entry, key) {
        return entry?.get?.(key)
            ?? entry?.[key];
    }

    function getOperation(entry) {
        return getEntryValue(entry, "operation");
    }

    function getRelated(entry) {
        const operation = getOperation(entry);

        return operation?.get?.("x-readme-related")
            ?? operation?.["x-readme-related"];
    }

    function getRelatedValue(related, key) {
        return related?.get?.(key)
            ?? related?.[key];
    }

    function linkedRank(related) {
        return getRelatedValue(related, "status") === "LINKED"
            ? 0
            : 1;
    }

    function compareNumber(firstNumber, secondNumber) {
        const firstValue = Number.isFinite(Number(firstNumber))
            ? Number(firstNumber)
            : Number.MAX_SAFE_INTEGER;

        const secondValue = Number.isFinite(Number(secondNumber))
            ? Number(secondNumber)
            : Number.MAX_SAFE_INTEGER;

        return firstValue - secondValue;
    }

    function compareText(firstText, secondText) {
        return String(firstText ?? "").localeCompare(
            String(secondText ?? ""),
            undefined,
            {sensitivity: "base"}
        );
    }

    const firstRelated = getRelated(first);
    const secondRelated = getRelated(second);

    const rankComparison = linkedRank(firstRelated) - linkedRank(secondRelated);

    if (rankComparison !== 0) {
        return rankComparison;
    }

    if (linkedRank(firstRelated) === 0) {
        const chapterComparison = compareNumber(
            getRelatedValue(firstRelated, "chapterOrder"),
            getRelatedValue(secondRelated, "chapterOrder")
        );

        if (chapterComparison !== 0) {
            return chapterComparison;
        }

        const sectionComparison = compareNumber(
            getRelatedValue(firstRelated, "sectionOrder"),
            getRelatedValue(secondRelated, "sectionOrder")
        );

        if (sectionComparison !== 0) {
            return sectionComparison;
        }
    }

    const firstOperation = getOperation(first);
    const secondOperation = getOperation(second);

    const signatureComparison = compareText(
        firstOperation?.get?.("x-method-signature")
            ?? firstOperation?.["x-method-signature"],
        secondOperation?.get?.("x-method-signature")
            ?? secondOperation?.["x-method-signature"]
    );

    if (signatureComparison !== 0) {
        return signatureComparison;
    }

    const pathComparison = compareText(
        getEntryValue(first, "path"),
        getEntryValue(second, "path")
    );

    if (pathComparison !== 0) {
        return pathComparison;
    }

    return compareText(
        getEntryValue(first, "method"),
        getEntryValue(second, "method")
    );
};

const SwaggerReadmeRelatedPlugin = function (system) {
    const React = system.React;

    function getValue(value, key) {
        return value?.get?.(key)
            ?? value?.[key];
    }

    function getReadmeRelatedFromSummaryProps(props) {
        const operationProps = props?.operationProps;
        const resolvedOperation = operationProps?.get?.("op")
            ?? operationProps?.op;

        const resolvedReadmeRelated =
            resolvedOperation?.get?.("x-readme-related")
            ?? resolvedOperation?.["x-readme-related"];

        if (resolvedReadmeRelated) {
            return resolvedReadmeRelated;
        }

        const specPath = props?.specPath?.toArray?.()
            ?? props?.specPath?.toJS?.()
            ?? props?.specPath;

        if (!Array.isArray(specPath)) {
            return null;
        }

        const spec = system.specSelectors.specJson();
        const operation = spec?.getIn?.(specPath);

        return operation?.get?.("x-readme-related")
            ?? operation?.["x-readme-related"];
    }

    function renderMethodReadme(readmeRelated) {
        if (!readmeRelated) {
            return null;
        }

        const status = getValue(readmeRelated, "status");
        const displayText = getValue(readmeRelated, "displayText");
        const href = getValue(readmeRelated, "href");

        if (!displayText) {
            return null;
        }

        const className = status === "LINKED"
            ? "swagger-readme-method-related is-linked"
            : status === "UNLINKED"
                ? "swagger-readme-method-related is-unlinked"
                : "swagger-readme-method-related is-invalid";

        const content = status === "LINKED" && href
            ? React.createElement(
                "a",
                {
                    href,
                    className: "swagger-readme-related-link"
                },
                displayText
            )
            : React.createElement(
                "span",
                null,
                "README · ",
                displayText
            );

        return React.createElement(
            "div",
            {className},
            content
        );
    }

    return {
        wrapComponents: {
            OperationTag: (Original) => {
                return function (props) {
                    const tagDetails = props.tagObj?.get?.("tagDetails")
                        ?? props.tagObj?.tagDetails;

                    const readmeRelated = tagDetails?.get?.("x-readme-related")
                        ?? tagDetails?.["x-readme-related"];

                    const status = getValue(readmeRelated, "status");
                    const displayText = getValue(readmeRelated, "displayText");
                    const controllerName = getValue(readmeRelated, "controllerName");

                    const displayTag = status === "LINKED" && displayText
                        ? displayText
                        : controllerName || props.tag;

                    return React.createElement(
                        Original,
                        {
                            ...props,
                            tag: displayTag
                        }
                    );
                };
            },

            OperationSummary: (Original) => {
                return function (props) {
                    const readmeRelated =
                        getReadmeRelatedFromSummaryProps(props);

                    return React.createElement(
                        React.Fragment,
                        null,
                        React.createElement(Original, props),
                        renderMethodReadme(readmeRelated)
                    );
                };
            }
        }
    };
};

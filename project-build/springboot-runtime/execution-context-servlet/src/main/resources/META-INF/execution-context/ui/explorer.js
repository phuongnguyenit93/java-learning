(function () {
    const marker = "/execution-context/";
    const markerIndex = window.location.pathname.indexOf(marker);
    const basePath = markerIndex >= 0
        ? window.location.pathname.substring(0, markerIndex)
        : "";
    const apiBase = `${basePath}/execution-context/api`;

    const state = {
        summaries: [],
        selected: new Set(),
        detailExecutionId: null
    };

    const queryForm = document.getElementById("queryForm");
    const searchInput = document.getElementById("searchInput");
    const fromInput = document.getElementById("fromInput");
    const toInput = document.getElementById("toInput");
    const limitInput = document.getElementById("limitInput");
    const executionRows = document.getElementById("executionRows");
    const resultCount = document.getElementById("resultCount");
    const selectionCount = document.getElementById("selectionCount");
    const selectAllInput = document.getElementById("selectAllInput");
    const exportSelectedButton = document.getElementById("exportSelectedButton");
    const refreshButton = document.getElementById("refreshButton");
    const emptyState = document.getElementById("emptyState");
    const detailPanel = document.getElementById("detailPanel");
    const detailExecutionId = document.getElementById("detailExecutionId");
    const detailContent = document.getElementById("detailContent");
    const exportDetailButton = document.getElementById("exportDetailButton");
    const closeDetailButton = document.getElementById("closeDetailButton");
    const message = document.getElementById("message");

    function buildQuery() {
        const params = new URLSearchParams();
        params.set("limit", limitInput.value || "20");

        if (searchInput.value.trim()) {
            params.set("search", searchInput.value.trim());
        }
        if (fromInput.value) {
            params.set("from", String(new Date(fromInput.value).getTime()));
        }
        if (toInput.value) {
            params.set("to", String(new Date(toInput.value).getTime()));
        }

        const pageParams = new URLSearchParams(window.location.search);
        if (pageParams.get("path")) {
            params.set("path", pageParams.get("path"));
        }
        if (pageParams.get("httpMethod")) {
            params.set("httpMethod", pageParams.get("httpMethod"));
        }

        return params;
    }

    async function loadExecutions() {
        try {
            const response = await fetch(`${apiBase}/executions?${buildQuery()}`);
            if (!response.ok) {
                throw new Error(`Query failed with HTTP ${response.status}`);
            }

            state.summaries = await response.json();
            const availableIds = new Set(state.summaries.map(item => item.executionId));
            state.selected = new Set([...state.selected].filter(id => availableIds.has(id)));
            renderTable();
        } catch (error) {
            showMessage(error.message);
        }
    }

    function renderTable() {
        executionRows.replaceChildren();

        for (const summary of state.summaries) {
            const row = document.createElement("tr");

            row.appendChild(checkboxCell(summary));
            row.appendChild(textCell(formatDate(summary.startedAtEpochMilli)));
            row.appendChild(textCell(summary.httpMethod));
            row.appendChild(textCell(summary.path, "path-cell"));
            row.appendChild(textCell(handlerName(summary), "handler-cell"));
            row.appendChild(statusCell(summary));
            row.appendChild(textCell(`${summary.durationMillis} ms`));
            row.appendChild(textCell(String(summary.logCount)));
            row.appendChild(actionCell(summary));

            executionRows.appendChild(row);
        }

        resultCount.textContent = `${state.summaries.length} execution${state.summaries.length === 1 ? "" : "s"}`;
        emptyState.hidden = state.summaries.length !== 0;
        updateSelectionUi();
    }

    function checkboxCell(summary) {
        const cell = document.createElement("td");
        cell.className = "checkbox-cell";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = state.selected.has(summary.executionId);
        checkbox.setAttribute("aria-label", `Select ${summary.executionId}`);
        checkbox.addEventListener("change", () => {
            if (checkbox.checked) {
                state.selected.add(summary.executionId);
            } else {
                state.selected.delete(summary.executionId);
            }
            updateSelectionUi();
        });
        cell.appendChild(checkbox);
        return cell;
    }

    function textCell(value, className) {
        const cell = document.createElement("td");
        if (className) {
            cell.className = className;
        }
        cell.textContent = value == null ? "" : value;
        cell.title = cell.textContent;
        return cell;
    }

    function statusCell(summary) {
        const cell = textCell(summary.status == null ? "-" : String(summary.status));
        cell.className = summary.failed ? "status-failed" : "status-success";
        return cell;
    }

    function actionCell(summary) {
        const cell = document.createElement("td");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "secondary";
        button.textContent = "View";
        button.addEventListener("click", () => loadDetail(summary.executionId));
        cell.appendChild(button);
        return cell;
    }

    function handlerName(summary) {
        const className = summary.controllerClass || "";
        const simpleClass = className.includes(".")
            ? className.substring(className.lastIndexOf(".") + 1)
            : className;
        return summary.signature
            ? `${simpleClass}#${summary.signature}`
            : simpleClass;
    }

    function formatDate(epochMillis) {
        if (!epochMillis) {
            return "-";
        }
        return new Date(epochMillis).toLocaleString();
    }

    function updateSelectionUi() {
        selectionCount.textContent = `${state.selected.size} selected`;
        exportSelectedButton.disabled = state.selected.size === 0;
        selectAllInput.checked = state.summaries.length > 0
            && state.summaries.every(item => state.selected.has(item.executionId));
        selectAllInput.indeterminate = state.selected.size > 0 && !selectAllInput.checked;
    }

    async function loadDetail(executionId) {
        try {
            const response = await fetch(`${apiBase}/executions/${encodeURIComponent(executionId)}`);
            if (!response.ok) {
                throw new Error(`Unable to load execution ${executionId} (HTTP ${response.status})`);
            }

            const context = await response.json();
            state.detailExecutionId = executionId;
            detailExecutionId.textContent = executionId;
            detailContent.textContent = JSON.stringify(context, null, 2);
            detailPanel.hidden = false;
            detailPanel.scrollIntoView({behavior: "smooth", block: "start"});
        } catch (error) {
            showMessage(error.message);
        }
    }

    async function exportSelected() {
        await exportContexts({executionIds: [...state.selected], query: null});
    }

    async function exportContexts(payload) {
        try {
            const response = await fetch(`${apiBase}/export`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error(`Export failed with HTTP ${response.status}`);
            }

            const blob = await response.blob();
            downloadBlob(blob, filenameFromResponse(response) || "execution-context.zip");
        } catch (error) {
            showMessage(error.message);
        }
    }

    async function exportOne(executionId) {
        try {
            const response = await fetch(`${apiBase}/executions/${encodeURIComponent(executionId)}/export`);
            if (!response.ok) {
                throw new Error(`Export failed with HTTP ${response.status}`);
            }
            const blob = await response.blob();
            downloadBlob(blob, filenameFromResponse(response) || `execution-context-${executionId}.zip`);
        } catch (error) {
            showMessage(error.message);
        }
    }

    function filenameFromResponse(response) {
        const disposition = response.headers.get("Content-Disposition") || "";
        const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
        if (utf8Match) {
            return decodeURIComponent(utf8Match[1]);
        }
        const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
        return plainMatch ? plainMatch[1] : null;
    }

    function downloadBlob(blob, filename) {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
    }

    function showMessage(text) {
        message.textContent = text;
        message.hidden = false;
        window.setTimeout(() => {
            message.hidden = true;
        }, 4500);
    }

    queryForm.addEventListener("submit", event => {
        event.preventDefault();
        loadExecutions();
    });

    refreshButton.addEventListener("click", loadExecutions);
    exportSelectedButton.addEventListener("click", exportSelected);

    selectAllInput.addEventListener("change", () => {
        if (selectAllInput.checked) {
            state.summaries.forEach(item => state.selected.add(item.executionId));
        } else {
            state.summaries.forEach(item => state.selected.delete(item.executionId));
        }
        renderTable();
    });

    exportDetailButton.addEventListener("click", () => {
        if (state.detailExecutionId) {
            exportOne(state.detailExecutionId);
        }
    });

    closeDetailButton.addEventListener("click", () => {
        detailPanel.hidden = true;
        state.detailExecutionId = null;
    });

    const pageParams = new URLSearchParams(window.location.search);
    const executionId = pageParams.get("executionId");
    const search = pageParams.get("search");
    if (search) {
        searchInput.value = search;
    }

    loadExecutions().then(() => {
        if (executionId) {
            loadDetail(executionId);
        }
    });
})();

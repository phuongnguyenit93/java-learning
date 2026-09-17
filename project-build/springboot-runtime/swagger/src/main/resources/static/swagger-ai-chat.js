(function () {
    const providers = [
        {id: "chatgpt", name: "ChatGPT", mark: "GPT", available: true, description: "OpenAI assistant"},
        {id: "claude", name: "Claude", mark: "CL", description: "Anthropic assistant"},
        {id: "grok", name: "Grok", mark: "GR", description: "xAI assistant"},
        {id: "gemini", name: "Gemini", mark: "GE", description: "Google assistant"},
        {id: "cursor", name: "Cursor", mark: "CU", description: "AI code editor"},
        {id: "github-copilot", name: "GitHub Copilot", mark: "GH", description: "Developer assistant"},
        {id: "microsoft-copilot", name: "Microsoft Copilot", mark: "MS", description: "Microsoft assistant"},
        {id: "perplexity", name: "Perplexity", mark: "PX", description: "AI answer engine"},
        {id: "deepseek", name: "DeepSeek", mark: "DS", description: "AI assistant"},
        {id: "mistral", name: "Mistral Le Chat", mark: "MI", description: "Mistral assistant"},
        {id: "meta-ai", name: "Meta AI", mark: "ME", description: "Meta assistant"},
        {id: "amazon-q", name: "Amazon Q", mark: "AQ", description: "AWS assistant"},
        {id: "jetbrains-ai", name: "JetBrains AI", mark: "JB", description: "IDE assistant"},
        {id: "windsurf", name: "Windsurf", mark: "WS", description: "AI development environment"},
        {id: "cline", name: "Cline", mark: "CN", description: "Coding agent"},
        {id: "continue", name: "Continue", mark: "CO", description: "Coding assistant"},
        {id: "ollama", name: "Ollama", mark: "OL", description: "Local model runtime"},
        {id: "openrouter", name: "OpenRouter", mark: "OR", description: "Model gateway"}
    ];

    const state = {
        installed: false,
        connected: false,
        selectedProvider: null,
        provider: null,
        connection: null,
        messages: [],
        connectionAttempt: 0
    };

    function installSwaggerAiChat() {
        if (state.installed) {
            return;
        }

        const connectButton = document.getElementById("swagger-ai-connect-action");
        if (!connectButton) {
            return;
        }

        state.installed = true;
        createModal();
        createChat();

        connectButton.addEventListener("click", () => {
            if (state.connected) {
                openChat();
                return;
            }
            openProviderWizard();
        });

        document.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                closeModal();
            }
        });
    }

    function createModal() {
        const overlay = document.createElement("div");
        overlay.id = "swagger-ai-modal-overlay";
        overlay.className = "swagger-ai-modal-overlay";
        overlay.hidden = true;
        overlay.innerHTML = `
            <section class="swagger-ai-modal" role="dialog" aria-modal="true" aria-labelledby="swagger-ai-modal-title">
                <header class="swagger-ai-modal-header">
                    <div>
                        <h2 id="swagger-ai-modal-title" class="swagger-ai-modal-title">Connect AI</h2>
                        <p id="swagger-ai-modal-subtitle" class="swagger-ai-modal-subtitle">Choose an AI provider to connect with Swagger.</p>
                    </div>
                    <button type="button" class="swagger-ai-icon-button" data-ai-modal-close aria-label="Close">×</button>
                </header>
                <div id="swagger-ai-modal-body" class="swagger-ai-modal-body"></div>
            </section>
        `;

        overlay.addEventListener("click", event => {
            if (event.target === overlay || event.target.closest("[data-ai-modal-close]")) {
                closeModal();
            }
        });

        document.body.appendChild(overlay);
    }

    function createChat() {
        const panel = document.createElement("section");
        panel.id = "swagger-ai-chat-panel";
        panel.className = "swagger-ai-chat-panel";
        panel.hidden = true;
        panel.innerHTML = `
            <header class="swagger-ai-chat-header">
                <div class="swagger-ai-chat-identity">
                    <div class="swagger-ai-chat-provider">
                        <span class="swagger-ai-status-dot" aria-hidden="true"></span>
                        <span data-ai-chat-provider>ChatGPT</span>
                    </div>
                    <div class="swagger-ai-chat-status">MCP Connected</div>
                </div>
                <div class="swagger-ai-chat-actions">
                    <button type="button" class="swagger-ai-icon-button" data-ai-chat-minimize aria-label="Minimize">−</button>
                    <button type="button" class="swagger-ai-icon-button" data-ai-chat-settings aria-label="Connection settings">⚙</button>
                    <button type="button" class="swagger-ai-icon-button" data-ai-chat-close aria-label="Close chat">×</button>
                </div>
            </header>
            <div id="swagger-ai-chat-messages" class="swagger-ai-chat-messages"></div>
            <form id="swagger-ai-chat-compose" class="swagger-ai-chat-compose">
                <input
                    id="swagger-ai-chat-input"
                    class="swagger-ai-chat-input"
                    type="text"
                    autocomplete="off"
                    placeholder="Ask about this application..."
                    aria-label="AI chat message"
                />
                <button class="swagger-ai-chat-send" type="submit" aria-label="Send">➤</button>
            </form>
        `;

        const launcher = document.createElement("button");
        launcher.id = "swagger-ai-chat-launcher";
        launcher.className = "swagger-ai-chat-launcher";
        launcher.type = "button";
        launcher.hidden = true;
        launcher.textContent = "● ChatGPT";

        panel.querySelector("[data-ai-chat-minimize]").addEventListener("click", minimizeChat);
        panel.querySelector("[data-ai-chat-close]").addEventListener("click", minimizeChat);
        panel.querySelector("[data-ai-chat-settings]").addEventListener("click", openConnectionSettings);
        panel.querySelector("#swagger-ai-chat-compose").addEventListener("submit", handleChatSubmit);
        launcher.addEventListener("click", openChat);

        document.body.appendChild(panel);
        document.body.appendChild(launcher);
    }

    function openProviderWizard() {
        state.selectedProvider = null;
        setModalHeading(
            "Connect AI",
            "Choose an AI provider to connect with Swagger."
        );
        renderProviderStep();
        openModal();
    }

    function renderProviderStep() {
        const body = modalBody();
        body.innerHTML = `
            ${renderStepper(1)}
            <h3 class="swagger-ai-section-heading">Choose provider</h3>
            <p class="swagger-ai-section-copy">ChatGPT is available in this prototype. Other integrations are visible now so the UI can grow without being redesigned later.</p>
            <div class="swagger-ai-provider-grid">
                ${providers.map(renderProviderCard).join("")}
            </div>
        `;

        body.querySelectorAll("[data-ai-provider]").forEach(card => {
            card.addEventListener("click", () => selectProvider(card.dataset.aiProvider));
        });
    }

    function renderProviderCard(provider) {
        const availableClass = provider.available ? " is-available" : "";
        const status = provider.available ? "Available" : "In development";

        return `
            <button type="button" class="swagger-ai-provider-card${availableClass}" data-ai-provider="${provider.id}">
                <span class="swagger-ai-provider-card-top">
                    <span class="swagger-ai-provider-mark">${provider.mark}</span>
                    <span class="swagger-ai-provider-status">${status}</span>
                </span>
                <span class="swagger-ai-provider-name">${provider.name}</span>
                <span class="swagger-ai-provider-description">${provider.description}</span>
            </button>
        `;
    }

    function selectProvider(providerId) {
        const provider = providers.find(candidate => candidate.id === providerId);
        if (!provider) {
            return;
        }

        state.selectedProvider = provider;
        setModalHeading("Connect AI", `${provider.name} connection`);

        if (provider.available) {
            renderChatGptConnectionStep();
            return;
        }

        renderComingSoonStep(provider);
    }

    function renderChatGptConnectionStep() {
        const body = modalBody();

        body.innerHTML = `
            ${renderStepper(2)}
            <h3 class="swagger-ai-section-heading">ChatGPT Connection</h3>
            <p class="swagger-ai-section-copy">Prototype tunnel configuration only. No MCP tunnel is started yet.</p>

            <form id="swagger-ai-connection-form" class="swagger-ai-form">
                <div class="swagger-ai-field">
                    <label for="swagger-ai-tunnel-type">Tunnel type</label>
                    <select id="swagger-ai-tunnel-type" class="swagger-ai-select">
                        <option value="openai-secure-mcp-tunnel">OpenAI Secure MCP Tunnel</option>
                    </select>
                </div>

                <div class="swagger-ai-field">
                    <label for="swagger-ai-tunnel-id">Tunnel ID</label>
                    <input id="swagger-ai-tunnel-id" class="swagger-ai-input" type="text" placeholder="Enter tunnel ID" autocomplete="off" aria-describedby="swagger-ai-tunnel-id-error" />
                    <span id="swagger-ai-tunnel-id-error" class="swagger-ai-field-error" hidden>Tunnel ID is required.</span>
                </div>

                <div class="swagger-ai-field">
                    <label for="swagger-ai-tunnel-api-key">Tunnel API Key</label>
                    <div class="swagger-ai-secret-row">
                        <input id="swagger-ai-tunnel-api-key" class="swagger-ai-input" type="password" placeholder="Enter tunnel API key" autocomplete="new-password" aria-describedby="swagger-ai-tunnel-api-key-error" />
                        <button type="button" class="swagger-ai-secondary-button" data-ai-toggle-secret>Show</button>
                    </div>
                    <span class="swagger-ai-field-hint">Prototype only: the raw tunnel key is never logged and is cleared after Connect.</span>
                    <span id="swagger-ai-tunnel-api-key-error" class="swagger-ai-field-error" hidden>Tunnel API Key is required.</span>
                </div>

                <details class="swagger-ai-advanced">
                    <summary>Advanced</summary>
                    <div class="swagger-ai-advanced-content">
                        Reserved for future integration. Planned settings include:
                        <ul>
                            <li>Public MCP URL</li>
                            <li>Cloudflare settings</li>
                            <li>Authentication</li>
                            <li>Timeout</li>
                        </ul>
                    </div>
                </details>

                <div class="swagger-ai-modal-actions">
                    <button type="button" class="swagger-ai-secondary-button" data-ai-back>Back</button>
                    <div class="swagger-ai-modal-actions-right">
                        <button type="button" class="swagger-ai-secondary-button" data-ai-modal-close>Cancel</button>
                        <button type="submit" class="swagger-ai-primary-button">Connect</button>
                    </div>
                </div>
            </form>
        `;

        body.querySelector("[data-ai-back]").addEventListener("click", () => {
            setModalHeading("Connect AI", "Choose an AI provider to connect with Swagger.");
            renderProviderStep();
        });

        body.querySelector("[data-ai-modal-close]").addEventListener("click", closeModal);
        body.querySelector("[data-ai-toggle-secret]").addEventListener("click", toggleSecretVisibility);
        body.querySelector("#swagger-ai-connection-form").addEventListener("submit", handleConnectionSubmit);
        body.querySelector("#swagger-ai-tunnel-id").addEventListener("input", clearTunnelIdValidationError);
        body.querySelector("#swagger-ai-tunnel-api-key").addEventListener("input", clearTunnelApiKeyValidationError);
    }

    function renderComingSoonStep(provider) {
        const body = modalBody();
        body.innerHTML = `
            ${renderStepper(2)}
            <div class="swagger-ai-coming-soon">
                <div class="swagger-ai-coming-soon-icon">🚧</div>
                <h3 class="swagger-ai-section-heading">${provider.name}</h3>
                <p class="swagger-ai-section-copy">Integration is under development.<br />Support for ${provider.name} will be added in a future version.</p>
                <button type="button" class="swagger-ai-secondary-button" data-ai-back>Back</button>
            </div>
        `;

        body.querySelector("[data-ai-back]").addEventListener("click", () => {
            setModalHeading("Connect AI", "Choose an AI provider to connect with Swagger.");
            renderProviderStep();
        });
    }

    function renderStepper(activeStep) {
        const providerClass = activeStep === 1 ? "is-active" : "is-complete";
        const connectionClass = activeStep === 2 ? "is-active" : "";

        return `
            <div class="swagger-ai-stepper" aria-label="Connection steps">
                <div class="swagger-ai-step ${providerClass}">
                    <span class="swagger-ai-step-number">1</span>
                    <span>Provider</span>
                </div>
                <div class="swagger-ai-step ${connectionClass}">
                    <span class="swagger-ai-step-number">2</span>
                    <span>Connection</span>
                </div>
            </div>
        `;
    }

    function toggleSecretVisibility(event) {
        const input = document.getElementById("swagger-ai-tunnel-api-key");
        if (!input) {
            return;
        }

        const visible = input.type === "text";
        input.type = visible ? "password" : "text";
        event.currentTarget.textContent = visible ? "Show" : "Hide";
    }

    function handleConnectionSubmit(event) {
        event.preventDefault();

        const tunnelType = document.getElementById("swagger-ai-tunnel-type")?.value || "openai-secure-mcp-tunnel";
        const tunnelIdInput = document.getElementById("swagger-ai-tunnel-id");
        const tunnelApiKeyInput = document.getElementById("swagger-ai-tunnel-api-key");
        const tunnelId = tunnelIdInput?.value?.trim() || "";
        const hasTunnelApiKey = Boolean(tunnelApiKeyInput?.value?.trim());

        if (!tunnelId) {
            showTunnelIdValidationError(tunnelIdInput);
            return;
        }

        if (!hasTunnelApiKey) {
            showTunnelApiKeyValidationError(tunnelApiKeyInput);
            return;
        }

        const connection = {
            provider: "chatgpt",
            providerName: "ChatGPT",
            tunnelType,
            tunnelId,
            hasTunnelApiKey
        };

        console.log("[SWAGGER-AI] Prototype connection submitted", {
            provider: connection.provider,
            tunnelType: connection.tunnelType,
            tunnelId: connection.tunnelId,
            hasTunnelApiKey: connection.hasTunnelApiKey
        });

        if (tunnelApiKeyInput) {
            tunnelApiKeyInput.value = "";
        }

        renderConnectingState(connection);
    }

    function showTunnelIdValidationError(tunnelIdInput) {
        const error = document.getElementById("swagger-ai-tunnel-id-error");
        tunnelIdInput?.classList.add("is-invalid");
        tunnelIdInput?.setAttribute("aria-invalid", "true");
        if (error) {
            error.hidden = false;
        }
        tunnelIdInput?.focus();
    }

    function clearTunnelIdValidationError(event) {
        const input = event.currentTarget;
        const error = document.getElementById("swagger-ai-tunnel-id-error");
        input.classList.remove("is-invalid");
        input.removeAttribute("aria-invalid");
        if (error) {
            error.hidden = true;
        }
    }

    function showTunnelApiKeyValidationError(tunnelApiKeyInput) {
        const error = document.getElementById("swagger-ai-tunnel-api-key-error");
        tunnelApiKeyInput?.classList.add("is-invalid");
        tunnelApiKeyInput?.setAttribute("aria-invalid", "true");
        if (error) {
            error.hidden = false;
        }
        tunnelApiKeyInput?.focus();
    }

    function clearTunnelApiKeyValidationError(event) {
        const input = event.currentTarget;
        const error = document.getElementById("swagger-ai-tunnel-api-key-error");
        input.classList.remove("is-invalid");
        input.removeAttribute("aria-invalid");
        if (error) {
            error.hidden = true;
        }
    }

    function renderConnectingState(connection) {
        const attempt = ++state.connectionAttempt;
        setModalHeading("Connect AI", "Preparing the prototype MCP connection.");

        modalBody().innerHTML = `
            <div class="swagger-ai-loading">
                <div class="swagger-ai-loading-spinner" aria-hidden="true"></div>
                <h3 class="swagger-ai-section-heading">Connecting ChatGPT through MCP...</h3>
                <div class="swagger-ai-progress-list">
                    <div id="swagger-ai-progress-config" class="swagger-ai-progress-item is-active">• Validating tunnel configuration...</div>
                    <div id="swagger-ai-progress-session" class="swagger-ai-progress-item">• Preparing secure MCP tunnel...</div>
                </div>
                <div id="swagger-ai-connect-success" class="swagger-ai-success" hidden>MCP Connected ✓</div>
            </div>
        `;

        window.setTimeout(() => {
            if (attempt !== state.connectionAttempt) {
                return;
            }
            const config = document.getElementById("swagger-ai-progress-config");
            const session = document.getElementById("swagger-ai-progress-session");
            if (config) {
                config.className = "swagger-ai-progress-item is-done";
                config.textContent = "✓ Tunnel configuration accepted";
            }
            if (session) {
                session.className = "swagger-ai-progress-item is-active";
            }
        }, 320);

        window.setTimeout(() => {
            if (attempt !== state.connectionAttempt) {
                return;
            }
            const session = document.getElementById("swagger-ai-progress-session");
            const success = document.getElementById("swagger-ai-connect-success");
            if (session) {
                session.className = "swagger-ai-progress-item is-done";
                session.textContent = "✓ MCP tunnel prepared";
            }
            if (success) {
                success.hidden = false;
            }
        }, 700);

        window.setTimeout(() => {
            if (attempt !== state.connectionAttempt) {
                return;
            }
            finishPrototypeConnection(connection);
        }, 1050);
    }

    function finishPrototypeConnection(connection) {
        state.connected = true;
        state.provider = providers.find(provider => provider.id === connection.provider) || providers[0];
        state.connection = {
            provider: connection.provider,
            providerName: connection.providerName,
            tunnelType: connection.tunnelType,
            tunnelId: connection.tunnelId,
            hasTunnelApiKey: connection.hasTunnelApiKey
        };

        if (state.messages.length === 0) {
            state.messages.push({
                role: "assistant",
                title: "Welcome 👋",
                text: "Ask me about this API."
            });
        }

        updateConnectedUi();
        closeModal();
        openChat();
    }

    function updateConnectedUi() {
        const button = document.getElementById("swagger-ai-connect-action");
        const providerName = state.provider?.name || "ChatGPT";
        const panelProvider = document.querySelector("[data-ai-chat-provider]");
        const launcher = document.getElementById("swagger-ai-chat-launcher");

        if (button) {
            button.textContent = "MCP Connected ✓";
            button.classList.add("is-connected");
        }
        if (panelProvider) {
            panelProvider.textContent = providerName;
        }
        if (launcher) {
            launcher.textContent = `● ${providerName}`;
        }

        renderMessages();
    }

    function openChat() {
        if (!state.connected) {
            openProviderWizard();
            return;
        }

        const panel = document.getElementById("swagger-ai-chat-panel");
        const launcher = document.getElementById("swagger-ai-chat-launcher");
        if (panel) {
            panel.hidden = false;
        }
        if (launcher) {
            launcher.hidden = true;
        }

        renderMessages();
        window.setTimeout(() => document.getElementById("swagger-ai-chat-input")?.focus(), 0);
    }

    function minimizeChat() {
        if (!state.connected) {
            return;
        }

        const panel = document.getElementById("swagger-ai-chat-panel");
        const launcher = document.getElementById("swagger-ai-chat-launcher");
        if (panel) {
            panel.hidden = true;
        }
        if (launcher) {
            launcher.hidden = false;
        }
    }

    function handleChatSubmit(event) {
        event.preventDefault();
        if (!state.connected) {
            return;
        }

        const input = document.getElementById("swagger-ai-chat-input");
        const message = input?.value?.trim();
        if (!message) {
            return;
        }

        const timestamp = new Date().toISOString();
        state.messages.push({role: "user", text: message});

        console.log("[SWAGGER-AI] Chat message", {
            provider: state.connection?.provider || "chatgpt",
            message,
            timestamp
        });

        if (input) {
            input.value = "";
        }
        renderMessages();

        window.setTimeout(() => {
            if (!state.connected) {
                return;
            }
            state.messages.push({
                role: "assistant",
                title: "Prototype mode",
                text: "Prototype only — AI connection is not enabled yet."
            });
            renderMessages();
        }, 420);
    }

    function renderMessages() {
        const container = document.getElementById("swagger-ai-chat-messages");
        if (!container) {
            return;
        }

        container.replaceChildren();
        state.messages.forEach(message => {
            const row = document.createElement("div");
            row.className = `swagger-ai-message-row is-${message.role}`;

            const bubble = document.createElement("div");
            bubble.className = "swagger-ai-message-bubble";

            if (message.title) {
                const title = document.createElement("span");
                title.className = "swagger-ai-message-title";
                title.textContent = message.title;
                bubble.appendChild(title);
            }

            const text = document.createElement("span");
            text.textContent = message.text;
            bubble.appendChild(text);
            row.appendChild(bubble);
            container.appendChild(row);
        });

        container.scrollTop = container.scrollHeight;
    }

    function openConnectionSettings() {
        if (!state.connected || !state.connection) {
            return;
        }

        setModalHeading("Connection", "Current MCP connection settings.");
        const body = modalBody();
        body.innerHTML = `
            <h3 class="swagger-ai-section-heading">${escapeHtml(state.connection.providerName)}</h3>
            <p class="swagger-ai-section-copy">MCP Connected</p>
            <div class="swagger-ai-connection-summary">
                ${connectionRow("Provider", state.connection.providerName)}
                ${connectionRow("Tunnel type", tunnelTypeLabel(state.connection.tunnelType))}
                ${connectionRow("Tunnel ID", state.connection.tunnelId)}
                ${connectionRow("Tunnel API Key", state.connection.hasTunnelApiKey ? "Configured ✓" : "Not configured")}
            </div>
            <div class="swagger-ai-modal-actions">
                <button type="button" class="swagger-ai-danger-button" data-ai-disconnect>Disconnect</button>
                <div class="swagger-ai-modal-actions-right">
                    <button type="button" class="swagger-ai-secondary-button" data-ai-modal-close>Close</button>
                </div>
            </div>
        `;

        body.querySelector("[data-ai-disconnect]").addEventListener("click", disconnectAi);
        body.querySelector("[data-ai-modal-close]").addEventListener("click", closeModal);
        openModal();
    }

    function connectionRow(label, value) {
        return `
            <div class="swagger-ai-connection-row">
                <span>${escapeHtml(label)}</span>
                <strong>${escapeHtml(value || "")}</strong>
            </div>
        `;
    }

    function tunnelTypeLabel(tunnelType) {
        if (tunnelType === "openai-secure-mcp-tunnel") {
            return "OpenAI Secure MCP Tunnel";
        }
        return tunnelType || "Not configured";
    }

    function disconnectAi() {
        state.connectionAttempt++;
        state.connected = false;
        state.selectedProvider = null;
        state.provider = null;
        state.connection = null;
        state.messages = [];

        const button = document.getElementById("swagger-ai-connect-action");
        const panel = document.getElementById("swagger-ai-chat-panel");
        const launcher = document.getElementById("swagger-ai-chat-launcher");
        if (button) {
            button.textContent = "Connect AI";
            button.classList.remove("is-connected");
        }
        if (panel) {
            panel.hidden = true;
        }
        if (launcher) {
            launcher.hidden = true;
        }

        console.log("[SWAGGER-AI] Prototype disconnected");
        closeModal();
    }

    function openModal() {
        const overlay = document.getElementById("swagger-ai-modal-overlay");
        if (overlay) {
            overlay.hidden = false;
        }
    }

    function closeModal() {
        const overlay = document.getElementById("swagger-ai-modal-overlay");
        if (overlay) {
            overlay.hidden = true;
        }
    }

    function setModalHeading(title, subtitle) {
        const titleElement = document.getElementById("swagger-ai-modal-title");
        const subtitleElement = document.getElementById("swagger-ai-modal-subtitle");
        if (titleElement) {
            titleElement.textContent = title;
        }
        if (subtitleElement) {
            subtitleElement.textContent = subtitle;
        }
    }

    function modalBody() {
        return document.getElementById("swagger-ai-modal-body");
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    window.installSwaggerAiChat = installSwaggerAiChat;

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", installSwaggerAiChat, {once: true});
    } else {
        installSwaggerAiChat();
    }
})();

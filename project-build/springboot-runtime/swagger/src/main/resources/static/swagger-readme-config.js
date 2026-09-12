let rootReadmeMarkdown = null;

/**
 * Intercept link tới README markdown.
 *
 * Thay vì browser navigate/download file .md,
 * fetch nội dung rồi replace info.description của Swagger.
 */
function installReadmeNavigation() {

    document.addEventListener("click", async function (event) {

        const link = event.target.closest("a");

        if (!link) {
            return;
        }

        const href = link.getAttribute("href");

        if (!href) {
            return;
        }

        // ============================
        // Back to top
        // ============================
        if (href === "#back-to-top") {
            event.preventDefault();

            // Scroll vùng description về đầu
            const description = document.querySelector(
                ".swagger-ui .information-container .description"
            );

            if (description) {
                description.scrollTo({
                    top: 0,
                    left: 0,
                    behavior: "smooth"
                });
            }

            // Scroll cả trang về đầu Swagger info
            const informationContainer = document.querySelector(
                ".swagger-ui .information-container"
            );

            if (informationContainer) {
                informationContainer.scrollIntoView({
                    behavior: "smooth",
                    block: "start"
                });
            }

            return;
        }

        // ============================
        // Quay lại README chính
        // ============================

        if (href === "#swagger-readme-home") {

            event.preventDefault();

            restoreRootReadme();

            return;
        }


        // ============================
        // Load markdown con
        // ============================

        const url = new URL(link.href);

        if (!url.pathname.includes(`${basePath}/readme/`)) {
            return;
        }

        if (!url.pathname.toLowerCase().endsWith(".md")) {
            return;
        }

        event.preventDefault();

        try {

            const response = await fetch(url.href);

            if (!response.ok) {
                console.error(
                    "Cannot load README:",
                    response.status,
                    url.href
                );

                return;
            }

            let markdown = await response.text();

            markdown = rewriteRelativeLinks(
                markdown,
                url
            );

            updateSwaggerDescription(markdown);

        } catch (error) {

            console.error(
                "Failed to load README:",
                error
            );
        }


    });
}


/**
 * Thay info.description trong spec hiện tại.
 *
 * Swagger UI sẽ tự render Markdown.
 */
function updateSwaggerDescription(markdown) {

    if (!window.ui) {
        console.error("Swagger UI is not initialized");
        return;
    }

    const immutableSpec =
        window.ui.specSelectors.specJson();

    if (!immutableSpec) {
        console.error("Swagger spec is not available");
        return;
    }

    const spec = immutableSpec.toJS();

    spec.info = spec.info || {};

    // Lưu README gốc duy nhất một lần
    if (rootReadmeMarkdown === null) {
        rootReadmeMarkdown = spec.info.description || "";
    }

    // Thêm nút/link quay lại README gốc
    const navigation = `
[← Quay lại README chính](#swagger-readme-home)

---

`;

    spec.info.description =
        navigation + markdown;

    window.ui.specActions.updateJsonSpec(spec);

    scrollToSwaggerInfo();
}


/**
 * Khi Markdown con có:
 *
 *   [ThreadLocal](../2.Context/ThreadLocal.md)
 *
 * Browser bình thường sẽ resolve relative path dựa vào
 * custom-swagger.html, không phải file .md hiện tại.
 *
 * Vì vậy convert relative URL thành URL dựa trên source .md.
 */
function rewriteRelativeLinks(markdown, sourceUrl) {

    return markdown.replace(
        /(!?\[[^\]]*])\(([^)]+)\)/g,
        function (match, label, href) {

            // #anchor
            if (href.startsWith("#")) {
                return match;
            }

            // http / https / mailto / data...
            if (/^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(href)) {
                return match;
            }

            try {

                const resolved =
                    new URL(href, sourceUrl);

                return `${label}(${resolved.href})`;

            } catch (e) {
                return match;
            }
        }
    );
}

function restoreRootReadme() {

    if (!window.ui || rootReadmeMarkdown === null) {
        return;
    }

    const immutableSpec =
        window.ui.specSelectors.specJson();

    if (!immutableSpec) {
        return;
    }

    const spec = immutableSpec.toJS();

    spec.info = spec.info || {};

    spec.info.description = rootReadmeMarkdown;

    window.ui.specActions.updateJsonSpec(spec);

    scrollToSwaggerInfo();
}

function scrollToSwaggerInfo() {

    setTimeout(() => {

        const info =
            document.querySelector(
                ".information-container"
            );

        if (info) {
            info.scrollIntoView({
                behavior: "smooth",
                block: "start"
            });
        }

    }, 0);
}
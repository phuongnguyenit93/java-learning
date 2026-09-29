const YoutubePlugin = function (system) {
    return {
        wrapComponents: {

            OperationTag: (Original, system) => {

                const React = system.React;

                return function (props) {

                    const {
                        tag,
                        tagObj
                    } = props;

                    console.log("🔥 OperationTag");
                    console.log("tag =", tag);
                    console.log("tagObj =", tagObj);


                    // ==========================================
                    // tagDetails
                    // ==========================================

                    const tagDetails =
                        tagObj?.get?.("tagDetails")
                        ?? tagObj?.tagDetails;


                    console.log(
                        "tagDetails =",
                        tagDetails
                    );


                    // ==========================================
                    // x-custom-html
                    // ==========================================

                    const html =
                        tagDetails?.get?.("x-custom-html")
                        ?? tagDetails?.["x-custom-html"];


                    console.log(
                        "html =",
                        html
                    );


                    // ==========================================
                    // Custom HTML
                    // ==========================================

                    const customHtml =
                        html
                            ? React.createElement(
                                "div",
                                {
                                    className:
                                        "swagger-custom-html",

                                    dangerouslySetInnerHTML: {
                                        __html: html
                                    }
                                }
                            )
                            : null;


                    // ==========================================
                    // Original
                    // ==========================================

                    return React.createElement(
                        React.Fragment,
                        null,

                        customHtml,

                        React.createElement(
                            Original,
                            props
                        )
                    );
                };
            },

            /*
             * Ta chỉ chỉnh sửa phần parameter và render Youtube phía trên phần đó
             * Các phần như description , bodyResponse sẽ không can thiệp vào
             */
            parameters: (Original, system) => {
                const React = system.React;

                return function (props) {

                    const {pathMethod, specSelectors} = props;

                    // console.log("🔥 Parameters wrapped");
                    // console.log("pathMethod:", pathMethod);

                    /*
                     * pathMethod có dạng:
                     * ["paths", "/basic/example-thread", "get"]
                     * hoặc tùy version có thể là:
                     * ["/basic/example-thread", "get"]
                     * Vì vậy ta xử lý cả hai.
                     */

                    let path;
                    let method;

                    if (pathMethod?.length === 3) {
                        path = pathMethod[1];
                        method = pathMethod[2];
                    } else if (pathMethod?.length === 2) {
                        path = pathMethod[0];
                        method = pathMethod[1];
                    }

                    /*
                     * Nếu không xác định được API
                     * thì render Swagger bình thường.
                     */
                    if (!path || !method) {
                        return React.createElement(
                            Original,
                            props
                        );
                    }

                    /*
                     * Lấy OpenAPI document.
                     */
                    const spec =
                        specSelectors.specJson();

                    if (!spec) {
                        return React.createElement(
                            Original,
                            props
                        );
                    }

                    /*
                     * Lấy operation từ OpenAPI.
                     */
                    const operation =
                        spec.getIn([
                            "paths",
                            path,
                            method.toLowerCase()
                        ]);

                    // console.log(
                    //     "Swagger operation:",
                    //     operation?.toJS()
                    // );

                    if (!operation) {
                        return React.createElement(
                            Original,
                            props
                        );
                    }

                    /*
                     * Lấy custom extension.
                     */

                    const youtube =
                        operation.get("x-youtube");

                    // console.log(
                    //     "x-youtube:",
                    //     youtube?.toJS()
                    // );

                    /*
                     * API không có video
                     * => Swagger UI bình thường.
                     */
                    if (!youtube) {
                        return React.createElement(
                            Original,
                            props
                        );
                    }

                    const videoId =
                        youtube.get("videoId");

                    const title =
                        youtube.get("title") ||
                        "YouTube Video";

                    if (!videoId) {
                        return React.createElement(
                            Original,
                            props
                        );
                    }

                    /*
                     * YouTube component.
                     */
                    const youtubeComponent =
                        React.createElement(
                            "div",
                            {
                                className:
                                    "swagger-youtube"
                            },

                            React.createElement(
                                "div",
                                {
                                    className:
                                        "swagger-youtube-title"
                                },
                                "🎥 ",
                                title
                            ),

                            React.createElement(
                                "div",
                                {
                                    className:
                                        "swagger-youtube-player"
                                },

                                React.createElement(
                                    "iframe",
                                    {
                                        src:
                                            "https://www.youtube.com/embed/"
                                            + videoId,

                                        title: title,

                                        width: "560",
                                        height: "315",

                                        frameBorder: "0",

                                        allow:
                                            "accelerometer; " +
                                            "autoplay; " +
                                            "clipboard-write; " +
                                            "encrypted-media; " +
                                            "gyroscope; " +
                                            "picture-in-picture",

                                        allowFullScreen: true
                                    }
                                )
                            )
                        );

                    /*
                     * Render:
                     * YouTube
                     *     ↓
                     * Original Parameters
                     */
                    return React.createElement(
                        React.Fragment,
                        null,

                        youtubeComponent,

                        React.createElement(
                            Original,
                            props
                        )
                    );
                };
            }
        }
    };
};
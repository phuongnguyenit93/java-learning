SBA.use({
    install({ viewRegistry }) {
        viewRegistry.addView({
            name: 'instances/proxy-inspector',
            parent: 'instances',
            path: 'proxy-inspector',
            component: {
                // Setup là trái tim của Vue 3
                setup() {
                    // Trả về một hàm render
                    return () => SBA.h('div', { class: 'section' }, [
                        SBA.h('div', { class: 'card' }, [
                            SBA.h('div', { class: 'card-content' }, [
                                SBA.h('h1', { class: 'title has-text-primary' }, 'Hello World!'),
                                SBA.h('p', { class: 'subtitle' }, 'Nếu thấy dòng này, Vue 3 Composition API đã chạy.')
                            ])
                        ])
                    ]);
                }
            },
            label: 'Proxy Inspector',
            order: 1000
        });
    }
});
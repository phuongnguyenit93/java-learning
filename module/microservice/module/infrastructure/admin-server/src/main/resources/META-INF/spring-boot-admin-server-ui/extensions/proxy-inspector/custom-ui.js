SBA.use({
    install({ viewRegistry }) {
        const Vue = window.SBA.Vue || window.Vue;
        const { h, ref, onMounted, computed } = Vue;

        const custom = {
            props: ['instance'],
            setup(props) {
                const beans = ref([]);
                const loading = ref(true);
                const expandedBean = ref(null);
                const filterText = ref("");

                const fetchBeans = async () => {
                    try {
                        const response = await props.instance.axios.get('actuator/beans');
                        const contexts = response.data.contexts;
                        const flattened = [];
                        if (contexts) {
                            Object.values(contexts).forEach(ctx => {
                                Object.keys(ctx.beans).forEach(name => {
                                    flattened.push({ name, ...ctx.beans[name] });
                                });
                            });
                        }
                        beans.value = flattened;
                    } catch (e) { console.error(e); } finally { loading.value = false; }
                };

                const filteredBeans = computed(() => {
                    if (!filterText.value) return beans.value;
                    const search = filterText.value.toLowerCase();
                    return beans.value.filter(bean =>
                        bean.name.toLowerCase().includes(search) ||
                        (bean.type && bean.type.toLowerCase().includes(search))
                    );
                });

                const toggleBean = (name) => {
                    expandedBean.value = expandedBean.value === name ? null : name;
                };

                onMounted(fetchBeans);

                return () => h('div', { class: 'section', style: { padding: '20px', width: '100%' } }, [
                    h('div', { class: 'level' }, [
                        h('div', { class: 'level-left' }, [h('h1', { class: 'title' }, 'Proxy Inspector')]),
                        h('div', { class: 'level-right' }, [
                            h('div', { class: 'field' }, [
                                h('p', { class: 'control has-icons-left' }, [
                                    h('input', {
                                        class: 'input is-rounded',
                                        type: 'text',
                                        placeholder: 'Filter beans...',
                                        style: { width: '300px' }, // Cố định độ rộng ô search
                                        value: filterText.value,
                                        onInput: (e) => { filterText.value = e.target.value; }
                                    }),
                                    h('span', { class: 'icon is-small is-left' }, [h('i', { class: 'fas fa-filter' })])
                                ])
                            ])
                        ])
                    ]),

                    // Thêm style width: 100% và min-width để tránh bị co quá nhỏ
                    h('div', { class: 'box', style: { padding: '0', width: '100%', minWidth: '100%' } }, [
                        loading.value ? h('p', { class: 'p-4' }, 'Loading...') : h('table', {
                            class: 'table is-fullwidth is-hoverable',
                            style: { width: '100%', tableLayout: 'fixed' } // tableLayout: fixed giúp các cột được chia tỉ lệ chính xác
                        }, [
                            h('thead', null, [
                                h('tr', null, [
                                    h('th', { style: { width: '50px' } }, ''),
                                    h('th', null, `Beans (${filteredBeans.value.length})`)
                                ])
                            ]),
                            h('tbody', null, filteredBeans.value.flatMap(bean => {
                                const isExpanded = expandedBean.value === bean.name;
                                return [
                                    h('tr', {
                                        key: bean.name,
                                        onClick: () => toggleBean(bean.name),
                                        style: { cursor: 'pointer' }
                                    }, [
                                        h('td', { style: { verticalAlign: 'middle' } }, h('span', { class: 'icon' }, [
                                            h('i', { class: `fas ${isExpanded ? 'fa-chevron-down' : 'fa-chevron-right'}` })
                                        ])),
                                        h('td', { style: { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, [
                                            h('div', { style: { fontWeight: 'bold', fontSize: '14px', color: '#363636' } }, bean.name),
                                            h('div', { style: { fontSize: '11px', color: '#7a7a7a', marginTop: '2px' } }, bean.type)
                                        ])
                                    ]),
                                    // --- CHI TIẾT (DETAIL) ---
                                    isExpanded ? h('tr', { key: bean.name + '-detail' }, [
                                        h('td', { colspan: '2', style: { backgroundColor: '#fcfcfc', padding: '1.5rem' } }, [
                                            h('div', { class: 'content', style: { width: '100%' } }, [
                                                h('div', { style: { marginBottom: '15px' } }, [
                                                    h('p', { style: { fontSize: '11px', color: '#b5b5b5', textTransform: 'uppercase', marginBottom: '5px' } }, 'Runtime Implementation Class'),
                                                    h('code', { style: { wordBreak: 'break-all', display: 'block', padding: '10px', backgroundColor: '#ececec', borderRadius: '4px', color: '#d33' } }, bean.className || 'N/A')
                                                ]),
                                                h('div', null, [
                                                    h('p', { style: { fontSize: '11px', color: '#b5b5b5', textTransform: 'uppercase', marginBottom: '5px' } }, 'Proxy Dependencies Mapping'),
                                                    bean.dependencyClasses ? h('table', {
                                                        class: 'table is-bordered is-narrow is-fullwidth',
                                                        style: { fontSize: '12px', width: '100%', tableLayout: 'fixed' }
                                                    }, [
                                                        h('thead', null, [
                                                            h('tr', null, [
                                                                h('th', { style: { width: '25%', backgroundColor: '#f9f9f9' } }, 'Field Name'),
                                                                h('th', { style: { width: '35%', backgroundColor: '#f9f9f9' } }, 'Declared Type'),
                                                                h('th', { style: { backgroundColor: '#f9f9f9' } }, 'Actual Implementation Class')
                                                            ])
                                                        ]),
                                                        h('tbody', null, Object.entries(bean.dependencyClasses).map(([dep, clazz]) => {
                                                            const [declaredType, actualClass] = clazz.includes('|') ? clazz.split('|') : ['N/A', clazz];
                                                            return h('tr', { key: dep }, [
                                                                h('td', { style: { fontWeight: '600', wordBreak: 'break-word' } }, dep),
                                                                h('td', { style: { color: '#7a7a7a', wordBreak: 'break-word' } }, declaredType.trim()),
                                                                h('td', { style: { wordBreak: 'break-all' } }, h('code', { style: { color: '#4a4a4a', fontSize: '11px' } }, actualClass.trim()))
                                                            ]);
                                                        }))
                                                    ]) : h('p', { style: { fontSize: '12px', color: '#ccc' } }, 'No complex proxy dependencies found.')
                                                ])
                                            ])
                                        ])
                                    ]) : null
                                ].filter(Boolean);
                            }))
                        ])
                    ])
                ]);
            }
        };

        // --- ĐĂNG KÝ VIEW VÀ ICON ---
        viewRegistry.addView({
            name: "instances/proxy-inspector",
            path: "proxy-inspector",
            component: custom,
            parent: "instances",
            group: "proxy-inspector",
            label: "Proxy Inspector",
            order: 1000,
        });

        SBA.viewRegistry.setGroupIcon(
            "proxy-inspector",
            `<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 mr-3" viewBox="0 0 24 24" fill="currentColor">
          <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
       </svg>`
        );
    },
});
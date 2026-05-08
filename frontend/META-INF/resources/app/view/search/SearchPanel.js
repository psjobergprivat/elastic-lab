Ext.define('ElasticLab.view.search.SearchPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'elasticlab-search',
    layout: 'border',

    indexName: null,
    mappings: null,
    flatFields: null,
    mappingTypes: null,
    rootNode: null,
    renderingBuilder: false,
    builderRenderTask: null,
    searchCurrentPage: 1,
    searchPageSize: 50,

    items: [
        {
            region: 'north',
            xtype: 'toolbar',
            height: 40,
            items: [
                {
                    xtype: 'combobox',
                    itemId: 'indexCombo',
                    fieldLabel: 'Index',
                    labelWidth: 40,
                    width: 260,
                    queryMode: 'local',
                    displayField: 'name',
                    valueField: 'name',
                    editable: false,
                    store: { fields: ['name'], data: [] },
                    listeners: {
                        select: function (combo, record) {
                            combo.up('elasticlab-search').onIndexSelected(record.get('name'));
                        }
                    }
                },
                {
                    text: 'Reload mapping',
                    handler: function (btn) { btn.up('elasticlab-search').reloadMapping(); }
                },
                '->',
                {
                    xtype: 'button',
                    itemId: 'searchBtn',
                    text: 'Search',
                    cls: 'elasticlab-search-btn',
                    handler: function (btn) { btn.up('elasticlab-search').runSearch(); }
                }
            ]
        },
        {
            region: 'center',
            xtype: 'panel',
            layout: 'border',
            items: [
                {
                    region: 'center',
                    xtype: 'panel',
                    layout: 'border',
                    items: [
                        {
                            region: 'center',
                            xtype: 'panel',
                            title: 'Query Builder',
                            itemId: 'builder',
                            scrollable: true,
                            bodyPadding: 6
                        },
                        {
                            region: 'east',
                            xtype: 'panel',
                            title: 'Frontend Query Viewer',
                            itemId: 'viewer',
                            width: 420,
                            split: true,
                            scrollable: true,
                            bodyPadding: 6,
                            html: '<pre style="margin:0;font-family:monospace;font-size:12px"></pre>'
                        }
                    ]
                },
                {
                    region: 'south',
                    xtype: 'panel',
                    height: '50%',
                    split: true,
                    layout: 'border',
                    items: [
                        {
                            region: 'center',
                            xtype: 'panel',
                            itemId: 'resultsArea',
                            title: 'Results',
                            layout: 'card',
                            items: [
                                {
                                    xtype: 'grid',
                                    itemId: 'resultsList',
                                    emptyText: 'No results yet.',
                                    tbar: [
                                        '->',
                                        { xtype: 'tbtext', itemId: 'searchTotal', text: '' },
                                        '-',
                                        {
                                            xtype: 'button',
                                            itemId: 'searchPrevPage',
                                            text: '◀',
                                            disabled: true,
                                            handler: function (btn) {
                                                var view = btn.up('elasticlab-search');
                                                view.executeSearch(view.searchCurrentPage - 1);
                                            }
                                        },
                                        { xtype: 'tbtext', itemId: 'searchPageInfo', text: '' },
                                        {
                                            xtype: 'button',
                                            itemId: 'searchNextPage',
                                            text: '▶',
                                            disabled: true,
                                            handler: function (btn) {
                                                var view = btn.up('elasticlab-search');
                                                view.executeSearch(view.searchCurrentPage + 1);
                                            }
                                        }
                                    ],
                                    store: { fields: ['id', 'score', 'source'] },
                                    columns: [
                                        { text: 'ID', dataIndex: 'id', width: 260 },
                                        { text: 'Score', dataIndex: 'score', width: 80 },
                                        {
                                            text: 'Source',
                                            dataIndex: 'source',
                                            flex: 1,
                                            renderer: function (value) {
                                                return Ext.util.Format.htmlEncode(Ext.JSON.encode(value));
                                            }
                                        }
                                    ],
                                    listeners: {
                                        itemclick: function (grid, record) {
                                            grid.up('elasticlab-search').showDetail(record.data);
                                        }
                                    }
                                },
                                {
                                    xtype: 'panel',
                                    itemId: 'resultsDetail',
                                    layout: 'border',
                                    items: [
                                        {
                                            region: 'north',
                                            xtype: 'toolbar',
                                            items: [
                                                {
                                                    text: 'Back to list',
                                                    handler: function (btn) { btn.up('elasticlab-search').showList(); }
                                                },
                                                '-',
                                                { xtype: 'tbtext', itemId: 'detailTitle', text: '' }
                                            ]
                                        },
                                        {
                                            region: 'center',
                                            xtype: 'panel',
                                            itemId: 'detailBody',
                                            scrollable: true,
                                            bodyPadding: 8,
                                            html: ''
                                        }
                                    ]
                                }
                            ]
                        },
                        {
                            region: 'east',
                            xtype: 'panel',
                            title: 'Elastic Query Viewer',
                            itemId: 'elasticViewer',
                            width: 420,
                            split: true,
                            scrollable: true,
                            bodyPadding: 6,
                            html: '<pre style="margin:0;font-family:monospace;font-size:12px">(run a search to see the query sent to Elastic)</pre>'
                        }
                    ]
                }
            ]
        }
    ],

    listeners: {
        afterrender: function (panel) { panel.initialize(); }
    },

    initialize: function () {
        var me = this;
        me.rootNode = null;
        Ext.Ajax.request({
            url: '/api/metadata/indices',
            method: 'GET',
            success: function (response) {
                var names = Ext.decode(response.responseText) || [],
                    combo = me.down('#indexCombo');
                combo.getStore().loadData(names.map(function (n) { return { name: n }; }));
                if (names.length) {
                    combo.setValue(names[0]);
                    me.onIndexSelected(names[0]);
                } else {
                    me.renderBuilder();
                    me.refreshViewer();
                }
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    onIndexSelected: function (name) {
        this.indexName = name;
        this.reloadMapping();
    },

    reloadMapping: function () {
        var me = this;
        if (!me.indexName) return;
        Ext.Ajax.request({
            url: '/api/metadata/indices/' + encodeURIComponent(me.indexName),
            method: 'GET',
            success: function (response) {
                var data = Ext.decode(response.responseText) || {};
                me.mappings = data.mappings || {};
                me.flatFields = me.flattenMapping(me.mappings);
                me.mappingTypes = me.uniqueTypes(me.flatFields);
                me.renderBuilder();
                me.refreshViewer();
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    flattenMapping: function (mappingsRoot) {
        var out = [];
        var walk = function (props, prefix) {
            if (!props) return;
            Ext.Object.each(props, function (key, def) {
                if (!def || typeof def !== 'object') return;
                var path = prefix ? prefix + '.' + key : key;
                if (def.properties) {
                    walk(def.properties, path);
                }
                if (def.type) {
                    out.push({ path: path, type: def.type });
                }
                if (def.fields) {
                    Ext.Object.each(def.fields, function (subKey, subDef) {
                        if (subDef && subDef.type) {
                            out.push({ path: path + '.' + subKey, type: subDef.type });
                        }
                    });
                }
            });
        };
        walk(mappingsRoot.properties, '');
        return out;
    },

    uniqueTypes: function (flatFields) {
        var seen = {}, out = [];
        flatFields.forEach(function (f) {
            if (!seen[f.type]) { seen[f.type] = true; out.push(f.type); }
        });
        return out.sort();
    },

    createGroupNode: function (op) {
        return { kind: 'group', operator: op || 'AND', children: [] };
    },
    createNotNode: function () {
        return { kind: 'not', child: null };
    },
    createPropertyNode: function () {
        return { kind: 'property', path: '', valueType: 'text', value: '' };
    },
    createTypeAllNode: function () {
        return { kind: 'typeAll', valueType: (this.mappingTypes && this.mappingTypes[0]) || 'text', value: '' };
    },
    createGlobalNode: function () {
        return { kind: 'global', value: '' };
    },
    createFreeTextNode: function () {
        return { kind: 'freeText', value: '' };
    },

    ensureRootNode: function () {
        this.rootNode = this.normalizeNode(this.rootNode);
        return this.rootNode;
    },

    normalizeNode: function (node) {
        var me = this;
        if (!node || typeof node !== 'object') return null;

        switch (node.kind) {
            case 'group':
                if (['AND', 'OR'].indexOf(node.operator) < 0) {
                    node.operator = 'AND';
                }
                node.children = (Array.isArray(node.children) ? node.children : []).map(function (child) {
                    return me.normalizeNode(child);
                }).filter(Boolean);
                return node;
            case 'not':
                node.child = me.normalizeNode(node.child);
                return node;
            case 'property':
                node.path = me.normalizeString(node.path);
                node.valueType = me.normalizeString(node.valueType) || 'text';
                node.value = me.normalizeString(node.value);
                return node;
            case 'typeAll':
                node.valueType = me.normalizeString(node.valueType) || (me.mappingTypes && me.mappingTypes[0]) || 'text';
                node.value = me.normalizeString(node.value);
                return node;
            case 'global':
            case 'freeText':
                node.value = me.normalizeString(node.value);
                return node;
            default:
                return null;
        }
    },

    normalizeString: function (value) {
        return value == null ? '' : String(value);
    },

    requestBuilderRender: function () {
        var me = this;
        if (me.builderRenderTask) {
            clearTimeout(me.builderRenderTask);
        }
        me.builderRenderTask = Ext.defer(function () {
            me.builderRenderTask = null;
            me.renderBuilder();
        }, 1);
    },

    renderBuilder: function () {
        var builder = this.down('#builder'),
            config,
            component;
        if (!builder) return;

        this.ensureRootNode();
        try {
            config = this.buildChildConfig(this.rootNode, this.rootSlot());
            this.renderingBuilder = true;
            component = Ext.ComponentManager.create(config, 'panel');
        } catch (e) {
            this.renderingBuilder = false;
            if (window.console && console.error) {
                console.error('Failed to render query builder', e);
            }
            return;
        }

        Ext.suspendLayouts();
        try {
            builder.removeAll();
            builder.add(component);
        } finally {
            this.renderingBuilder = false;
            Ext.resumeLayouts(true);
        }
    },

    rootSlot: function () {
        var me = this;
        return {
            replace: function (n) { me.rootNode = n; },
            remove: function () { me.rootNode = null; }
        };
    },

    groupChildSlot: function (group, child) {
        return {
            replace: function (n) {
                var i = group.children.indexOf(child);
                if (i >= 0) group.children[i] = n;
            },
            remove: function () { Ext.Array.remove(group.children, child); }
        };
    },

    notChildSlot: function (notNode) {
        return {
            replace: function (n) { notNode.child = n; },
            remove: function () { notNode.child = null; }
        };
    },

    buildChildConfig: function (node, slot) {
        if (!node) return this.buildEmptyPicker(slot);
        if (node.kind === 'group') return this.buildGroupConfig(node, slot);
        if (node.kind === 'not') return this.buildNotConfig(node, slot);
        return this.buildLeafConfig(node, slot);
    },

    buildEmptyPicker: function (slot) {
        var me = this,
            put = function (factory) {
                return function () {
                    slot.replace(factory.call(me));
                    me.requestBuilderRender();
                    me.refreshViewer();
                };
            };
        return {
            xtype: 'toolbar',
            margin: '2 0',
            items: [
                { xtype: 'tbtext', text: '(empty — pick a query type)' },
                '->',
                { text: '+ Criterion', handler: put(me.createPropertyNode) },
                { text: '+ Type query', handler: put(me.createTypeAllNode) },
                { text: '+ Global', handler: put(me.createGlobalNode) },
                { text: '+ Free text', handler: put(me.createFreeTextNode) },
                { text: '+ Group', handler: put(function () { return me.createGroupNode('AND'); }) },
                { text: '+ Not', handler: put(me.createNotNode) }
            ]
        };
    },

    buildGroupConfig: function (node, slot) {
        var me = this,
            tbar = [
                {
                    xtype: 'combobox',
                    width: 80,
                    editable: false,
                    store: ['AND', 'OR'],
                    value: node.operator,
                    listeners: {
                        change: function (c, v) {
                            if (me.renderingBuilder || ['AND', 'OR'].indexOf(v) < 0) return;
                            node.operator = v;
                            me.refreshViewer();
                        }
                    }
                },
                { xtype: 'tbtext', text: 'group' },
                '->',
                { text: '+ Criterion', handler: function () { node.children.push(me.createPropertyNode()); me.requestBuilderRender(); me.refreshViewer(); } },
                { text: '+ Type query', handler: function () { node.children.push(me.createTypeAllNode()); me.requestBuilderRender(); me.refreshViewer(); } },
                { text: '+ Global', handler: function () { node.children.push(me.createGlobalNode()); me.requestBuilderRender(); me.refreshViewer(); } },
                { text: '+ Free text', handler: function () { node.children.push(me.createFreeTextNode()); me.requestBuilderRender(); me.refreshViewer(); } },
                { text: '+ Group', handler: function () { node.children.push(me.createGroupNode('AND')); me.requestBuilderRender(); me.refreshViewer(); } },
                { text: '+ Not', handler: function () { node.children.push(me.createNotNode()); me.requestBuilderRender(); me.refreshViewer(); } }
            ];
        if (slot) {
            tbar.push('-');
            tbar.push({
                text: 'Delete group',
                handler: function () {
                    slot.remove();
                    me.requestBuilderRender();
                    me.refreshViewer();
                }
            });
        }
        return {
            xtype: 'panel',
            frame: true,
            margin: '4 0',
            bodyPadding: 6,
            tbar: tbar,
            items: (node.children || []).map(function (child) {
                return me.buildChildConfig(child, me.groupChildSlot(node, child));
            }).filter(Boolean)
        };
    },

    buildNotConfig: function (node, slot) {
        var me = this,
            tbar = [
                { xtype: 'tbtext', text: 'NOT' },
                '->'
            ];
        if (slot) {
            tbar.push({
                text: 'Delete NOT',
                handler: function () {
                    slot.remove();
                    me.requestBuilderRender();
                    me.refreshViewer();
                }
            });
        }
        return {
            xtype: 'panel',
            frame: true,
            margin: '4 0',
            bodyPadding: 6,
            cls: 'elasticlab-not-panel',
            tbar: tbar,
            items: [me.buildChildConfig(node.child, me.notChildSlot(node))]
        };
    },

    buildLeafConfig: function (node, slot) {
        var me = this,
            items = [
                {
                    xtype: 'combobox',
                    width: 110,
                    editable: false,
                    store: [
                        ['property', 'Property'],
                        ['typeAll', 'Type'],
                        ['global', 'Global'],
                        ['freeText', 'Free text']
                    ],
                    value: node.kind,
                    listeners: {
                        change: function (c, v) {
                            if (me.renderingBuilder || v === node.kind) return;
                            me.swapKind(slot, v);
                        }
                    }
                }
            ].concat(me.buildLeafInputs(node));

        items.push('->');
        if (slot) {
            items.push({
                xtype: 'button',
                text: 'Remove',
                handler: function () {
                    slot.remove();
                    me.requestBuilderRender();
                    me.refreshViewer();
                }
            });
        }

        return {
            xtype: 'toolbar',
            margin: '2 0',
            items: items
        };
    },

    buildLeafInputs: function (node) {
        var me = this;
        switch (node.kind) {
            case 'property': return me.buildPropertyInputs(node);
            case 'typeAll': return me.buildTypeAllInputs(node);
            case 'global': return me.buildGlobalInputs(node);
            case 'freeText': return me.buildFreeTextInputs(node);
            default: return [];
        }
    },

    buildPropertyInputs: function (node) {
        var me = this,
            fields = me.flatFields || [];
        return [
            {
                xtype: 'combobox',
                width: 260,
                fieldLabel: 'Path',
                labelWidth: 40,
                emptyText: 'pick a property',
                queryMode: 'local',
                displayField: 'path',
                valueField: 'path',
                value: node.path,
                store: { fields: ['path', 'type'], data: fields.slice() },
                listeners: {
                    change: function (c, v) {
                        if (me.renderingBuilder) return;
                        node.path = v || '';
                        var rec = c.findRecordByValue(v);
                        if (rec && node.valueType !== rec.get('type')) {
                            node.valueType = rec.get('type');
                            me.requestBuilderRender();
                        }
                        me.refreshViewer();
                    }
                }
            },
            { xtype: 'tbtext', itemId: 'typeHint', text: node.valueType ? '(' + node.valueType + ')' : '' },
            me.buildValueInput(node)
        ];
    },

    buildTypeAllInputs: function (node) {
        var me = this,
            types = me.mappingTypes || [];
        return [
            {
                xtype: 'combobox',
                width: 130,
                fieldLabel: 'Type',
                labelWidth: 40,
                editable: false,
                store: types,
                value: node.valueType,
                listeners: {
                    change: function (c, v) {
                        if (me.renderingBuilder || !v) return;
                        node.valueType = v;
                        me.refreshViewer();
                    }
                }
            },
            {
                xtype: 'textfield',
                width: 260,
                emptyText: 'value across all fields of this type',
                value: node.value,
                listeners: {
                    change: function (c, v) {
                        if (me.renderingBuilder) return;
                        node.value = me.normalizeString(v);
                        me.refreshViewer();
                    }
                }
            }
        ];
    },

    buildGlobalInputs: function (node) {
        var me = this;
        return [
            {
                xtype: 'textfield',
                width: 380,
                fieldLabel: 'Global',
                labelWidth: 50,
                emptyText: 'search in all properties',
                value: node.value,
                listeners: {
                    change: function (c, v) {
                        if (me.renderingBuilder) return;
                        node.value = me.normalizeString(v);
                        me.refreshViewer();
                    }
                }
            }
        ];
    },

    buildFreeTextInputs: function (node) {
        var me = this;
        return [
            {
                xtype: 'textfield',
                width: 500,
                fieldLabel: 'DSL',
                labelWidth: 40,
                emptyText: 'elastic query_string syntax, e.g. title:foo AND tags:bar',
                value: node.value,
                listeners: {
                    change: function (c, v) {
                        if (me.renderingBuilder) return;
                        node.value = me.normalizeString(v);
                        me.refreshViewer();
                    }
                }
            }
        ];
    },

    buildValueInput: function (node) {
        var me = this,
            type = (node.valueType || 'text').toLowerCase(),
            common = {
                width: 200,
                emptyText: 'value',
                listeners: {
                    change: function (c, v) {
                        if (me.renderingBuilder) return;
                        node.value = me.normalizeString(v);
                        me.refreshViewer();
                    }
                }
            };
        if (type === 'boolean') {
            return Ext.apply({
                xtype: 'combobox',
                editable: false,
                store: ['true', 'false'],
                value: node.value
            }, common);
        }
        if (['long', 'integer', 'short', 'byte', 'double', 'float', 'half_float', 'scaled_float'].indexOf(type) >= 0) {
            return Ext.apply({
                xtype: 'numberfield',
                value: (node.value === '' || node.value == null) ? null : Number(node.value)
            }, common);
        }
        return Ext.apply({ xtype: 'textfield', value: node.value }, common);
    },

    swapKind: function (slot, newKind) {
        var replacement;
        switch (newKind) {
            case 'property': replacement = this.createPropertyNode(); break;
            case 'typeAll': replacement = this.createTypeAllNode(); break;
            case 'global': replacement = this.createGlobalNode(); break;
            case 'freeText': replacement = this.createFreeTextNode(); break;
            default: return;
        }
        if (!slot) return;
        slot.replace(replacement);
        this.requestBuilderRender();
        this.refreshViewer();
    },

    serializeNode: function (node) {
        if (!node) return null;
        switch (node.kind) {
            case 'group':
                return {
                    type: 'group',
                    operator: node.operator,
                    children: (node.children || []).map(this.serializeNode, this).filter(Boolean)
                };
            case 'not':
                return {
                    type: 'not',
                    child: this.serializeNode(node.child)
                };
            case 'property':
                return { type: 'property', path: node.path, valueType: node.valueType, value: node.value };
            case 'typeAll':
                return { type: 'typeAll', valueType: node.valueType, value: node.value };
            case 'global':
                return { type: 'global', value: node.value };
            case 'freeText':
                return { type: 'freeText', value: node.value };
            default:
                return null;
        }
    },

    buildRequestPayload: function () {
        return {
            indexName: this.indexName,
            root: this.serializeNode(this.ensureRootNode())
        };
    },

    refreshViewer: function () {
        var viewer = this.down('#viewer');
        if (!viewer) return;
        var payload = this.buildRequestPayload(),
            pretty = Ext.util.Format.htmlEncode(JSON.stringify(payload, null, 2));
        viewer.update('<pre style="margin:0;font-family:monospace;font-size:12px">' + pretty + '</pre>');
    },

    runSearch: function () {
        this.executeSearch(1);
    },

    executeSearch: function (page) {
        var me = this,
            grid = me.down('#resultsList'),
            pageSize = me.searchPageSize,
            payload = Ext.apply(me.buildRequestPayload(), { from: (page - 1) * pageSize, size: pageSize });

        me.searchCurrentPage = page;
        me.showList();

        Ext.Ajax.request({
            url: '/api/search',
            method: 'POST',
            jsonData: payload,
            success: function (response) {
                var data = Ext.decode(response.responseText),
                    hits = (data && data.hits) || [],
                    total = (data && data.total) || 0,
                    totalPages = Math.max(1, Math.ceil(total / pageSize));
                grid.getStore().loadData(hits);
                me.updateElasticViewer(data && data.esQuery);
                me.updateSearchPagination(page, total, totalPages);
            },
            failure: function (response) {
                Ext.Msg.alert('Search failed', response.responseText || 'Unknown error');
            }
        });
    },

    updateSearchPagination: function (page, total, totalPages) {
        this.down('#searchTotal').setText('Total: ' + total);
        this.down('#searchPageInfo').setText(page + ' / ' + totalPages);
        this.down('#searchPrevPage').setDisabled(page <= 1);
        this.down('#searchNextPage').setDisabled(page >= totalPages);
    },

    updateElasticViewer: function (esQuery) {
        var viewer = this.down('#elasticViewer');
        if (!viewer) return;
        var pretty;
        if (esQuery) {
            try {
                pretty = JSON.stringify(JSON.parse(esQuery), null, 2);
            } catch (e) {
                pretty = esQuery;
            }
        } else {
            pretty = '(no query was sent)';
        }
        viewer.update('<pre style="margin:0;font-family:monospace;font-size:12px">' +
            Ext.util.Format.htmlEncode(pretty) + '</pre>');
    },

    showDetail: function (hit) {
        var area = this.down('#resultsArea'),
            body = this.down('#detailBody'),
            title = this.down('#detailTitle'),
            pretty = Ext.util.Format.htmlEncode(JSON.stringify(hit.source || {}, null, 2));
        title.setText('ID: ' + (hit.id || ''));
        body.update('<pre style="margin:0;font-family:monospace;font-size:12px">' + pretty + '</pre>');
        area.getLayout().setActiveItem(1);
    },

    showList: function () {
        var area = this.down('#resultsArea');
        area.getLayout().setActiveItem(0);
    }
});

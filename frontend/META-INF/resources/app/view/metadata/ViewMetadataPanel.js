Ext.define('ElasticLab.view.metadata.ViewMetadataPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'elasticlab-viewmetadata',
    layout: 'border',

    serverMetadata: null,
    indexMetadata: null,

    items: [
        {
            region: 'north',
            xtype: 'toolbar',
            items: [
                {
                    xtype: 'combobox',
                    itemId: 'indexCombo',
                    fieldLabel: 'Index',
                    labelWidth: 50,
                    queryMode: 'local',
                    displayField: 'name',
                    valueField: 'name',
                    emptyText: 'Select an index...',
                    flex: 1,
                    store: { fields: ['name'], data: [] },
                    listeners: {
                        select: function (combo, record) {
                            combo.up('elasticlab-viewmetadata').loadIndexMetadata(record.get('name'));
                        }
                    }
                },
                {
                    text: 'Refresh',
                    handler: function (btn) {
                        btn.up('elasticlab-viewmetadata').refresh();
                    }
                }
            ]
        },
        {
            region: 'center',
            xtype: 'panel',
            itemId: 'content',
            bodyPadding: 12,
            scrollable: true,
            html: ''
        }
    ],

    listeners: {
        afterrender: function (panel) {
            panel.refresh();
        }
    },

    refresh: function () {
        var panel = this;
        panel.indexMetadata = null;
        Ext.Ajax.request({
            url: '/api/metadata/server',
            method: 'GET',
            success: function (response) {
                panel.serverMetadata = Ext.decode(response.responseText);
                panel.refreshIndices();
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    refreshIndices: function () {
        var panel = this,
            combo = panel.down('#indexCombo');
        Ext.Ajax.request({
            url: '/api/metadata/indices',
            method: 'GET',
            success: function (response) {
                var names = Ext.decode(response.responseText) || [];
                combo.getStore().loadData(names.map(function (n) { return { name: n }; }));
                if (names.length === 1) {
                    combo.setValue(names[0]);
                    panel.loadIndexMetadata(names[0]);
                } else {
                    combo.setValue(null);
                    panel.rerender();
                }
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    loadIndexMetadata: function (indexName) {
        var panel = this;
        Ext.Ajax.request({
            url: '/api/metadata/indices/' + encodeURIComponent(indexName),
            method: 'GET',
            success: function (response) {
                panel.indexMetadata = Ext.decode(response.responseText);
                panel.rerender();
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    rerender: function () {
        var content = this.down('#content');
        content.update(this.renderServer() + this.renderIndex());
    },

    renderServer: function () {
        var sm = this.serverMetadata || {};
        return [
            '<h3>Elastic server metadata</h3>',
            '<table style="border-collapse:collapse">',
            '<tr><td style="padding:4px 16px 4px 0"><b>Server version</b></td><td>', Ext.util.Format.htmlEncode(sm.version || ''), '</td></tr>',
            '<tr><td style="padding:4px 16px 4px 0"><b>Cluster name</b></td><td>', Ext.util.Format.htmlEncode(sm.clusterName || ''), '</td></tr>',
            '</table>'
        ].join('');
    },

    renderIndex: function () {
        if (!this.indexMetadata) {
            return '<h3 style="margin-top:20px">Elastic index metadata</h3>' +
                   '<p style="color:#666">Select an index above.</p>';
        }
        var data = this.indexMetadata,
            name = Ext.util.Format.htmlEncode(data.name || ''),
            count = data.documentCount,
            version = Ext.util.Format.htmlEncode(data.indexVersion || '—'),
            mappingsJson = Ext.util.Format.htmlEncode(JSON.stringify(data.mappings || {}, null, 2));
        return [
            '<h3 style="margin-top:20px">Elastic index metadata</h3>',
            '<table style="border-collapse:collapse">',
            '<tr><td style="padding:4px 16px 4px 0"><b>Index name</b></td><td>', name, '</td></tr>',
            '<tr><td style="padding:4px 16px 4px 0"><b>Number of documents</b></td><td>', count, '</td></tr>',
            '<tr><td style="padding:4px 16px 4px 0"><b>Index version</b></td><td>', version, '</td></tr>',
            '</table>',
            '<h4 style="margin-top:16px">Current mappings</h4>',
            '<pre style="background:#f3f3f3;padding:8px;border:1px solid #ddd;overflow:auto">', mappingsJson, '</pre>'
        ].join('');
    }
});

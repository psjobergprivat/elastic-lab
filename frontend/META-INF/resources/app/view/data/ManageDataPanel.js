Ext.define('ElasticLab.view.data.ManageDataPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'elasticlab-managedata',
    layout: 'border',

    items: [
        {
            region: 'west',
            title: 'Add document',
            width: 420,
            split: true,
            bodyPadding: 10,
            layout: 'vbox',
            defaults: { width: '100%' },
            items: [
                {
                    xtype: 'textarea',
                    itemId: 'documentJson',
                    fieldLabel: 'Document JSON',
                    labelAlign: 'top',
                    height: 260,
                    value: '{\n  "title": "Example",\n  "tags": ["lab", "demo"]\n}'
                },
                {
                    xtype: 'button',
                    text: 'Add Document',
                    handler: function (btn) {
                        btn.up('elasticlab-managedata').addDocument();
                    }
                }
            ]
        },
        {
            region: 'center',
            xtype: 'grid',
            itemId: 'documentsGrid',
            emptyText: 'No documents loaded.',
            tbar: [
                {
                    text: 'Refresh',
                    handler: function (btn) {
                        btn.up('elasticlab-managedata').refreshDocuments();
                    }
                },
                {
                    text: 'Delete selected',
                    handler: function (btn) {
                        btn.up('elasticlab-managedata').deleteSelectedDocument();
                    }
                }
            ],
            store: {
                fields: ['id', 'source']
            },
            columns: [
                { text: 'ID', dataIndex: 'id', width: 260 },
                {
                    text: 'Source',
                    dataIndex: 'source',
                    flex: 1,
                    renderer: function (value) {
                        return Ext.util.Format.htmlEncode(Ext.JSON.encode(value));
                    }
                }
            ]
        }
    ],

    listeners: {
        afterrender: function (panel) {
            panel.refreshDocuments();
        }
    },

    refreshDocuments: function () {
        var grid = this.down('#documentsGrid');
        Ext.Ajax.request({
            url: '/api/data',
            method: 'GET',
            success: function (response) {
                var payload = Ext.decode(response.responseText),
                    hits = (payload && payload.hits) || [];
                grid.getStore().loadData(hits);
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    addDocument: function () {
        var view = this,
            raw = view.down('#documentJson').getValue(),
            parsed;
        try {
            parsed = Ext.decode(raw);
        } catch (e) {
            Ext.Msg.alert('Invalid JSON', e.message);
            return;
        }
        Ext.Ajax.request({
            url: '/api/data',
            method: 'POST',
            jsonData: parsed,
            success: function () {
                view.refreshDocuments();
            },
            failure: function (response) {
                Ext.Msg.alert('Add failed', response.responseText || 'Unknown error');
            }
        });
    },

    deleteSelectedDocument: function () {
        var view = this,
            grid = view.down('#documentsGrid'),
            selection = grid.getSelection()[0];
        if (!selection) {
            Ext.Msg.alert('No selection', 'Select a document in the grid first.');
            return;
        }
        Ext.Ajax.request({
            url: '/api/data/' + encodeURIComponent(selection.get('id')),
            method: 'DELETE',
            success: function () {
                view.refreshDocuments();
            },
            failure: function (response) {
                Ext.Msg.alert('Delete failed', response.responseText || 'Unknown error');
            }
        });
    }
});

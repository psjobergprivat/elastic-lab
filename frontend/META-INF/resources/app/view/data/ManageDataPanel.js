Ext.define('ElasticLab.view.data.ManageDataPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'elasticlab-managedata',
    layout: 'border',

    statusPollMs: 750,
    statusPollTask: null,
    docCurrentPage: 1,
    docPageSize: 50,

    items: [
        {
            region: 'west',
            title: 'Generate test data',
            width: 460,
            split: true,
            scrollable: true,
            bodyPadding: 10,
            layout: 'vbox',
            defaults: { width: '100%', margin: '0 0 8 0' },
            items: [
                {
                    xtype: 'numberfield',
                    itemId: 'numDocuments',
                    fieldLabel: 'Number of documents',
                    labelWidth: 170,
                    minValue: 1,
                    maxValue: 10000000,
                    step: 100,
                    value: 100
                },
                {
                    xtype: 'fieldcontainer',
                    fieldLabel: 'Fields per document',
                    labelWidth: 170,
                    layout: 'hbox',
                    defaults: { margin: '0 6 0 0' },
                    items: [
                        { xtype: 'numberfield', itemId: 'minFields', emptyText: 'min', minValue: 1, maxValue: 50, value: 5, width: 80 },
                        { xtype: 'tbtext', text: 'to' },
                        { xtype: 'numberfield', itemId: 'maxFields', emptyText: 'max', minValue: 1, maxValue: 50, value: 12, width: 80 }
                    ]
                },
                {
                    xtype: 'fieldcontainer',
                    fieldLabel: 'Depth (levels)',
                    labelWidth: 170,
                    layout: 'hbox',
                    defaults: { margin: '0 6 0 0' },
                    items: [
                        { xtype: 'numberfield', itemId: 'minDepth', emptyText: 'min', minValue: 1, maxValue: 5, value: 1, width: 80 },
                        { xtype: 'tbtext', text: 'to' },
                        { xtype: 'numberfield', itemId: 'maxDepth', emptyText: 'max', minValue: 1, maxValue: 5, value: 2, width: 80 }
                    ]
                },
                {
                    xtype: 'fieldcontainer',
                    layout: 'hbox',
                    defaults: { margin: '0 6 0 0' },
                    items: [
                        {
                            xtype: 'button',
                            text: 'Preview example',
                            handler: function (btn) { btn.up('elasticlab-managedata').previewExample(); }
                        },
                        {
                            xtype: 'button',
                            itemId: 'generateBtn',
                            text: 'Generate & insert',
                            handler: function (btn) { btn.up('elasticlab-managedata').startGeneration(); }
                        }
                    ]
                },
                {
                    xtype: 'progressbar',
                    itemId: 'generateProgress',
                    height: 18,
                    hidden: true
                },
                {
                    xtype: 'tbtext',
                    itemId: 'statusText',
                    text: ''
                },
                {
                    xtype: 'panel',
                    title: 'Example document',
                    flex: 1,
                    minHeight: 180,
                    bodyPadding: 6,
                    scrollable: true,
                    items: [
                        {
                            xtype: 'component',
                            itemId: 'previewBody',
                            html: '<pre style="margin:0;font-family:monospace;font-size:12px">No preview yet.</pre>'
                        }
                    ]
                },
                {
                    xtype: 'button',
                    text: 'Delete all documents',
                    cls: 'elasticlab-danger-btn',
                    style: 'background-color:#c0392b;border-color:#922b21;color:#fff;font-weight:bold',
                    handler: function (btn) { btn.up('elasticlab-managedata').deleteAllDocuments(); }
                }
            ]
        },
        {
            region: 'center',
            xtype: 'panel',
            layout: 'card',
            itemId: 'docArea',
            items: [
                {
                    xtype: 'grid',
                    itemId: 'documentsGrid',
                    emptyText: 'No documents loaded.',
                    tbar: [
                        {
                            text: 'Refresh',
                            handler: function (btn) { btn.up('elasticlab-managedata').refreshDocuments(); }
                        },
                        {
                            text: 'Delete selected',
                            handler: function (btn) { btn.up('elasticlab-managedata').deleteSelectedDocument(); }
                        },
                        '->',
                        { xtype: 'tbtext', itemId: 'gridTotal', text: '' },
                        '-',
                        {
                            xtype: 'button',
                            itemId: 'prevPage',
                            text: '◀',
                            disabled: true,
                            handler: function (btn) {
                                var view = btn.up('elasticlab-managedata');
                                view.refreshDocuments(view.docCurrentPage - 1);
                            }
                        },
                        { xtype: 'tbtext', itemId: 'pageInfo', text: '' },
                        {
                            xtype: 'button',
                            itemId: 'nextPage',
                            text: '▶',
                            disabled: true,
                            handler: function (btn) {
                                var view = btn.up('elasticlab-managedata');
                                view.refreshDocuments(view.docCurrentPage + 1);
                            }
                        }
                    ],
                    store: { fields: ['id', 'source'] },
                    columns: [
                        { text: 'ID', dataIndex: 'id', width: 280 },
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
                            grid.up('elasticlab-managedata').showDocumentDetail(record.data);
                        }
                    }
                },
                {
                    xtype: 'panel',
                    layout: 'border',
                    items: [
                        {
                            region: 'north',
                            xtype: 'toolbar',
                            items: [
                                {
                                    text: 'Back to list',
                                    handler: function (btn) { btn.up('elasticlab-managedata').showDocumentList(); }
                                },
                                '-',
                                { xtype: 'tbtext', itemId: 'docDetailTitle', text: '' }
                            ]
                        },
                        {
                            region: 'center',
                            xtype: 'panel',
                            itemId: 'docDetailBody',
                            scrollable: true,
                            bodyPadding: 8,
                            html: ''
                        }
                    ]
                }
            ]
        }
    ],

    listeners: {
        afterrender: function (panel) {
            panel.refreshDocuments();
            panel.refreshStatus();
        },
        beforedestroy: function (panel) {
            panel.stopPollingStatus();
        }
    },

    collectParameters: function () {
        return {
            numDocuments: Number(this.down('#numDocuments').getValue()) || 0,
            minFields: Number(this.down('#minFields').getValue()) || 0,
            maxFields: Number(this.down('#maxFields').getValue()) || 0,
            minDepth: Number(this.down('#minDepth').getValue()) || 0,
            maxDepth: Number(this.down('#maxDepth').getValue()) || 0
        };
    },

    validateRanges: function (params) {
        if (params.minFields > params.maxFields) {
            Ext.Msg.alert('Invalid input', 'Fields min cannot be greater than max.');
            return false;
        }
        if (params.minDepth > params.maxDepth) {
            Ext.Msg.alert('Invalid input', 'Depth min cannot be greater than max.');
            return false;
        }
        return true;
    },

    previewExample: function () {
        var view = this,
            params = view.collectParameters();
        if (!view.validateRanges(params)) return;
        Ext.Ajax.request({
            url: '/api/data/preview',
            method: 'POST',
            jsonData: params,
            success: function (response) {
                var doc = Ext.decode(response.responseText),
                    pretty = Ext.util.Format.htmlEncode(JSON.stringify(doc, null, 2));
                view.down('#previewBody').update(
                    '<pre style="margin:0;font-family:monospace;font-size:12px">' + pretty + '</pre>');
            },
            failure: function (response) {
                Ext.Msg.alert('Preview failed', response.responseText || 'Unknown error');
            }
        });
    },

    startGeneration: function () {
        var view = this,
            params = view.collectParameters();
        if (!view.validateRanges(params)) return;
        view.setGenerateBusy(true);
        Ext.Ajax.request({
            url: '/api/data/generate',
            method: 'POST',
            jsonData: params,
            success: function (response) {
                var status = Ext.decode(response.responseText);
                view.applyStatus(status);
                view.startPollingStatus();
            },
            failure: function (response) {
                view.setGenerateBusy(false);
                Ext.Msg.alert('Generate failed', response.responseText || 'Unknown error');
            }
        });
    },

    refreshStatus: function () {
        var view = this;
        Ext.Ajax.request({
            url: '/api/data/generate/status',
            method: 'GET',
            success: function (response) {
                var status = Ext.decode(response.responseText);
                view.applyStatus(status);
                if (status && status.state === 'running') {
                    view.startPollingStatus();
                }
            }
        });
    },

    startPollingStatus: function () {
        var view = this;
        view.stopPollingStatus();
        view.statusPollTask = Ext.TaskManager.start({
            interval: view.statusPollMs,
            run: function () { view.pollStatusOnce(); }
        });
    },

    stopPollingStatus: function () {
        if (this.statusPollTask) {
            Ext.TaskManager.stop(this.statusPollTask);
            this.statusPollTask = null;
        }
    },

    pollStatusOnce: function () {
        var view = this;
        Ext.Ajax.request({
            url: '/api/data/generate/status',
            method: 'GET',
            success: function (response) {
                var status = Ext.decode(response.responseText);
                view.applyStatus(status);
                if (!status || status.state !== 'running') {
                    view.stopPollingStatus();
                    view.setGenerateBusy(false);
                    if (status && status.state === 'completed') {
                        view.refreshDocuments(1);
                    } else if (status && status.state === 'failed') {
                        Ext.Msg.alert('Generation failed', status.error || 'Unknown error');
                    }
                }
            }
        });
    },

    applyStatus: function (status) {
        var bar = this.down('#generateProgress'),
            text = this.down('#statusText');
        if (!status || status.state === 'idle') {
            bar.hide();
            bar.updateProgress(0, '');
            text.setText('');
            return;
        }
        var total = status.totalDocuments || 0,
            done = status.insertedDocuments || 0,
            ratio = total > 0 ? done / total : 0,
            label = done + ' / ' + total;
        bar.show();
        bar.updateProgress(ratio, label);
        if (status.state === 'running') {
            text.setText('Generating...');
        } else if (status.state === 'completed') {
            text.setText('Inserted ' + done + ' documents.');
        } else if (status.state === 'failed') {
            text.setText('Failed: ' + (status.error || 'unknown'));
        }
    },

    setGenerateBusy: function (busy) {
        var btn = this.down('#generateBtn');
        btn.setDisabled(!!busy);
        btn.setText(busy ? 'Generating...' : 'Generate & insert');
    },

    deleteAllDocuments: function () {
        var view = this;
        Ext.Msg.confirm('Delete all documents',
            'This will delete every document in the index. Continue?',
            function (answer) {
                if (answer !== 'yes') return;
                Ext.Ajax.request({
                    url: '/api/data',
                    method: 'DELETE',
                    success: function (response) {
                        var result = Ext.decode(response.responseText);
                        Ext.Msg.alert('Done', 'Deleted ' + (result.deleted || 0) + ' documents.');
                        view.refreshDocuments(1);
                    },
                    failure: function (response) {
                        Ext.Msg.alert('Delete failed', response.responseText || 'Unknown error');
                    }
                });
            });
    },

    refreshDocuments: function (page) {
        var view = this,
            grid = view.down('#documentsGrid'),
            totalText = view.down('#gridTotal'),
            pageInfo = view.down('#pageInfo'),
            prevBtn = view.down('#prevPage'),
            nextBtn = view.down('#nextPage'),
            pageSize = view.docPageSize,
            currentPage = (typeof page === 'number') ? page : (view.docCurrentPage || 1),
            from = (currentPage - 1) * pageSize;

        view.docCurrentPage = currentPage;

        Ext.Ajax.request({
            url: '/api/data',
            method: 'GET',
            params: { from: from, size: pageSize },
            success: function (response) {
                var payload = Ext.decode(response.responseText),
                    hits = (payload && payload.hits) || [],
                    total = (payload && payload.total) || 0,
                    totalPages = Math.max(1, Math.ceil(total / pageSize));
                grid.getStore().loadData(hits);
                totalText.setText('Total: ' + total);
                pageInfo.setText(currentPage + ' / ' + totalPages);
                prevBtn.setDisabled(currentPage <= 1);
                nextBtn.setDisabled(currentPage >= totalPages);
            },
            failure: function (response) {
                Ext.Msg.alert('Load failed', response.responseText || 'Unknown error');
            }
        });
    },

    showDocumentDetail: function (hit) {
        var area = this.down('#docArea'),
            body = this.down('#docDetailBody'),
            title = this.down('#docDetailTitle'),
            pretty = Ext.util.Format.htmlEncode(JSON.stringify(hit.source || {}, null, 2));
        title.setText('ID: ' + (hit.id || ''));
        body.update('<pre style="margin:0;font-family:monospace;font-size:12px">' + pretty + '</pre>');
        area.getLayout().setActiveItem(1);
    },

    showDocumentList: function () {
        this.down('#docArea').getLayout().setActiveItem(0);
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
            success: function () { view.refreshDocuments(); },
            failure: function (response) {
                Ext.Msg.alert('Delete failed', response.responseText || 'Unknown error');
            }
        });
    }
});

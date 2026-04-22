Ext.define('ElasticLab.view.search.SearchPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'elasticlab-search',
    layout: 'border',

    items: [
        {
            region: 'north',
            xtype: 'toolbar',
            items: [
                {
                    xtype: 'textfield',
                    itemId: 'queryField',
                    emptyText: 'Search text (leave empty for match_all)',
                    flex: 1,
                    listeners: {
                        specialkey: function (field, event) {
                            if (event.getKey() === event.ENTER) {
                                field.up('elasticlab-search').runSearch();
                            }
                        }
                    }
                },
                '->',
                {
                    xtype: 'button',
                    text: 'Search',
                    handler: function (btn) {
                        btn.up('elasticlab-search').runSearch();
                    }
                }
            ]
        },
        {
            region: 'center',
            xtype: 'grid',
            itemId: 'resultsGrid',
            emptyText: 'No results yet.',
            store: {
                fields: ['id', 'score', 'source']
            },
            columns: [
                { text: 'ID', dataIndex: 'id', width: 260 },
                { text: 'Score', dataIndex: 'score', width: 90 },
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

    runSearch: function () {
        var view = this,
            text = view.down('#queryField').getValue(),
            grid = view.down('#resultsGrid'),
            body = text
                ? { query: { query_string: { query: text } } }
                : { query: { match_all: {} } };

        Ext.Ajax.request({
            url: '/api/search',
            method: 'POST',
            jsonData: body,
            success: function (response) {
                var payload = Ext.decode(response.responseText),
                    hits = (payload && payload.hits && payload.hits.hits) || [];
                grid.getStore().loadData(hits.map(function (hit) {
                    return { id: hit._id, score: hit._score, source: hit._source };
                }));
            },
            failure: function (response) {
                Ext.Msg.alert('Search failed', response.responseText || 'Unknown error');
            }
        });
    }
});

Ext.define('ElasticLab.view.Main', {
    extend: 'Ext.container.Viewport',
    layout: 'fit',

    items: [{
        xtype: 'tabpanel',
        items: [
            { title: 'Search', xtype: 'elasticlab-search' },
            { title: 'Manage Data', xtype: 'elasticlab-managedata' }
        ]
    }]
});

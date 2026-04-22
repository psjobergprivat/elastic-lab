Ext.define('ElasticLab.view.Main', {
    extend: 'Ext.container.Viewport',
    layout: 'fit',

    items: [{
        xtype: 'tabpanel',
        itemId: 'mainTabs',
        items: [
            { title: 'Search',        xtype: 'elasticlab-search',       itemId: 'search' },
            { title: 'Manage Data',   xtype: 'elasticlab-managedata',   itemId: 'manage-data' },
            { title: 'View Metadata', xtype: 'elasticlab-viewmetadata', itemId: 'view-metadata' }
        ],
        listeners: {
            afterrender: function (tabs) {
                var requested = new URLSearchParams(window.location.search).get('tab'),
                    target = requested && tabs.down('#' + requested);
                if (target) {
                    tabs.setActiveTab(target);
                }
            },
            tabchange: function (tabs, newTab) {
                var url = new URL(window.location.href);
                url.searchParams.set('tab', newTab.getItemId());
                window.history.replaceState({}, '', url);
            }
        }
    }]
});

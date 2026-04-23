Ext.define('ElasticLab.view.Main', {
    extend: 'Ext.container.Viewport',
    layout: 'border',

    items: [
        {
            region: 'north',
            xtype: 'container',
            height: 56,
            cls: 'elasticlab-header',
            style: 'background:#1f3a5f;color:#fff;display:flex;align-items:center;padding:0 16px',
            html: '<img src="favicon.svg" alt="Elastic Lab" style="width:32px;height:32px;margin-right:12px;position:relative;top:5px">' +
                  '<span style="font-size:24px;font-weight:bold;letter-spacing:0.5px">Elastic Lab</span>'
        },
        {
            region: 'center',
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
        }
    ]
});

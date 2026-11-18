export let notificationTypeList = [
    {
        name: 'TELEGRAM',
        label: 'TG 通知'
    },
    {
        name: 'CUSTOM_REQUEST',
        label: '自定义请求'
    },
    {
        name: 'EMBY_REFRESH',
        label: '媒体库刷新'
    }
]

export let getLabel = (name) => {
    return notificationTypeList.find(item => item.name === name)?.label ?? '已移除的通知';
}

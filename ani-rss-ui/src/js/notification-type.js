import {t} from "@/i18n/index.js"
export let notificationTypeList = [
    {
        name: 'TELEGRAM',
        label: 'telegram'
    },
    {
        name: 'CUSTOM_REQUEST',
        label: 'customrequest'
    },
    {
        name: 'EMBY_REFRESH',
        label: 'libraryrefresh'
    },
    {
        name: 'PLUGIN',
        label: 'pluginchannel'
    }
]

export let getLabel = (name) => {
    return t(notificationTypeList.find(item => item.name === name)?.label ?? 'removednotification');
}

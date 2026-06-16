package com.local.dfcraftmonitor.data.backend

import com.local.dfcraftmonitor.data.remote.AmsConstants

enum class AmsEndpoint(
    val endpointId: String,
    val url: String,
) {
    AMS_IDE("amsIde", AmsConstants.CRAFTING_BASE_URL),
    AMS_IDE_PAGE("amsIdePage", "https://comm.ams.game.qq.com/ide/page/"),
    MINI_WX_API("miniWxApi", "https://mini.game.qq.com/ams/ame/wxApi"),
    AMS_USER_LOGIN("amsUserLogin", "https://ams.game.qq.com/ams/userLoginSvr"),
    OBJECT_IMAGES("playerhubObjectImages", "https://playerhub.df.qq.com/playerhub/60004/object/"),
    STATIC_ASSETS("dfmStaticAssets", "https://game.gtimg.cn/images/dfm/cp/a20240807community/"),
    ;

    companion object {
        val CRAFTING_STATUS: AmsEndpoint = AMS_IDE
    }
}

# 官方授权链路备忘

## QQ 互联 Android SDK

官方 QQ 互联 Android SDK 文档说明：移动应用需要在 QQ 互联开放平台注册应用并获得 appid/appkey；SDK 登录成功后回调返回 openid、access_token、expires_in。access_token 用于发起授权范围内的 OpenAPI 请求，过期或失效时需要重新登录。相关官方文档：

- https://wiki.connect.qq.com/android_sdk%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E
- https://wiki.connect.qq.com/sdk%E6%8E%A5%E5%85%A5%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E%E6%96%87%E6%A1%A3

## 微信开放平台移动应用登录

微信移动应用登录一般是 OAuth2 授权码模式：APP 通过微信 SDK 发起 SendAuth 请求，用户同意后回调 code；开发者再用 code 换取 access_token/openid。实际接入需要微信开放平台应用、AppID、AppSecret、回调 Activity 和授权 scope。官方文档页面当前无法由本环境直接打开，但路径为：

- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html
- https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html

## 对本项目的含义

- QQ/微信开放平台登录只能证明用户身份，不能自动等价于三角洲小程序内部的游戏账号会话。
- M0 需要找出小程序制造接口使用的会话类型：开放平台 access_token、微信小程序 session、游戏侧 token、Cookie，或其他只读会话。
- 任何会话材料都必须本地保存，不能上传服务器。

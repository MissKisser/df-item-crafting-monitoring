package com.local.dfcraftmonitor.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmsDashboardParserTest {

    @Test
    fun parsesDaySecretList() {
        val result = AmsDashboardParser.parseDaySecrets(
            """
            {
              "ret":0,
              "iRet":0,
              "jData":{"data":{"code":0,"data":{"list":[
                {"mapName":"零号大坝","secret":"7391"},
                {"mapName":"航天基地","secret":"1850"}
              ]}}}
            }
            """.trimIndent(),
        )

        assertEquals(listOf(DaySecret("零号大坝", "7391"), DaySecret("航天基地", "1850")), result)
    }

    @Test
    fun parsesToolObjectListWithDisplayCategoryAndCompactPrice() {
        val result = AmsDashboardParser.parseToolObjects(
            """
            {
              "ret":0,
              "iRet":0,
              "jData":{"data":{"code":0,"data":{"list":[
                {
                  "objectID":13030000115,
                  "objectName":"禁区一体枪托",
                  "primaryClass":"acc",
                  "secondClass":"accStock",
                  "secondClassCN":"枪托",
                  "avgPrice":31472,
                  "pic":"https://playerhub.df.qq.com/playerhub/60004/object/13030000115.png"
                }
              ]}}}
            }
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("13030000115", result[0].id)
        assertEquals("禁区一体枪托", result[0].name)
        assertEquals("配件 / 枪托", result[0].category)
        assertEquals("3.1万", result[0].price)
        assertEquals("", result[0].trend)
        assertTrue(result[0].pricePoints.isEmpty())
        assertTrue(result[0].imageUrl.endsWith("13030000115.png"))
    }

    @Test
    fun parsesHourlyPricePointsWithLabelsAndCompactPrices() {
        val result = AmsDashboardParser.parsePricePoints(
            """
            {
              "ret":0,
              "iRet":0,
              "jData":{"data":{"code":0,"data":{"list":[
                {"hour":"09:00","avgPrice":30000},
                {"hour":"10:00","avgPrice":33000}
              ]}}}
            }
            """.trimIndent(),
        )

        assertEquals(listOf(PricePoint("09:00", "3万", 30000), PricePoint("10:00", "3.3万", 33000)), result)
    }

    @Test
    fun parsesLivePricePointShapes() {
        val hourly = AmsDashboardParser.parsePricePoints(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"objectPriceHour":{"objectID":13160000018,"list":[
              {"time":"10:00","avgPrice":87115}
            ]}}}}}
            """.trimIndent(),
        )
        val recent = AmsDashboardParser.parsePricePoints(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"objectPriceRecent":{"objectID":13160000018,"list":[
              {"dtstatdate":"20260617","avgPrice":88000}
            ]}}}}}
            """.trimIndent(),
        )
        val latest = AmsDashboardParser.parsePricePoints(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"dataMap":{"13160000018":{"avgPrice":87115}}}}}}
            """.trimIndent(),
        )

        assertEquals(listOf(PricePoint("10:00", "8.7万", 87115)), hourly)
        assertEquals(listOf(PricePoint("20260617", "8.8万", 88000)), recent)
        assertEquals(listOf(PricePoint("13160000018", "8.7万", 87115)), latest)
    }

    @Test
    fun parsesToolConfigItems() {
        val result = AmsDashboardParser.parseToolConfigs(
            """
            {
              "ret":0,
              "iRet":0,
              "jData":{"data":{"code":0,"data":{"list":[
                {"id":"gun","name":"枪械","type":"category"}
              ]}}}
            }
            """.trimIndent(),
        )

        assertEquals(listOf(ToolConfigItem("gun", "枪械", "category")), result)
    }

    @Test
    fun parserSkipsFailedBusinessResponses() {
        val result = AmsDashboardParser.parseToolObjects(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":101,"msg":"请先登录","data":{}}}}
            """.trimIndent(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun parsesManufacturingPlaceLabelsWhenPresent() {
        val result = AmsDashboardParser.parseManufacturingPlaces(
            """
            {
              "ret":0,
              "iRet":0,
              "jData":{"data":{"code":0,"data":{"list":[
                {"placeName":"技术中心","placeType":"tech"},
                {"placeName":"工作台","placeType":"workbench"}
              ]}}}
            }
            """.trimIndent(),
        )

        assertEquals(listOf(MapSummary("技术中心", "tech"), MapSummary("工作台", "workbench")), result)
    }

    @Test
    fun parsesManufacturingRecommendationsFromLivePlaceUnlocks() {
        val result = AmsDashboardParser.parseManufacturingRecommendations(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[
              {"place":"tech","placeDetail":{"unlock":{"props":[
                {"salePrice":87115,"costPrice":57804,"fee":8885,"bail":0,"objectID":13160000018,"perCount":1,"period":"4.5"},
                {"salePrice":125332,"costPrice":188007,"fee":14149,"bail":0,"objectID":13160000016,"perCount":4,"period":"4.5"}
              ]}}},
              {"place":"workbench","placeDetail":{"unlock":{"gun":[
                {"salePrice":183809,"costPrice":91374,"fee":21450,"bail":0,"objectID":11010005010,"perCount":1,"period":"14"}
              ]}}}
            ]}}}}
            """.trimIndent(),
        )

        // 与小程序一致：按每小时利润降序。workbench profit=70985, /14h≈5070/h；tech profit=20426, /4.5h≈4539/h。
        assertEquals(2, result.size)
        assertEquals("11010005010", result[0].id)
        assertEquals("工作台", result[0].placeName)
        assertEquals("7.1万", result[0].profit)
        assertEquals(70_985L, result[0].profitValue)
        assertEquals("18.4万", result[0].salePrice)
        assertEquals("9.1万", result[0].costPrice)
        assertEquals("2.1万", result[0].fee)
        assertEquals("14", result[0].period)
        assertEquals("1", result[0].perCount)
        assertTrue(result[0].profitPerHourValue > result[1].profitPerHourValue)
        assertEquals("13160000018", result[1].id)
        assertEquals("技术中心", result[1].placeName)
    }

    @Test
    fun manufacturingProfitSubtractsBailAndPicksTopPerHourPerPlace() {
        // 同一台位下多条配方，应按每小时利润取最高那条；且利润要减去 bail（保证金）。
        val result = AmsDashboardParser.parseManufacturingRecommendations(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[
              {"place":"tech","placeDetail":{"unlock":{"props":[
                {"salePrice":100000,"costPrice":30000,"fee":5000,"bail":15000,"objectID":1,"perCount":1,"period":"2"},
                {"salePrice":100000,"costPrice":30000,"fee":5000,"bail":0,"objectID":2,"perCount":1,"period":"2"}
              ]}}}
            ]}}}}
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        // 配方2 未扣保证金，利润更高（100000-30000-5000-0=65000），每小时=32500，应被选中。
        assertEquals("2", result[0].id)
        assertEquals(65_000L, result[0].profitValue)
        assertEquals(32_500L, result[0].profitPerHourValue)
        assertEquals("0", result[0].bail)
    }

    @Test
    fun manufacturingProfitSubtractsBailWhenBailPresent() {
        // 单条带保证金的配方，验证 bail 被计入成本。
        val result = AmsDashboardParser.parseManufacturingRecommendations(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"list":[
              {"place":"tech","placeDetail":{"unlock":{"props":[
                {"salePrice":100000,"costPrice":30000,"fee":5000,"bail":15000,"objectID":1,"perCount":1,"period":"2"}
              ]}}}
            ]}}}}
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        // 100000-30000-5000-15000 = 50000；每小时 = 50000/2 = 25000
        assertEquals(50_000L, result[0].profitValue)
        assertEquals(25_000L, result[0].profitPerHourValue)
        assertEquals("1.5万", result[0].bail)
    }

    @Test
    fun parsesPlayerProfileAndMineStats() {
        val result = AmsDashboardParser.parsePlayerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "nickName":"干员K",
              "areaName":"烽火地带",
              "avatar":"https://example.test/avatar.png",
              "avatarFrame":"https://example.test/frame.png",
              "curRankName":"铂金",
              "curRankPic":"https://example.test/rank.png",
              "maxRankName":"钻石",
              "maxRankPic":"https://example.test/max-rank.png",
              "totalBringOutValue":12345678,
              "evacuationRate":"58.6%",
              "killOperatorNum":321,
              "profitLossRatio":"1.45"
            }}}}
            """.trimIndent(),
        )

        assertEquals("干员K", result.nickname)
        assertEquals("烽火地带", result.areaName)
        assertEquals("铂金", result.currentRankName)
        assertEquals("1234.6万", result.totalBringOutValue)
        assertEquals("58.6%", result.evacuationRate)
    }

    @Test
    fun parsesIncomeCollectionsMatchesAndRedArchive() {
        val body = """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "yesterdayIncome":880000,
              "collectionList":[{"objectName":"非洲之心","pic":"https://example.test/red.png","value":520000,"mapName":"零号大坝"}],
              "recentMatches":[{"battleId":"b1","mapName":"零号大坝","modeName":"机密行动","result":"撤离成功","flowCalGainedPrice":320000,"killOperatorNum":4,"duration":"18:42","battleTime":"今天 12:00"}],
              "redRecords":[{"recordId":"r1","objectName":"曼德尔砖","pic":"https://example.test/m.png","value":780000,"mapName":"长弓溪谷","foundTime":"昨天"}]
            }}}}
        """.trimIndent()

        assertEquals("88万", AmsDashboardParser.parseYesterdayIncome(body).amount)
        assertEquals("非洲之心", AmsDashboardParser.parseCollections(body).single().name)
        assertEquals("b1", AmsDashboardParser.parseRecentMatches(body).single().id)
        assertEquals("曼德尔砖", AmsDashboardParser.parseRedArchive(body).single().name)
    }

    @Test
    fun parsesLiveSolRecentDetailIncomeAndCollectionTop() {
        val body = """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "solDetail":{
                "recentGain":880000,
                "recentGainDate":"2026-06-16",
                "userCollectionTop":{"list":[{"objectID":15080050003,"count":2,"price":520000}]}
              }
            }}}}
        """.trimIndent()

        assertEquals("88万", AmsDashboardParser.parseYesterdayIncome(body).amount)
        assertEquals(
            listOf(CollectionItem("15080050003", "15080050003", "", "52万", "", 2)),
            AmsDashboardParser.parseCollections(body),
        )
    }

    @Test
    fun parsesCollectionCountFromVariousFields() {
        val body = """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"collectionList":[
              {"objectName":"非洲之心","pic":"https://example.test/red.png","value":520000,"mapName":"零号大坝"},
              {"objectName":"曼德尔砖","pic":"https://example.test/m.png","value":780000,"num":3}
            ]}}}}
        """.trimIndent()

        val result = AmsDashboardParser.parseCollections(body)
        assertEquals(2, result.size)
        assertEquals(1, result[0].count)
        assertEquals(3, result[1].count)
    }

    @Test
    fun parsesLiveSolCareerStatsWithRankNamesAndIcons() {
        val base = PlayerProfile.empty().copy(
            nickname = "干员K",
            areaName = "QQ区",
            avatarUrl = "https://example.test/avatar.png",
        )
        val result = AmsDashboardParser.parseSolCareerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
              "levelScore":"6008",
              "majorLevelMax":"7",
              "totalEscape":"432",
              "totalFight":"1384",
              "totalGainedPrice":"794331244",
              "totalKill":"1138",
              "profitLossRatio":"83438155"
            }}}}}
            """.trimIndent(),
            base,
        )

        assertEquals("干员K", result.nickname)
        assertEquals("三角洲巅峰", result.currentRankName)
        assertTrue(result.currentRankIconUrl.endsWith("dw_fh_icon25.png"))
        assertEquals("三角洲巅峰", result.highestRankName)
        assertTrue(result.highestRankIconUrl.endsWith("dw_fh_icon25.png"))
        assertEquals("7.9亿", result.totalBringOutValue)
        assertEquals("31.2%", result.evacuationRate)
        assertEquals("1138", result.operatorKills)
        assertEquals("83.4万", result.profitLossRatio)
    }

    @Test
    fun parsesLivePhq59YSolBattleRecords() {
        val result = AmsDashboardParser.parseRecentMatches(
            """
            {"ret":0,"iRet":0,"jData":{"data":[
              {
                "MapId":"3902",
                "EscapeFailReason":2,
                "FinalPrice":0,
                "dtEventTime":"2026-06-17 08:54:13",
                "ArmedForceId":40005,
                "DurationS":192,
                "KillCount":2,
                "KillPlayerAICount":1,
                "flowCalGainedPrice":"-2351938",
                "RoomId":"648524815026258282",
                "BattleResult":""
              }
            ]}}
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("648524815026258282", result[0].id)
        assertEquals("航天基地-绝密", result[0].mapName)
        assertEquals("撤离失败", result[0].result)
        // 与小程序一致：净收益用千分位原始数值（负数=亏损），不再转"万"
        assertEquals("-2,351,938", result[0].netIncome)
        assertEquals(-2351938L, result[0].netIncomeValue)
        assertEquals("3", result[0].operatorKills)
        assertEquals("3分12秒", result[0].duration)
    }

    @Test
    fun parsesRecentMatchNetIncomeOnlyFromFlowCalGainedPrice() {
        // 验证：不再把 bringOutValue/income 等字段误当成净收益。
        // 本条没有 flowCalGainedPrice，净收益应为空（而不是 fallback 到 FinalPrice 等正值）。
        val result = AmsDashboardParser.parseRecentMatches(
            """
            {"ret":0,"iRet":0,"jData":{"data":[
              {"MapId":"3902","RoomId":"r1","FinalPrice":1431000,"KillCount":0,"DurationS":120,"EscapeFailReason":0}
            ]}}
            """.trimIndent(),
        )
        assertEquals(1, result.size)
        assertEquals("", result[0].netIncome)
        assertEquals(null, result[0].netIncomeValue)
        assertEquals(null, result[0].broughtOutValue)
    }

    @Test
    fun parsesOperatorFromArmedForceId() {
        // 战绩只返回 ArmedForceId，干员名/头像由 assetsId.sqlName/sqlImg 查表得到（与小程序一致）。
        val result = AmsDashboardParser.parseRecentMatches(
            """
            {"ret":0,"iRet":0,"jData":{"data":[
              {"MapId":"2202","RoomId":"r1","ArmedForceId":10007,"flowCalGainedPrice":528751,"KillCount":2,"DurationS":600,"EscapeFailReason":0}
            ]}}
            """.trimIndent(),
        )
        assertEquals(1, result.size)
        // 10007 → 红狼
        assertEquals("红狼", result[0].operatorName)
        assertTrue(result[0].operatorImageUrl.contains("war-role5.png"))
    }

    @Test
    fun parsesOperatorNameAndIconDirectHelpers() {
        // 直接校验 assetsId 映射表：露娜 / 骇爪 / 蜂医 等。
        assertEquals("露娜", AmsDashboardParser.operatorName(40005))
        assertEquals("骇爪", AmsDashboardParser.operatorName(40010))
        assertEquals("蜂医", AmsDashboardParser.operatorName(20003))
        assertEquals("", AmsDashboardParser.operatorName(99999))
        assertEquals("", AmsDashboardParser.operatorName(null))
        assertTrue(AmsDashboardParser.operatorIconUrl(10007).endsWith("war-role5.png"))
        assertEquals("", AmsDashboardParser.operatorIconUrl(null))
    }

    @Test
    fun parsesCdnFormatFromConfigList() {
        // 与小程序一致：dfm/config.list 的 config.cdnFormat.object.prefix/suffix。
        val body = """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"config":{"cdnFormat":{"object":{
              "prefix":"https://game.gtimg.cn/images/dfm/object/",
              "suffix":"png"
            }}}}}}}
        """.trimIndent()
        val cdn = AmsDashboardParser.parseCdnFormat(body)
        assertEquals("https://game.gtimg.cn/images/dfm/object/", cdn.prefix)
        assertEquals("png", cdn.suffix)
        assertTrue(cdn.isAvailable)
        // 拼出的干员头像 URL 应使用真实 prefix
        val url = AmsDashboardParser.operatorIconUrl(10007, cdn.prefix, cdn.suffix)
        assertEquals("https://game.gtimg.cn/images/dfm/object/war-role5.png", url)
        // 空响应兜底
        assertFalse(AmsDashboardParser.parseCdnFormat(null).isAvailable)
    }

   @Test
    fun parsesRedUnlockRecordWithoutLocalFallback() {
        val result = AmsDashboardParser.parseRedArchive(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"total":1,"list":[
              {"time":"2026-06-16 22:00:00","itemId":"15090000001","mapid":"1902","num":2,"des":"出红"}
            ]}}}}
            """.trimIndent(),
        )

        assertEquals(1, result.size)
        assertEquals("15090000001_2026-06-16 22:00:00", result[0].id)
        assertEquals("15090000001", result[0].name)
        assertEquals("长弓溪谷-机密", result[0].mapName)
        assertEquals("2026-06-16 22:00:00", result[0].foundTime)
    }

    // ---- 昵称/头像修复相关测试 ----

    @Test
    fun parsePlayerProfile_fallsBackToUinWhenNicknameEmpty() {
        // 模拟真实 user.info 响应：nickname/avatar 为空字符串，uin 有值
        val result = AmsDashboardParser.parsePlayerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "uin":"3334612216573072958",
              "roleID":"3334612216573072958",
              "nickname":"",
              "avatar":"",
              "gender":0,
              "signature":"",
              "birthday":"",
              "status":1
            }}}}
            """.trimIndent(),
        )

        // nickname 为空时应兜底到 uin
        assertEquals("3334612216573072958", result.nickname)
        // avatar 为空时保持空（由 QIRBwm 流程提供真实头像）
        assertEquals("", result.avatarUrl)
    }

    @Test
    fun parsePlayerProfile_usesPicUrlForAvatar() {
        // 验证 picUrl 字段能被识别为 avatarUrl
        val result = AmsDashboardParser.parsePlayerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "nickName":"TestUser",
              "picUrl":"https://example.test/pic.png",
              "region":"QQ区"
            }}}}
            """.trimIndent(),
        )

        assertEquals("TestUser", result.nickname)
        assertEquals("https://example.test/pic.png", result.avatarUrl)
        assertEquals("QQ区", result.areaName)
    }

    @Test
    fun parsePlayerProfile_usesWechatHeadImageFieldsForAvatar() {
        val result = AmsDashboardParser.parsePlayerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "nickName":"微信干员",
              "headimgurl":"http://thirdwx.qlogo.cn/mmopen/test/132",
              "region":"微信区"
            }}}}
            """.trimIndent(),
        )

        assertEquals("微信干员", result.nickname)
        assertEquals("https://thirdwx.qlogo.cn/mmopen/test/132", result.avatarUrl)
        assertEquals("微信区", result.areaName)
    }

    @Test
    fun parsePlayerProfile_skipsNumericHeadIconForWechatAvatar() {
        val result = AmsDashboardParser.parsePlayerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "nickName":"微信干员",
              "headIcon":"42010040702",
              "headimgurl":"http://thirdwx.qlogo.cn/mmopen/test/132",
              "region":"微信区"
            }}}}
            """.trimIndent(),
        )

        assertEquals("https://thirdwx.qlogo.cn/mmopen/test/132", result.avatarUrl)
    }

    @Test
    fun parseSolCareerProfile_readsNicknameAndAvatarFromSolDetail() {
        // base（来自 user.info）nickname/avatar 均为空
        val base = PlayerProfile.empty().copy(
            nickname = "3334612216573072958",  // uin 兜底
            areaName = "",
            avatarUrl = "",
        )
        val result = AmsDashboardParser.parseSolCareerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
              "nickName":"三角洲战士",
              "picUrl":"https://example.test/game-avatar.png",
              "areaName":"烽火地带",
              "avatarFrame":"https://example.test/frame.png",
              "levelScore":"6008",
              "majorLevelMax":"7",
              "totalEscape":"432",
              "totalFight":"1384",
              "totalGainedPrice":"794331244",
              "totalKill":"1138",
              "profitLossRatio":"83438155"
            }}}}}
            """.trimIndent(),
            base,
        )

        // solDetail 的 nickName/picUrl 应覆盖 base 的空值
        assertEquals("三角洲战士", result.nickname)
        assertEquals("https://example.test/game-avatar.png", result.avatarUrl)
        assertEquals("烽火地带", result.areaName)
        assertEquals("https://example.test/frame.png", result.avatarFrameUrl)
        // 段位/战绩字段仍正常解析
        assertEquals("三角洲巅峰", result.currentRankName)
        assertEquals("7.9亿", result.totalBringOutValue)
    }

    @Test
    fun parseSolCareerProfile_skipsNumericHeadIconForAvatar() {
        val base = PlayerProfile.empty().copy(avatarUrl = "")
        val result = AmsDashboardParser.parseSolCareerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
              "nickName":"三角洲战士",
              "headIcon":"42010040702",
              "picUrl":"http://thirdwx.qlogo.cn/mmopen/sol/132",
              "levelScore":"6008",
              "majorLevelMax":"7"
            }}}}}
            """.trimIndent(),
            base,
        )

        assertEquals("https://thirdwx.qlogo.cn/mmopen/sol/132", result.avatarUrl)
    }

    @Test
    fun parseSolCareerProfile_preservesBaseNicknameWhenSolDetailEmpty() {
        // solDetail 不含昵称/头像字段时，应保留 base 的值
        val base = PlayerProfile.empty().copy(
            nickname = "干员K",
            areaName = "QQ区",
            avatarUrl = "https://example.test/avatar.png",
        )
        val result = AmsDashboardParser.parseSolCareerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
              "levelScore":"3000",
              "majorLevelMax":"4",
              "totalEscape":"100",
              "totalFight":"500",
              "totalGainedPrice":"100000000",
              "totalKill":"200",
              "profitLossRatio":"5000000"
            }}}}}
            """.trimIndent(),
            base,
        )

        // base 的昵称/头像应被保留
        assertEquals("干员K", result.nickname)
        assertEquals("https://example.test/avatar.png", result.avatarUrl)
        assertEquals("QQ区", result.areaName)
    }

    @Test
    fun endToEnd_userInfoEmpty_solCareerEnrichesProfile() {
        // 模拟真实流程：user.info 返回空 nickname/avatar → parsePlayerProfile → uin 兜底
        val base = AmsDashboardParser.parsePlayerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{
              "uin":"3334612216573072958",
              "roleID":"3334612216573072958",
              "nickname":"",
              "avatar":"",
              "gender":0
            }}}}
            """.trimIndent(),
        )
        // 第一步：uin 兜底生效
        assertEquals("3334612216573072958", base.nickname)
        assertEquals("", base.avatarUrl)

        // 第二步：solCareer 接口提供游戏角色数据
        val enriched = AmsDashboardParser.parseSolCareerProfile(
            """
            {"ret":0,"iRet":0,"jData":{"data":{"code":0,"data":{"solDetail":{
              "nickName":"幽灵行动者",
              "picUrl":"https://example.test/sol-avatar.png",
              "areaName":"零号大坝",
              "levelScore":"4500",
              "majorLevelMax":"5",
              "totalEscape":"300",
              "totalFight":"800",
              "totalGainedPrice":"500000000",
              "totalKill":"900",
              "profitLossRatio":"50000000"
            }}}}}
            """.trimIndent(),
            base,
        )

        // 最终 profile 应使用 solDetail 的游戏角色数据
        assertEquals("幽灵行动者", enriched.nickname)
        assertEquals("https://example.test/sol-avatar.png", enriched.avatarUrl)
        assertEquals("零号大坝", enriched.areaName)
        // uin 不再显示
        assertFalse(enriched.nickname.contains("3334612216573072958"))
    }

    @Test
    fun parseRoleInfo_decodesUrlEncodedNameAndAvatar() {
        val base = PlayerProfile.empty().copy(
            nickname = "3334612216573072958",
            avatarUrl = "",
        )
        val result = AmsDashboardParser.parseRoleInfo(
            """
            {"ret":0,"iRet":0,"sMsg":"ok","jData":{
              "iRet":"0","sMsg":"ok",
              "userData":{
                "picurl":"http%3A%2F%2Fthirdqq%2Eqlogo%2Ecn%2Fek%5Ftest%2F100",
                "charac_name":"%E5%85%84%E5%BC%9F%E6%9C%89%E7%A6%8F%E5%90%8C%E5%85%A5"
              },
              "careerData":{
                "solescaperatio":"31%",
                "soltotalfght":"1395",
                "soltotalkill":"1151",
                "totalprice":"97957099"
              }
            }}
            """.trimIndent(),
            base,
        )

        assertEquals("兄弟有福同入", result.nickname)
        assertEquals("https://thirdqq.qlogo.cn/ek_test/100", result.avatarUrl)
        assertEquals("31%", result.evacuationRate)
    }

    @Test
    fun parseRoleInfo_convertsNumericPicurlToObjectCdnAvatar() {
        val result = AmsDashboardParser.parseRoleInfo(
            """
            {"ret":0,"iRet":0,"sMsg":"ok","jData":{
              "iRet":"0","sMsg":"ok",
              "userData":{
                "picurl":"42010040702",
                "charac_name":"%E5%BE%AE%E4%BF%A1%E5%B9%B2%E5%91%98"
              },
              "careerData":{
                "solescaperatio":"30%",
                "soltotalkill":"2939"
              }
            }}
            """.trimIndent(),
            PlayerProfile.empty(),
        )

        assertEquals("微信干员", result.nickname)
        assertEquals(
            "https://playerhub.df.qq.com/playerhub/60004/object/42010040702.png",
            result.avatarUrl,
        )
    }

    @Test
    fun parseRoleInfo_preservesBaseWhenUserDataMissing() {
        val base = PlayerProfile.empty().copy(nickname = "已有昵称", avatarUrl = "https://existing.png")
        val result = AmsDashboardParser.parseRoleInfo(
            """{"ret":0,"iRet":0,"jData":{"iRet":"0","sMsg":"ok"}}""",
            base,
        )

        assertEquals("已有昵称", result.nickname)
        assertEquals("https://existing.png", result.avatarUrl)
    }
}

package com.local.dfcraftmonitor.data.backend

import org.junit.Assert.assertEquals
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
        assertTrue(result[0].imageUrl.endsWith("13030000115.png"))
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
}

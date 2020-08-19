/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.plugin.center

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.util.ConsoleExperimentalAPI
import java.io.File

@ConsoleExperimentalAPI
public interface PluginCenter {

    @Serializable
    @ConsoleExperimentalAPI
    public data class PluginInsight(
        val name: String,
        val version: String,
        @SerialName("core")
        val coreVersion: String,
        @SerialName("console")
        val consoleVersion: String,
        val author: String,
        val description: String,
        val tags: List<String>,
        val commands: List<String>
    )

    @ConsoleExperimentalAPI
    @Serializable
    public data class PluginInfo(
        val name: String,
        val version: String,
        @SerialName("core")
        val coreVersion: String,
        @SerialName("console")
        val consoleVersion: String,
        val tags: List<String>,
        val author: String,
        val contact: String,
        val description: String,
        val usage: String,
        val vcs: String,
        val commands: List<String>,
        val changeLog: List<String>
    )

    /**
     * 获取一些中心的插件基本信息,
     * 能获取到多少由实际的 [PluginCenter] 决定
     * 返回 插件名->Insight
     */
    public suspend fun fetchPlugin(page: Int): Map<String, PluginInsight>

    /**
     * 尝试获取到某个插件 by 全名, case sensitive
     * null 则没有
     */
    public suspend fun findPlugin(name: String): PluginInfo?


    public suspend fun <T : Any> T.downloadPlugin(name: String, progressListener: T.(Float) -> Unit): File

    public suspend fun downloadPlugin(name: String, progressListener: PluginCenter.(Float) -> Unit): File =
        downloadPlugin<PluginCenter>(name, progressListener)

    /**
     * 刷新
     */
    public suspend fun refresh()

    public val name: String
}

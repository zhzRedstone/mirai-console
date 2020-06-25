/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 *
 */

package net.mamoe.mirai.console.plugin.jvm

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("PropertyName")
open class AbstractJvmPlugin @JvmOverloads constructor(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : JvmPlugin {
    internal var _description: JvmPluginDescription? = null
    override val description: JvmPluginDescription
        get() = _description ?: error("Plugin not initialized")

    internal val _isEnable = AtomicBoolean(false)
    override val isEnable: Boolean
        get() = _isEnable.get()

    override val dataFolder: File by lazy {
        File(
            PluginManager.pluginsDataFolder,
            description.name
        ).apply { mkdir() }
    }

    override val logger: MiraiLogger by lazy { MiraiConsole.newLogger(description.name) }

    final override val coroutineContext: CoroutineContext
        get() = _coroutineContext

    @JvmField
    internal var _coroutineContext: CoroutineContext = coroutineContext + SupervisorJob(coroutineContext[Job])
}
/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE")

package net.mamoe.mirai.console.plugin

import kotlinx.atomicfu.locks.withLock
import net.mamoe.mirai.console.MiraiConsole
import java.io.File
import java.util.*
import java.util.concurrent.locks.ReentrantLock

val Plugin.description: PluginDescription
    get() = PluginManager.resolvedPlugins.firstOrNull { it == this }?.description ?: error("Plugin is unloaded")

inline fun PluginLoader<*, *>.register() = PluginManager.registerPluginLoader(this)
inline fun PluginLoader<*, *>.unregister() = PluginManager.unregisterPluginLoader(this)

object PluginManager {
    val pluginsDir = File(MiraiConsole.rootDir, "plugins").apply { mkdir() }
    val pluginsDataFolder = File(MiraiConsole.rootDir, "data").apply { mkdir() }

    private val _pluginLoaders: MutableList<PluginLoader<*, *>> = mutableListOf()
    private val loadersLock: ReentrantLock = ReentrantLock()

    @JvmField
    internal val resolvedPlugins: MutableList<Plugin> = mutableListOf()

    /**
     * 已加载的插件列表
     */
    @JvmStatic
    val plugins: List<Plugin>
        get() = resolvedPlugins.toList()

    /**
     * 内建的插件加载器列表. 由 [MiraiConsole] 初始化
     */
    @JvmStatic
    val builtInLoaders: List<PluginLoader<*, *>>
        get() = MiraiConsole.builtInPluginLoaders

    /**
     * 由插件创建的 [PluginLoader]
     */
    @JvmStatic
    val pluginLoaders: List<PluginLoader<*, *>>
        get() = _pluginLoaders.toList()

    @JvmStatic
    fun registerPluginLoader(loader: PluginLoader<*, *>): Boolean = loadersLock.withLock {
        if (_pluginLoaders.any { it::class == loader }) {
            return false
        }
        _pluginLoaders.add(loader)
    }

    @JvmStatic
    fun unregisterPluginLoader(loader: PluginLoader<*, *>) = loadersLock.withLock {
        _pluginLoaders.remove(loader)
    }


    // region LOADING

    /**
     * STEPS:
     * 1. 关闭全部插件, 即执行 [PluginLoader.disableAll]
     * 2. 遍历全部 loader, 获取当前可用的全部的 [PluginDescription]
     * 3. 依次执行 [PluginLoader.loadPlugin], 加载插件
     * 4. enable kind 为 [PluginKind.LOADER] 的插件
     * 4. enable kind 为 [PluginKind.NORMAL] 的插件
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(PluginMissingDependencyException::class)
    internal fun reloadPlugins() {
        builtInLoaders.forEach { it.disableAll() }
        data class PL(
            val plugin: Plugin,
            val loader: PluginLoader<Plugin, in PluginDescription>,
            val description: PluginDescription
        )

        val plugins = LinkedList<PL>()
        builtInLoaders.forEach { loader ->
            loader.listDescriptions().forEach { description ->
                kotlin.runCatching {
                    val plugin = (loader as PluginLoader<Plugin, in PluginDescription>).loadPlugin(description)
                    plugins.add(PL(plugin, loader, description))
                }.onFailure { exception ->
                    MiraiConsole.mainLogger.warning("Exception in loading plugin ${description.name}", exception)
                }
            }
        }
        plugins.forEach { pl ->
            if (pl.description.kind == PluginKind.LOADER) {
                pl.loader.enable(pl.plugin)
            }
        }
        plugins.forEach { pl ->
            if (pl.description.kind == PluginKind.NORMAL) {
                pl.loader.enable(pl.plugin)
            }
        }
    }

    // endregion
}

class PluginMissingDependencyException : PluginResolutionException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}

open class PluginResolutionException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}


internal data class PluginDescriptionWithLoader(
    @JvmField val loader: PluginLoader<*, PluginDescription>, // easier type
    @JvmField val delegate: PluginDescription
) : PluginDescription by delegate

@Suppress("UNCHECKED_CAST")
internal fun <D : PluginDescription> PluginDescription.unwrap(): D =
    if (this is PluginDescriptionWithLoader) this.delegate as D else this as D

@Suppress("UNCHECKED_CAST")
internal fun PluginDescription.wrapWith(loader: PluginLoader<*, *>): PluginDescriptionWithLoader =
    PluginDescriptionWithLoader(
        loader as PluginLoader<*, PluginDescription>, this
    )

internal operator fun List<PluginDescription>.contains(dependency: PluginDependency): Boolean =
    any { it.name == dependency.name }

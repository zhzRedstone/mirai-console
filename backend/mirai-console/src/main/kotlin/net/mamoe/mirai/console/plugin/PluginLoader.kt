/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused")

package net.mamoe.mirai.console.plugin

import net.mamoe.mirai.console.plugin.jvm.JarPluginLoader
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * 插件加载器.
 *
 * 插件加载器只实现寻找插件列表, 加载插件, 启用插件, 关闭插件这四个功能.
 *
 * 有关插件的依赖和已加载的插件列表由 [PluginManager] 维护.
 */
interface PluginLoader<P : Plugin, D : PluginDescription> {

    fun listDescriptions(): List<D>

    @Throws(PluginLoadException::class)
    fun loadPlugin(description: D): P

    fun loadedPlugins(): List<P>

    fun enable(plugin: P)
    fun disable(plugin: P)
    fun unload(plugin: P)
    fun disableAll()

}

open class PluginLoadException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}

/**
 * '/plugins' 目录中的插件的加载器. 每个加载器需绑定一个后缀.
 *
 * @see AbstractFilePluginLoader
 * @see JarPluginLoader 内建的 Jar (JVM) 插件加载器.
 */
interface FilePluginLoader<P : Plugin, D : PluginDescription> : PluginLoader<P, D> {
    /**
     * 所支持的插件文件后缀, 含 '.'. 如 [JarPluginLoader] 为 ".jar"
     */
    val fileSuffix: String
}

abstract class AbstractPluginLoader<P : Plugin, D : PluginDescription> : PluginLoader<P, D> {
    abstract fun listDescriptionsUnsorted(): List<D>
    override fun listDescriptions(): List<D> {
        val list = LinkedList<D>()
        val unsorted = listDescriptionsUnsorted()

        val allNames = unsorted.mapTo(HashSet()) { it.name }

        val desc = HashMap<String, D>().apply {
            unsorted.forEach { desc ->
                this[desc.name] = desc
            }
        }

        val loading = LinkedList<String>()
        fun load(name: String, optional: Boolean) {
            if (name in loading) {
                throw PluginLoadException("Infinite loop dependency chain: $loading")
            }
            loading.add(name)
            val des = desc[name] ?: run {
                if (!optional) {
                    if (name !in allNames) {
                        throw PluginLoadException("Unknown plugin: $name")
                    }
                }
                loading.remove(name)
                return@load
            }
            des.dependencies.forEach { dependency ->
                load(dependency.name, dependency.isOptional)
            }
            list.add(des)
            desc.remove(name)
            loading.remove(name)
        }

        val keys = desc.keys
        while (keys.isNotEmpty()) load(keys.iterator().next(), false)
        return list
    }
}


abstract class AbstractFilePluginLoader<P : Plugin, D : PluginDescription>(
    override val fileSuffix: String
) : AbstractPluginLoader<P, D>(), FilePluginLoader<P, D> {
    private fun pluginsFilesSequence(): Sequence<File> =
        PluginManager.pluginsDir.walk().filter { it.isFile && it.name.endsWith(fileSuffix, ignoreCase = true) }

    /**
     * 读取扫描到的后缀与 [fileSuffix] 相同的文件中的 [PluginDescription]
     */
    protected abstract fun Sequence<File>.mapToDescription(): List<D>

    override fun listDescriptionsUnsorted(): List<D> = pluginsFilesSequence().mapToDescription()
}
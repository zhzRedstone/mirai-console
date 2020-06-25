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

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.mamoe.mirai.console.plugin.AbstractFilePluginLoader
import net.mamoe.mirai.console.plugin.PluginLoadException
import net.mamoe.mirai.console.utils.instance
import net.mamoe.yamlkt.Yaml
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.jar.JarEntry
import java.util.jar.JarFile

object JarPluginLoader : AbstractFilePluginLoader<JvmPlugin, JvmPluginDescription>(".jar") {
    val loadedPlugins = ConcurrentLinkedQueue<JvmPlugin>()
    override fun loadPlugin(description: JvmPluginDescription): JvmPlugin {
        if (description.declarePlugin != null) {
            throw PluginLoadException("Plugin ${description.name} was loaded.")
        }
        description.dependencies.forEach { dependency ->
            if (dependency.isOptional) return@forEach
            if (loadedPlugins.none { it.description.name == dependency.name }) {
                throw PluginLoadException("Exception in loading ${description.name}: Missing dependency `${dependency.name}`")
            }
        }

        val loader = pluginLoaderUsing(description)
        val main = description.mainClassName
        try {
            val klass = loader.loadClass(main).asSubclass(AbstractJvmPlugin::class.java)
            val instance =
                klass.instance ?: throw IllegalAccessException("Failed to allocate a new instance for $klass")
            description.declarePlugin = instance
            instance._description = description
            loadedPlugins.add(instance)
            return instance.also { it.onLoad() }
        } catch (any: Throwable) {
            throw PluginLoadException("Failed to load plugin ${description.name}", any)
        }
    }

    override fun loadedPlugins(): List<JvmPlugin> {
        return LinkedList(loadedPlugins)
    }

    override fun enable(plugin: JvmPlugin) {
        with(plugin as AbstractJvmPlugin) {
            kotlin.runCatching {
                if (_isEnable.compareAndSet(false, true)) {
                    if (!_coroutineContext[Job]!!.isActive) {
                        _coroutineContext += SupervisorJob()
                    }
                    plugin.onEnable()
                }
            }.onFailure {
                plugin.logger.error("Exception ")
            }
        }
    }

    override fun disable(plugin: JvmPlugin) {
        with(plugin as AbstractJvmPlugin) {
            kotlin.runCatching {
                if (_isEnable.compareAndSet(true, false)) {
                    plugin.onDisable()
                    _coroutineContext[Job]!!.cancel()
                }
            }.onFailure {
                plugin.logger.error("Exception ")
            }
        }
    }

    override fun unload(plugin: JvmPlugin) {
        loadedPlugins.removeIf {
            if (plugin === it) {
                disable(it)
                close(it.description)
                true
            } else false
        }
    }

    private fun close(description: JvmPluginDescription) {
        description.declareLoader!!.close()
    }

    override fun disableAll() {
        loadedPlugins.removeIf {
            disable(it)
            close(it.description)
            true
        }
    }

    override fun Sequence<File>.mapToDescription(): List<JvmPluginDescription> {
        return mapNotNull { file ->
            runCatching {
                JarFile(file).use { jar ->
                    val entry = jar.findEntry() ?: return@mapNotNull null
                    val stream = jar.getInputStream(entry)!!
                    return@mapNotNull Yaml.nonStrict.parse(
                        JvmPluginDescription.serializer(),
                        stream.readBytes().toString(Charsets.UTF_8)
                    ).apply { _file = file }
                }
            }
            return@mapNotNull null
        }.toList()
    }

    private fun JarFile.findEntry(): JarEntry? {
        return getJarEntry("mirai-console-plugin.yml") ?: getJarEntry("plugin.yml")
    }
}
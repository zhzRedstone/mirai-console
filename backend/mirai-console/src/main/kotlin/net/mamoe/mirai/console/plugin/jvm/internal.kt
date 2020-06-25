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

import net.mamoe.mirai.console.plugin.PluginLoadException
import java.net.URLClassLoader

internal interface JvmPluginClassLoader {
    val description: JvmPluginDescription

    @Throws(ClassNotFoundException::class)
    fun loadClass(name: String): Class<*>

    @Throws(ClassNotFoundException::class)
    fun findClass(name: String, global: Boolean): Class<*>

    fun close()
}

internal val miraiConsoleClassLoader = java.lang.invoke.MethodHandles.lookup().lookupClass().classLoader

internal open class AndroidPluginClassLoader(
    override val description: JvmPluginDescription
) : JvmPluginClassLoader {

    override fun loadClass(name: String): Class<*> {
        TODO("Not yet implemented")
    }

    override fun findClass(name: String, global: Boolean): Class<*> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}

internal class JvmPluginClassLoader0(
    override val description: JvmPluginDescription
) : URLClassLoader(arrayOf(), miraiConsoleClassLoader), JvmPluginClassLoader {
    init {
        if (description.declareLoader != null) {
            throw PluginLoadException("This plugin was loaded.")
        }
        description.declareLoader = this

        @Suppress("UNSAFE_CALL")
        addURL(description._file.toURI().toURL())
    }

    override fun findClass(name: String): Class<*> {
        return findClass(name, true)
    }

    override fun findClass(name: String, global: Boolean): Class<*> {
        if (global) {
            try {
                return super.findClass(name)
            } catch (ignore: ClassNotFoundException) {
            }
            JarPluginLoader.loadedPlugins.forEach { plugin ->
                val loader = plugin.description.declareLoader ?: return@forEach
                if (loader !== this) {
                    try {
                        return loader.findClass(name, false)
                    } catch (ignore: ClassNotFoundException) {
                    }
                }
            }
            throw ClassNotFoundException(name)
        } else {
            synchronized(getClassLoadingLock(name)) {
                val loaded = findLoadedClass(name)
                if (loaded != null) return loaded
                return super.findClass(name)
            }
        }
    }
}

// for Android
internal var pluginLoaderUsing: (JvmPluginDescription) -> JvmPluginClassLoader = { JvmPluginClassLoader0(it) }
/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:JvmName("SettingsFactory")
@file:Suppress("unused")

package net.mamoe.mirai.console.plugins

import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.*
import kotlinx.serialization.SerialName
import net.mamoe.mirai.console.utils.ConsoleExperimentalAPI
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * 标注这个类是一个插件配置.
 */
@Target(AnnotationTarget.CLASS)
annotation class Settings

/**
 * 配置注释, 目前只会显示在图形前端中.
 */
@ConsoleExperimentalAPI("may be replaced with Description from konfig-core")
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class Description(val value: String)

/**
 * 配置显示在图形前端中的名称, 若未指定则使用属性名
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class UIName(val value: String)

/**
 * 配置显示在配置文件中的名称, 若未指定则使用属性名
 */
typealias SerialName = SerialName // kotlinx.serialization.SerialName

/*
qq跨群转发插件
使用 停止转发 或 启动转发 可设置是否转发你的消息
/forward
   group <群号> 将当前群加入/移除转发组
   start  开启转发
   stop  关闭转发
   lock <qq> 锁定某成员的转发开关
   forward <qq>  更改成员的转发开关
   avatar 转发时显示头像开关

 */

@Settings
interface ForwardSettings {
    var group: List<Long>

    var enabled: Boolean

    var locked: List<Long>

    var avatarEnabled: Boolean

    val child: Child

    interface Child {
        var s: String
    }
}


inline fun <reified T : Any> PluginBase.loadSettings() = loadSettings(T::class)

/**
 * 读取一个配置.
 *
 * @param clazz 目标配置 [Class]
 * @param filename 配置文件名含后缀, 默认使用类的全限定句点名称 ([KClass.qualifiedName]) 并加上 ".yml"
 */
@JvmOverloads
fun <T : Any> PluginBase.loadSettings(
    clazz: KClass<T>,
    filename: String = clazz.qualifiedName + ".yml"
        ?: error("Cannot retrieve classname automatically from a anonymous class. Please specify explicitly")
): T = loadSettingsImpl(clazz, filename)


//// INTERNAL


internal class SettingsImpl(
    val descriptor: SerialDescriptor,
    val map: SettingsMap
) {
    /**
     * Values: primitives or [SettingsImpl]
     */
    val delegates: MutableMap<String, Any?> = mutableMapOf()

    val writeLock: ReentrantLock = ReentrantLock()

    fun addClass(name: String, clazz: KClass<*>) {
        val value = map[name] ?: error("Cannot find corresponding settings for ${clazz.qualifiedName}")
        check(value is SettingsMap.ClassValue<*>) {
            "$name is not a ClassValue so cannot be loaded as ${clazz.qualifiedName}"
        }
        delegates[name] = value.load(clazz)
    }

    companion object {
        operator fun invoke(clazz: KClass<*>, source: ReadWriteConfig) {
            TODO()
        }
    }
}

@OptIn(ImplicitReflectionSerializer::class)
internal fun KClass<*>.parseSerialDescriptor(): SerialDescriptor {
    return SerialDescriptor(this.qualifiedName!!, StructureKind.CLASS) {
        for (memberProperty in memberProperties) {
            val type = memberProperty.returnType.classifier as? KClass<*>
                ?: error("Unsupported property '${memberProperty.name}' type: ${memberProperty.returnType}")
            if (type.findAnnotation<Settings>() != null) {
                element(
                    memberProperty.name,
                    type.parseSerialDescriptor(),
                    type.annotations,
                    memberProperty.returnType.isMarkedNullable
                )
            } else {
                element(
                    memberProperty.name,
                    type.serializer().descriptor,
                    type.annotations,
                    memberProperty.returnType.isMarkedNullable
                )
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> PluginBase.loadSettingsImpl(clazz: KClass<T>, name: String): T {
    require(clazz.java.isInterface) { "Settings class must be an interface, but found ${clazz.java}" }
    require(
        clazz.java.getAnnotationsByType(Settings::class.java).isNotEmpty()
    ) { "Settings class must be annotated with ${Settings::class.qualifiedName}" }

    val map = Yaml().load<Map<String, Any?>>(this.dataFolder.child(name).apply { createNewFile() }.inputStream())

    return SettingsMapImpl(map.mapValues {

    }).load(clazz)
}

internal fun Any?.toSettingsValue(): SettingsMap.Value<Any?> {
    when (this) {
        null -> return
        is Map<*, *> -> return SettingsMapImpl(this.map { it.key.toString() to it.value.toSettingsValue() })
    }
}

internal class SettingsListImpl(
    override var value: List<Any?>
) : SettingsMap.ListValue<List<Any?>>

internal class SettingsValueImpl(
    override var value: Any?
) : SettingsMap.Value<Any?>

internal class SettingsMapImpl(
    var content: Map<String, SettingsMap.Value<Any?>>
) : SettingsMap.ClassValue<SettingsMap>, Map<String, SettingsMap.Value<Any?>> by content {
    override var value: SettingsMap
        get() = this
        set(value) {}
}

internal fun File.child(name: String): File = File(this, name)

fun <T : Any> SettingsMap.load(clazz: KClass<T>): T {
    val settings = SettingsImpl(clazz.parseSerialDescriptor(), this)
    val invocationHandler = InvocationHandler { proxy, method, args ->
        val mName = method.name

        when (mName) {
            // intrinsics
            "toString" -> return@InvocationHandler settings.toString()
            "hashCode" -> return@InvocationHandler settings.hashCode()
            "equals" -> return@InvocationHandler proxy === args[0]
        }

        val name = when {
            mName.startsWith("get") -> method.name.substringAfter("get").toLowerCase()
            mName.startsWith("set") -> method.name.substringAfter("set").toLowerCase()
            else -> error("Ambiguous method name: $method. Please declare only `getXxx` and `setXxx` methods")
        }

        if (!settings.delegates.containsKey(mName)) {
            settings.writeLock.withLock {
                if (settings.delegates.containsKey(mName))
                    return@withLock
                if (!method.returnType.isMemberClass) {

                }
                settings.delegates.put(mName, settings.map[name])
            }
        }

        settings.delegates[mName]
    }
    @Suppress("UNCHECKED_CAST")
    return Proxy.newProxyInstance(clazz.java.classLoader, arrayOf(clazz.java), invocationHandler) as T
}


private val boxedPrimitives = arrayListOf(
    Int::class.java,
    Boolean::class.java,
    Byte::class.java,
    Short::class.java,
    Long::class.java,
    Float::class.java,
    Char::class.java,
    Double::class.java
)

internal fun Class<*>.isBoxedPrimitive(): Boolean {
    return this in boxedPrimitives
}
/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:JvmName("SettingsFactory")

package net.mamoe.mirai.console.plugins

import kotlinx.serialization.*
import kotlinx.serialization.SerialName
import net.mamoe.mirai.console.utils.ConsoleExperimentalAPI
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
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
 * 读取
 */
fun <T : Any> PluginBase.loadSettings(clazz: KClass<T>): T = loadSettingsImpl(clazz)


//// INTERNAL


internal class SettingsImpl(
    val descriptor: SerialDescriptor,
    val delegate: ReadWriteConfig
) {
    companion object {
        operator fun invoke(clazz: KClass<*>, source: ReadWriteConfig) {

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

internal fun <T : Any> PluginBase.loadSettingsImpl(clazz: KClass<T>): T {
    require(clazz.java.isInterface) { "Settings class must be an interface, but found ${clazz.java}" }
    require(
        clazz.java.getAnnotationsByType(Settings::class.java).isNotEmpty()
    ) { "Settings class must be annotated with ${Settings::class.qualifiedName}" }

    val settings = SettingsImpl(clazz.parseSerialDescriptor(), loadConfig(clazz.qualifiedName!!))
    val invocationHandler = InvocationHandler { proxy, method, args ->

    }
    Proxy.newProxyInstance(clazz.java.classLoader, clazz.java,)
}
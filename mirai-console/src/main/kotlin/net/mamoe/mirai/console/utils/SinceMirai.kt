package net.mamoe.mirai.console.utils

import kotlin.annotation.AnnotationTarget.*

/**
 * 标记一个自 mirai-console 某个版本起才支持或在这个版本修改过的 API.
 */
@Target(CLASS, PROPERTY, FIELD, CONSTRUCTOR, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class SinceConsole(val version: String)

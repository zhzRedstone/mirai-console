/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 *
 */

package net.mamoe.mirai.console.utils

import java.lang.reflect.Modifier


val <T : Any> Class<T>.instance: T?
    get() {
        val instance = this.kotlin
        instance.objectInstance?.let { return it }
        runCatching {
            getDeclaredMethod("getInstance").takeIf { Modifier.isStatic(it.modifiers) }?.let { met ->
                met.isAccessible = true
                cast(met.invoke(null))?.let { return it }
            }
        }
        runCatching {
            getDeclaredField("INSTANCE").takeIf { Modifier.isStatic(it.modifiers) }?.let { field ->
                field.isAccessible = true
                cast(field.get(null))?.let { return it }
            }
        }
        runCatching {
            val cons = getDeclaredConstructor()
            cons.isAccessible = true
            return cons.newInstance()
        }
        return null
    }

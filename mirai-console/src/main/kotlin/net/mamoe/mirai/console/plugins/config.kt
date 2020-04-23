/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:JvmName("ConfigFactory")
@file:Suppress("MemberVisibilityCanBePrivate", "DEPRECATION_ERROR", "unused")

package net.mamoe.mirai.console.plugins

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import java.io.File

/**
 * 插件配置. 值可能会被 UI 端改动
 */
interface Setting : Map<String, Setting.Value<Any?>> {

    /**
     * 配置值. 可能会被 UI 端动态改动
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    interface Value<T> {
        @get:JvmName("get")
        val value: T
    }
}


/**
 * 只读数据文件.
 */
interface ReadOnlyConfig : Map<String, Any?> {
    // Member functions rather than extensions to make Java user happier

    /* final */ fun getString(key: String): String? = this[key]?.toString()
    /* final */ fun getInt(key: String): Int? = this[key]?.toString()?.toIntOrNull()
    /* final */ fun getFloat(key: String): Float? = this[key]?.toString()?.toFloatOrNull()
    /* final */ fun getDouble(key: String): Double? = this[key]?.toString()?.toDoubleOrNull()
    /* final */ fun getLong(key: String): Long? = this[key]?.toString()?.toLongOrNull()
    /* final */ fun getBoolean(key: String): Boolean? = this[key]?.toString()?.toBoolean()
    /* final */ fun getShort(key: String): Short? = this[key]?.toString()?.toShort()
    /* final */ fun getByte(key: String): Byte? = this[key]?.toString()?.toByte()

    /* final */ fun getList(key: String): ImmutableList<Any?>? = (this[key] as List<Any?>?)?.toPersistentList()

    /* final */ fun getStringList(key: String): ImmutableList<String>? =
        (this[key] as List<*>?)?.map { it.toString() }?.toPersistentList()

    /* final */ fun getIntList(key: String): ImmutableList<Int>? =
        (this[key] as List<*>?)?.map { it.toString().toInt() }?.toPersistentList()

    /* final */ fun getFloatList(key: String): ImmutableList<Float>? =
        (this[key] as List<*>?)?.map { it.toString().toFloat() }?.toPersistentList()

    /* final */ fun getDoubleList(key: String): ImmutableList<Double>? =
        (this[key] as List<*>?)?.map { it.toString().toDouble() }?.toPersistentList()

    /* final */ fun getLongList(key: String): ImmutableList<Long>? =
        (this[key] as List<*>?)?.map { it.toString().toLong() }?.toPersistentList()

    /* final */ fun getShortList(key: String): ImmutableList<Short>? =
        (this[key] as List<*>?)?.map { it.toString().toShort() }?.toPersistentList()

    /* final */ fun getByteList(key: String): ImmutableList<Byte>? =
        (this[key] as List<*>?)?.map { it.toString().toByte() }?.toPersistentList()

    /* final */ fun getBooleanList(key: String): ImmutableList<Boolean>? =
        (this[key] as List<*>?)?.map { it.toString().toBoolean() }?.toPersistentList()

    /* final */ fun getConfig(key: String): ReadOnlyConfig? =
        (this[key] as Map<*, *>?)?.mapKeys { it.toString() }?.toPersistentMap()?.toConfig()
}

@JvmName("fromMap")
fun ImmutableMap<String, Any?>.toConfig(): ReadOnlyConfig {
    return object : ReadOnlyConfig, Map<String, Any?> by this {}
}

@JvmName("fromMapMutable")
@JvmSynthetic
fun Map<String, Any?>.toConfig(): ReadOnlyConfig {
    return object : ReadOnlyConfig, Map<String, Any?> by this.toPersistentMap() {}
}

@JvmName("fromMap")
fun MutableMap<String, Any?>.toConfig(): ReadOnlyConfig {
    return object : ReadOnlyConfig, Map<String, Any?> by this {}
}

/**
 * 可读可写配置文件
 */
interface ReadWriteConfig : ReadOnlyConfig, MutableMap<String, Any?>

enum class ConfigFormat {
    YAML,
    JSON,
    TOML
}

/**
 * 将文件加载为只读配置
 */
fun File.loadAsReadOnlyConfig(): ReadOnlyConfig {
    require(this.canRead()) { "file cannot be read" }
    return Config.load(this)
}


/*

/* 最简单的代理 */
inline operator fun <reified T : Any> Config.getValue(thisRef: Any?, property: KProperty<*>): T {
    return smartCast(property)
}

inline operator fun <reified T : Any> Config.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this[property.name] = value
}

/* 带有默认值的代理 */
@Suppress("unused")
inline fun <reified T : Any> Config.withDefault(
    crossinline defaultValue: () -> T
): ReadWriteProperty<Any, T> {
    return object : ReadWriteProperty<Any, T> {
        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            if (this@withDefault.exist(property.name)) {//unsafe
                return this@withDefault.smartCast(property)
            }
            return defaultValue()
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            this@withDefault[property.name] = value
        }
    }
}

/* 带有默认值且如果为空会写入的代理 */
@Suppress("unused")
inline fun <reified T : Any> Config.withDefaultWrite(
    noinline defaultValue: () -> T
): WithDefaultWriteLoader<T> {
    return WithDefaultWriteLoader(
        T::class,
        this,
        defaultValue,
        false
    )
}

/* 带有默认值且如果为空会写入保存的代理 */
inline fun <reified T : Any> Config.withDefaultWriteSave(
    noinline defaultValue: () -> T
): WithDefaultWriteLoader<T> {
    return WithDefaultWriteLoader(T::class, this, defaultValue, true)
}

class WithDefaultWriteLoader<T : Any>(
    private val _class: KClass<T>,
    private val config: Config,
    private val defaultValue: () -> T,
    private val save: Boolean
) {
    operator fun provideDelegate(
        thisRef: Any,
        prop: KProperty<*>
    ): ReadWriteProperty<Any, T> {
        val defaultValue by lazy { defaultValue.invoke() }
        if (!config.contains(prop.name)) {
            config[prop.name] = defaultValue
            if (save) {
                config.save()
            }
        }
        return object : ReadWriteProperty<Any, T> {
            override fun getValue(thisRef: Any, property: KProperty<*>): T {
                if (config.exist(property.name)) {//unsafe
                    return config.smartCastInternal(property.name, _class)
                }
                return defaultValue
            }

            override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
                config[property.name] = value
            }
        }
    }
}

@PublishedApi
internal inline fun <reified T : Any> Config.smartCast(property: KProperty<*>): T {
    return smartCastInternal(property.name, T::class)
}

@PublishedApi
@Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
internal fun <T : Any> Config.smartCastInternal(propertyName: String, _class: KClass<T>): T {
    return when (_class) {
        String::class -> this.getString(propertyName)
        Int::class -> this.getInt(propertyName)
        Float::class -> this.getFloat(propertyName)
        Double::class -> this.getDouble(propertyName)
        Long::class -> this.getLong(propertyName)
        Boolean::class -> this.getBoolean(propertyName)
        else -> when {
            _class.isSubclassOf(ConfigSection::class) -> this.getConfigSection(propertyName)
            _class == List::class || _class == MutableList::class -> {
                val list = this.getList(propertyName) ?: throw NoSuchElementException(propertyName)
                return if (list.isEmpty()) {
                    list
                } else {
                    when (list[0]!!::class) {
                        String::class -> getStringList(propertyName)
                        Int::class -> getIntList(propertyName)
                        Float::class -> getFloatList(propertyName)
                        Double::class -> getDoubleList(propertyName)
                        Long::class -> getLongList(propertyName)
                        //不去支持getConfigSectionList(propertyName)
                        // LinkedHashMap::class -> getConfigSectionList(propertyName)//faster approach
                        else -> {
                            //if(list[0]!! is ConfigSection || list[0]!! is Map<*,*>){
                            // getConfigSectionList(propertyName)
                            //}else {
                            error("unsupported type" + list[0]!!::class)
                            //}
                        }
                    }
                } as T
            }
            else -> {
                error("unsupported type")
            }
        }
    } as T
}



interface ConfigSection : Config {
    companion object {
        fun create(): ConfigSection {
            return ConfigSectionImpl()
        }

        fun new(): ConfigSection {
            return this.create()
        }
    }

    override fun getConfigSection(key: String): ConfigSection {
        val content = get(key) ?: throw NoSuchElementException(key)
        if (content is ConfigSection) {
            return content
        }
        @Suppress("UNCHECKED_CAST")
        return ConfigSectionDelegation(
            Collections.synchronizedMap(
                (get(key) ?: throw NoSuchElementException(key)) as LinkedHashMap<String, Any?>
            )
        )
    }
}

internal inline fun <reified T : Any> ConfigSection.smartGet(key: String): T {
    return this.smartCastInternal(key, T::class)
}

@Serializable
open class ConfigSectionImpl : ConcurrentHashMap<String, Any?>(), ConfigSection {
    override operator fun get(key: String): Any? {
        return super.get(key)
    }

    @Suppress("RedundantOverride")
    override fun contains(key: String): Boolean {
        return super.contains(key)
    }

    override fun exist(key: String): Boolean {
        return containsKey(key)
    }

    override fun asMap(): Map<String, Any?> {
        return this
    }

    override fun save() {

    }

    override fun put(key: String, value: Any?): Any? {
        return if (value != null) {
            super.put(key, value)
        } else {
            super.get(key).also {
                super<ConcurrentHashMap>.remove(key)
            }
        }
    }
}

open class ConfigSectionDelegation(
    private val delegate: MutableMap<String, Any?>
) : ConfigSection, MutableMap<String, Any?> by delegate {
    override fun contains(key: String): Boolean {
        return delegate.containsKey(key)
    }

    override fun asMap(): Map<String, Any?> {
        return delegate
    }

    override fun save() {

    }
}


interface FileConfig : Config {
    fun deserialize(content: String): ConfigSection

    fun serialize(config: ConfigSection): String
}


@MiraiInternalAPI
abstract class FileConfigImpl internal constructor(
    private val rawContent: String
) : FileConfig,
    ConfigSection {

    internal var file: File? = null


    @Suppress("unused")
    constructor(file: File) : this(file.readText()) {
        this.file = file
    }


    private val content by lazy {
        deserialize(rawContent)
    }


    override val size: Int get() = content.size
    override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>> get() = content.entries
    override val keys: MutableSet<String> get() = content.keys
    override val values: MutableCollection<Any?> get() = content.values
    override fun containsKey(key: String): Boolean = content.containsKey(key)
    override fun containsValue(value: Any?): Boolean = content.containsValue(value)
    override fun put(key: String, value: Any?): Any? = content.put(key, value)
    override fun isEmpty(): Boolean = content.isEmpty()
    override fun putAll(from: Map<out String, Any?>) = content.putAll(from)
    override fun clear() = content.clear()
    override fun remove(key: String): Any? = content.remove(key)

    override fun save() {
        if (isReadOnly) {
            error("Config is readonly")
        }
        if (!((file?.exists())!!)) {
            file?.createNewFile()
        }
        file?.writeText(serialize(content))
    }

    val isReadOnly: Boolean get() = file == null

    override fun contains(key: String): Boolean {
        return content.contains(key)
    }

    override fun get(key: String): Any? {
        return content[key]
    }

    override fun asMap(): Map<String, Any?> {
        return content.asMap()
    }

}

@OptIn(MiraiInternalAPI::class)
class JsonConfig internal constructor(
    content: String
) : FileConfigImpl(content) {
    constructor(file: File) : this(file.readText()) {
        this.file = file
    }

    @UnstableDefault
    companion object {
        private val json = Json(
            JsonConfiguration(
                ignoreUnknownKeys = true,
                isLenient = true,
                prettyPrint = true
            )
        )
    }

    @OptIn(ImplicitReflectionSerializer::class)
    @UnstableDefault
    override fun deserialize(content: String): ConfigSection {
        if (content.isEmpty() || content.isBlank() || content == "{}") {
            return ConfigSectionImpl()
        }

        json.parseJson(content).jsonObject
        val gson = Gson()
        val typeRef = object : TypeToken<Map<String, Any?>>() {}.type
        return ConfigSectionDelegation(
            gson.fromJson(content, typeRef)
        )
    }

    @UnstableDefault
    override fun serialize(config: ConfigSection): String {
        val gson = Gson()
        return gson.toJson(config.toMap())
    }
}

@OptIn(MiraiInternalAPI::class)
class YamlConfig internal constructor(content: String) : FileConfigImpl(content) {
    constructor(file: File) : this(file.readText()) {
        this.file = file
    }

    override fun deserialize(content: String): ConfigSection {
        if (content.isEmpty() || content.isBlank()) {
            return ConfigSectionImpl()
        }
        return ConfigSectionDelegation(
            Collections.synchronizedMap(
                Yaml().load(content) as LinkedHashMap<String, Any?>
            )
        )
    }

    override fun serialize(config: ConfigSection): String {
        return Yaml().dumpAsMap(config)
    }

}

@OptIn(MiraiInternalAPI::class)
class TomlConfig internal constructor(content: String) : FileConfigImpl(content) {
    constructor(file: File) : this(file.readText()) {
        this.file = file
    }

    override fun deserialize(content: String): ConfigSection {
        if (content.isEmpty() || content.isBlank()) {
            return ConfigSectionImpl()
        }
        return ConfigSectionDelegation(
            Collections.synchronizedMap(
                Toml().read(content).toMap()
            )
        )

    }

    override fun serialize(config: ConfigSection): String {
        return TomlWriter().write(config)
    }
}*/
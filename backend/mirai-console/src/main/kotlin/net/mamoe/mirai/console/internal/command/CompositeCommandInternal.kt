/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "MemberVisibilityCanBePrivate", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package net.mamoe.mirai.console.internal.command

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.Command.Companion.primaryName
import net.mamoe.mirai.console.command.description.CommandArgumentContext
import net.mamoe.mirai.console.command.description.CommandArgumentContextAware
import net.mamoe.mirai.console.command.description.CommandParam
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.SingleMessage
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*

internal object CompositeCommandSubCommandAnnotationResolver :
    AbstractReflectionCommand.SubCommandAnnotationResolver {
    override fun hasAnnotation(function: KFunction<*>) = function.hasAnnotation<CompositeCommand.SubCommand>()
    override fun getSubCommandNames(function: KFunction<*>): Array<out String> =
        function.findAnnotation<CompositeCommand.SubCommand>()!!.value
}

internal object SimpleCommandSubCommandAnnotationResolver :
    AbstractReflectionCommand.SubCommandAnnotationResolver {
    override fun hasAnnotation(function: KFunction<*>) = function.hasAnnotation<SimpleCommand.Handler>()
    override fun getSubCommandNames(function: KFunction<*>): Array<out String> = arrayOf("")
}

internal abstract class AbstractReflectionCommand @JvmOverloads constructor(
    owner: CommandOwner,
    names: Array<out String>,
    description: String = "<no description available>",
    permission: CommandPermission = CommandPermission.Default,
    prefixOptional: Boolean = false
) : Command, AbstractCommand(
    owner,
    names = names,
    description = description,
    permission = permission,
    prefixOptional = prefixOptional
), CommandArgumentContextAware {
    internal abstract val subCommandAnnotationResolver: SubCommandAnnotationResolver

    @JvmField
    @Suppress("PropertyName")
    internal var _usage: String = "<not yet initialized>"

    override val usage: String  // initialized by subCommand reflection
        get() = _usage

    abstract suspend fun CommandSender.onDefault(rawArgs: Array<out Any>)

    internal val defaultSubCommand: DefaultSubCommandDescriptor by lazy {
        DefaultSubCommandDescriptor(
            "",
            permission,
            onCommand = { sender: CommandSender, args: Array<out Any> ->
                sender.onDefault(args)
            }
        )
    }

    internal open fun checkSubCommand(subCommands: Array<SubCommandDescriptor>) {

    }

    interface SubCommandAnnotationResolver {
        fun hasAnnotation(function: KFunction<*>): Boolean
        fun getSubCommandNames(function: KFunction<*>): Array<out String>
    }

    internal val subCommands: Array<SubCommandDescriptor> by lazy {
        this::class.declaredFunctions.filter { subCommandAnnotationResolver.hasAnnotation(it) }
            .also { subCommandFunctions ->
                // overloading not yet supported
                val overloadFunction = subCommandFunctions.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
                if (overloadFunction != null) {
                    error("Sub command overloading is not yet supported. (at ${this::class.qualifiedNameOrTip}.${overloadFunction.key})")
                }
            }.map { function ->
                createSubCommand(function, context)
            }.toTypedArray().also {
                _usage = it.firstOrNull()?.usage ?: description
            }.also { checkSubCommand(it) }
    }

    internal val bakedCommandNameToSubDescriptorArray: Map<Array<String>, SubCommandDescriptor> by lazy {
        kotlin.run {
            val map = LinkedHashMap<Array<String>, SubCommandDescriptor>(subCommands.size * 2)
            for (descriptor in subCommands) {
                for (name in descriptor.bakedSubNames) {
                    map[name] = descriptor
                }
            }
            map.toSortedMap { o1, o2 -> o1!!.contentHashCode() - o2!!.contentHashCode() }
        }
    }

    internal class DefaultSubCommandDescriptor(
        val description: String,
        val permission: CommandPermission,
        val onCommand: suspend (sender: CommandSender, rawArgs: Array<out Any>) -> Unit
    )

    internal class SubCommandDescriptor(
        val names: Array<out String>,
        val params: Array<CommandParam<*>>,
        val description: String,
        val permission: CommandPermission,
        val onCommand: suspend (sender: CommandSender, parsedArgs: Array<out Any>) -> Boolean,
        val context: CommandArgumentContext,
        val usage: String
    ) {
        internal suspend inline fun parseAndExecute(
            sender: CommandSender,
            argsWithSubCommandNameNotRemoved: Array<out Any>,
            removeSubName: Boolean
        ) {
            val args = parseArgs(sender, argsWithSubCommandNameNotRemoved, if (removeSubName) names.size else 0)
            if (args == null || !onCommand(
                    sender,
                    args
                )
            ) {
                sender.sendMessage(usage)
            }
        }

        @JvmField
        internal val bakedSubNames: Array<Array<String>> = names.map { it.bakeSubName() }.toTypedArray()
        private fun parseArgs(sender: CommandSender, rawArgs: Array<out Any>, offset: Int): Array<out Any>? {
            if (rawArgs.size < offset + this.params.size)
                return null
            //require(rawArgs.size >= offset + this.params.size) { "No enough args. Required ${params.size}, but given ${rawArgs.size - offset}" }

            return Array(this.params.size) { index ->
                val param = params[index]
                val rawArg = rawArgs[offset + index]
                when (rawArg) {
                    is String -> context[param.type]?.parse(rawArg, sender)
                    is SingleMessage -> context[param.type]?.parse(rawArg, sender)
                    else -> throw IllegalArgumentException("Illegal argument type: ${rawArg::class.qualifiedName}")
                } ?: error("Cannot find a parser for $rawArg")
            }
        }
    }

    /**
     * @param rawArgs 元素类型必须为 [SingleMessage] 或 [String], 且已经经过扁平化处理. 否则抛出异常 [IllegalArgumentException]
     */
    internal fun matchSubCommand(rawArgs: Array<out Any>): SubCommandDescriptor? {
        val maxCount = rawArgs.size - 1
        var cur = 0
        bakedCommandNameToSubDescriptorArray.forEach { (name, descriptor) ->
            if (name.size != cur) {
                if (cur++ == maxCount) return null
            }
            if (name.contentEqualsOffset(rawArgs, length = cur)) {
                return descriptor
            }
        }
        return null
    }
}

internal fun <T> Array<T>.contentEqualsOffset(other: Array<out Any>, length: Int): Boolean {
    repeat(length) { index ->
        if (other[index].toString() != this[index]) {
            return false
        }
    }
    return true
}

internal val ILLEGAL_SUB_NAME_CHARS = "\\/!@#$%^&*()_+-={}[];':\",.<>?`~".toCharArray()
internal fun String.isValidSubName(): Boolean = ILLEGAL_SUB_NAME_CHARS.none { it in this }
internal fun String.bakeSubName(): Array<String> = split(' ').filterNot { it.isBlank() }.toTypedArray()

internal fun Any.flattenCommandComponents(): ArrayList<Any> {
    val list = ArrayList<Any>()
    when (this) {
        is PlainText -> this.content.splitToSequence(' ').filterNot { it.isBlank() }
            .forEach { list.add(it) }
        is CharSequence -> this.splitToSequence(' ').filterNot { it.isBlank() }.forEach { list.add(it) }
        is SingleMessage -> list.add(this)
        is Array<*> -> this.forEach { if (it != null) list.addAll(it.flattenCommandComponents()) }
        is Iterable<*> -> this.forEach { if (it != null) list.addAll(it.flattenCommandComponents()) }
        else -> list.add(this.toString())
    }
    return list
}

internal inline fun <reified T : Annotation> KAnnotatedElement.hasAnnotation(): Boolean =
    findAnnotation<T>() != null

internal inline fun <T : Any> KClass<out T>.getInstance(): T {
    return this.objectInstance ?: this.createInstance()
}

internal val KClass<*>.qualifiedNameOrTip: String get() = this.qualifiedName ?: "<anonymous class>"

internal fun AbstractReflectionCommand.createSubCommand(
    function: KFunction<*>,
    context: CommandArgumentContext
): AbstractReflectionCommand.SubCommandDescriptor {
    val notStatic = !function.hasAnnotation<JvmStatic>()
    val overridePermission = function.findAnnotation<CompositeCommand.Permission>()//optional
    val subDescription =
        function.findAnnotation<CompositeCommand.Description>()?.value ?: "<no description available>"

    fun KClass<*>.isValidReturnType(): Boolean {
        return when (this) {
            Boolean::class, Void::class, Unit::class, Nothing::class -> true
            else -> false
        }
    }

    check((function.returnType.classifier as? KClass<*>)?.isValidReturnType() == true) {
        error("Return type of sub command ${function.name} must be one of the following: kotlin.Boolean, java.lang.Boolean, kotlin.Unit (including implicit), kotlin.Nothing, boolean or void (at ${this::class.qualifiedNameOrTip}.${function.name})")
    }

    check(!function.returnType.isMarkedNullable) {
        error("Return type of sub command ${function.name} must not be marked nullable in Kotlin, and must be marked with @NotNull or @NonNull explicitly in Java. (at ${this::class.qualifiedNameOrTip}.${function.name})")
    }

    val parameters = function.parameters.toMutableList()

    if (notStatic) parameters.removeAt(0) // instance

    var hasSenderParam = false
    check(parameters.isNotEmpty()) {
        "Parameters of sub command ${function.name} must not be empty. (Must have CommandSender as its receiver or first parameter or absent, followed by naturally typed params) (at ${this::class.qualifiedNameOrTip}.${function.name})"
    }

    parameters.forEach { param ->
        check(!param.isVararg) {
            "Parameter $param must not be vararg. (at ${this::class.qualifiedNameOrTip}.${function.name}.$param)"
        }
    }

    (parameters.first()).let { receiver ->
        if ((receiver.type.classifier as? KClass<*>)?.isSubclassOf(CommandSender::class) == true) {
            hasSenderParam = true
            parameters.removeAt(0)
        }
    }

    val commandName =
        subCommandAnnotationResolver.getSubCommandNames(function)
            .let { namesFromAnnotation ->
                if (namesFromAnnotation.isNotEmpty()) {
                    namesFromAnnotation
                } else arrayOf(function.name)
            }.also { names ->
                names.forEach {
                    check(it.isValidSubName()) {
                        "Name of sub command ${function.name} is invalid"
                    }
                }
            }

    val buildUsage = StringBuilder(this.description).append(": \n")

    //map parameter
    val params = parameters.map { param ->
        buildUsage.append("/$primaryName ")

        if (param.isOptional) error("optional parameters are not yet supported. (at ${this::class.qualifiedNameOrTip}.${function.name}.$param)")

        val argName = param.findAnnotation<CompositeCommand.Name>()?.value ?: param.name ?: "unknown"
        buildUsage.append("<").append(argName).append("> ").append(" ")
        CommandParam(
            argName,
            (param.type.classifier as? KClass<*>)
                ?: throw IllegalArgumentException("unsolved type reference from param " + param.name + ". (at ${this::class.qualifiedNameOrTip}.${function.name}.$param)")
        )
    }.toTypedArray()

    buildUsage.append(subDescription).append("\n")

    return AbstractReflectionCommand.SubCommandDescriptor(
        commandName,
        params,
        subDescription,
        overridePermission?.value?.getInstance() ?: permission,
        onCommand = { sender: CommandSender, args: Array<out Any> ->
            val result = if (notStatic) {
                if (hasSenderParam) {
                    function.isSuspend
                    function.callSuspend(this, sender, *args)
                } else function.callSuspend(this, *args)
            } else {
                if (hasSenderParam) {
                    function.callSuspend(sender, *args)
                } else function.callSuspend(*args)
            }

            checkNotNull(result) { "sub command return value is null (at ${this::class.qualifiedName}.${function.name})" }

            result as? Boolean ?: true // Unit, void is considered as true.
        },
        context = context,
        usage = buildUsage.toString()
    )
}
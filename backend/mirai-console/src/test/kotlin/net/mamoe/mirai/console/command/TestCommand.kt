/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused")

package net.mamoe.mirai.console.command

import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.Testing
import net.mamoe.mirai.console.Testing.withTesting
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.execute
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.executeCommand
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.registeredCommands
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregisterAllCommands
import net.mamoe.mirai.console.command.description.CommandArgumentContext
import net.mamoe.mirai.console.command.description.CommandArgumentParser
import net.mamoe.mirai.console.initTestEnvironment
import net.mamoe.mirai.console.internal.command.flattenCommandComponents
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.SingleMessage
import net.mamoe.mirai.message.data.toMessage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.*

object TestCompositeCommand : CompositeCommand(
    ConsoleCommandOwner,
    "testComposite", "tsC"
) {
    @SubCommand
    fun mute(seconds: Int) {
        Testing.ok(seconds)
    }
}


object TestSimpleCommand : RawCommand(owner, "testSimple", "tsS") {
    override suspend fun CommandSender.onCommand(args: Array<out Any>) {
        Testing.ok(args)
    }
}

internal val sender by lazy { ConsoleCommandSender.instance }
internal val owner by lazy { ConsoleCommandOwner }


internal class TestCommand {
    companion object {
        @JvmStatic
        @BeforeAll
        fun init() {
            initTestEnvironment()
        }

        @AfterAll
        @JvmStatic
        fun destroy() {
            MiraiConsole.cancel()
        }
    }

    @Test
    fun testRegister() {
        try {
            ConsoleCommandOwner.unregisterAllCommands() // builtins

            assertTrue(TestCompositeCommand.register())
            assertFalse(TestCompositeCommand.register())

            assertEquals(1, ConsoleCommandOwner.registeredCommands.size)

            assertEquals(1, CommandManagerImpl.registeredCommands.size)
            assertEquals(2, CommandManagerImpl.requiredPrefixCommandMap.size)
        } finally {
            TestCompositeCommand.unregister()
        }
    }

    @Test
    fun testSimpleExecute() = runBlocking {
        assertEquals(arrayOf("test").contentToString(), withTesting<Array<String>> {
            TestSimpleCommand.execute(sender, "test")
        }.contentToString())
    }

    @Test
    fun `test flattenCommandArgs`() {
        val result = arrayOf("test", image).flattenCommandComponents().toTypedArray()

        assertEquals("test", result[0])
        assertSame(image, result[1])

        assertEquals(2, result.size)
    }

    @Test
    fun testSimpleArgsSplitting() = runBlocking {
        assertEquals(arrayOf("test", "ttt", "tt").contentToString(), withTesting<Array<String>> {
            TestSimpleCommand.execute(sender, "test ttt tt".toMessage())
        }.contentToString())
    }

    val image = Image("/f8f1ab55-bf8e-4236-b55e-955848d7069f")

    @Test
    fun `PlainText and Image args splitting`() = runBlocking {
        val result = withTesting<Array<Any>> {
            TestSimpleCommand.execute(sender, "test", image, "tt")
        }
        assertEquals(arrayOf("test", image, "tt").contentToString(), result.contentToString())
        assertSame(image, result[1])
    }

    @Test
    fun `test throw Exception`() = runBlocking {
        assertEquals(null, sender.executeCommand(""))
    }

    @Test
    fun `executing command by string command`() = runBlocking {
        TestCompositeCommand.register()
        val result = withTesting<Int> {
            assertNotNull(sender.executeCommand("/testComposite", "mute 1"))
        }

        assertEquals(1, result)
    }

    @Test
    fun `composite command executing`() = runBlocking {
        assertEquals(1, withTesting {
            assertNotNull(TestCompositeCommand.execute(sender, "mute 1"))
        })
    }

    @Test
    fun `composite sub command resolution conflict`() {
        runBlocking {
            val composite = object : CompositeCommand(
                ConsoleCommandOwner,
                "tr"
            ) {
                @Suppress("UNUSED_PARAMETER")
                @SubCommand
                fun mute(seconds: Int) {
                    Testing.ok(1)
                }

                @Suppress("UNUSED_PARAMETER")
                @SubCommand
                fun mute(seconds: Int, arg2: Int) {
                    Testing.ok(2)
                }
            }

            assertFailsWith<IllegalStateException> {
                composite.register()
            }
            /*
            composite.withRegistration {
                assertEquals(1, withTesting { execute(sender, "tr", "mute 123") }) // one args, resolves to mute(Int)
                assertEquals(2, withTesting { execute(sender, "tr", "mute 123 123") })
            }*/
        }
    }

    @Test
    fun `composite sub command parsing`() {
        runBlocking {
            class MyClass(
                val value: Int
            )

            val composite = object : CompositeCommand(
                ConsoleCommandOwner,
                "test",
                overrideContext = CommandArgumentContext {
                    add(object : CommandArgumentParser<MyClass> {
                        override fun parse(raw: String, sender: CommandSender): MyClass {
                            return MyClass(raw.toInt())
                        }

                        override fun parse(raw: SingleMessage, sender: CommandSender): MyClass {
                            assertSame(image, raw)
                            return MyClass(2)
                        }
                    })
                }
            ) {
                @SubCommand
                fun mute(seconds: MyClass) {
                    Testing.ok(seconds)
                }
            }

            composite.withRegistration {
                assertEquals(333, withTesting<MyClass> { execute(sender, "mute 333") }.value)
                assertEquals(2, withTesting<MyClass> { execute(sender, "mute", image) }.value)
            }
        }
    }

    @Test
    fun `test simple command`() {
        runBlocking {

            val simple = object : SimpleCommand(owner, "test") {
                @Handler
                fun onCommand(string: String) {
                    Testing.ok(string)
                }
            }

            simple.withRegistration {
                assertEquals("xxx", withTesting { simple.execute(sender, "xxx") })
                assertEquals("xxx", withTesting { sender.executeCommand("/test xxx") })
            }
        }
    }
}

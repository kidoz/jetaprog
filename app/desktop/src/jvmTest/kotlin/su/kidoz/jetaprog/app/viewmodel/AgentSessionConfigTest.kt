package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.acp.protocol.AcpConnection
import su.kidoz.jetaprog.acp.protocol.AcpTransport
import su.kidoz.jetaprog.app.agent.AgentHistoryStore
import su.kidoz.jetaprog.app.agent.AgentSessionRecord
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONFIG = """[
 {"id":"engine","name":"Model","category":"model","type":"select","currentValue":"fast","options":[{"group":"models","name":"Available models","options":[{"value":"fast","name":"Fast"},{"value":"deep","name":"Deep"}]}]},
 {"id":"reasoning","name":"Reasoning","category":"thought_level","type":"select","currentValue":"low","options":[{"value":"low","name":"Low"},{"value":"high","name":"High"}]},
 {"id":"future","name":"Unknown","type":"future-control","currentValue":{},"options":{"future":true}}
]"""

/** Exercises the real ACP codec and client while controlling response and notification ordering. */
private class ConfigAgent(
    private val early: Boolean = false,
    private val initialConfig: String = CONFIG,
) : AcpTransport {
    val inbound = Channel<String>(Channel.UNLIMITED)
    override val incoming = inbound.receiveAsFlow()
    val requests = mutableListOf<JsonObject>()
    var hold = false
    var reject = false
    var pending: JsonElement? = null
    var responseConfig: String? = null

    override suspend fun send(line: String) {
        val request = AcpConnection.defaultJson.parseToJsonElement(line).jsonObject
        requests += request
        val id = request["id"] ?: return
        when (request.getValue("method").jsonPrimitive.content) {
            "initialize" -> {
                reply(id, """{"protocolVersion":1,"agentCapabilities":{"loadSession":true}}""")
            }

            "session/new", "session/load" -> {
                if (early) update(initialConfig)
                val configs = if (early) "" else ",\"configOptions\":$initialConfig"
                reply(
                    id,
                    """{"sessionId":"s1","modes":{"currentModeId":"legacy","availableModes":[{"id":"legacy","name":"Legacy"}]}$configs}""",
                )
            }

            "session/set_config_option" -> {
                pending = id
                if (reject) {
                    inbound.send("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32602,"message":"Unavailable"}}""")
                } else if (!hold) {
                    finish()
                }
            }
        }
    }

    suspend fun finish() {
        reply(
            pending!!,
            """{"configOptions":${responseConfig ?: CONFIG.replace(
                "\"currentValue\":\"fast\"",
                "\"currentValue\":\"deep\"",
            )}}""",
        )
    }

    suspend fun update(
        options: String,
        session: String = "s1",
    ) {
        inbound.send(
            """{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"$session","update":{"sessionUpdate":"config_option_update","configOptions":$options}}}""",
        )
    }

    private suspend fun reply(
        id: JsonElement,
        result: String,
    ) {
        inbound.send("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
    }

    override suspend fun close() {
        inbound.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionConfigTest {
    private val dispatcher = StandardTestDispatcher()
    private val fileSystem = mockk<FileSystem>()
    private val files = mutableMapOf<String, String>()
    private val models = mutableListOf<AgentSessionViewModel>()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { fileSystem.exists(any()) } answers
            { files.containsKey(firstArg()) || firstArg<String>().endsWith("agent-history") }
        coEvery { fileSystem.createDirectory(any()) } returns Result.success(Unit)
        coEvery { fileSystem.readText(any(), any()) } answers { Result.success(files.getValue(firstArg())) }
        coEvery { fileSystem.writeText(any(), any(), any()) } answers {
            files[firstArg()] = secondArg()
            Result.success(Unit)
        }
        coEvery { fileSystem.listDirectory(any()) } answers {
            val directory = firstArg<String>()
            Result.success(
                files.keys.filter { it.startsWith("$directory/") }.map { path ->
                    FileEntry(path.substringAfterLast('/'), path, false, true, false, 0, 0, false)
                },
            )
        }
    }

    @AfterTest
    fun cleanup() {
        models.forEach { it.dispose() }
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun advertisedGroupsReplaceLegacyControlsAndConfirmedChangesUseTheConfigMethod() =
        runTest {
            val agent = ConfigAgent()
            val vm = model(agent)
            vm.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            assertEquals(
                listOf("engine", "reasoning"),
                vm.state.value.configOptions!!
                    .map { it.id },
            )
            assertEquals(
                "Available models",
                vm.state.value.configOptions!!
                    .first()
                    .values
                    .first()
                    .group,
            )
            assertNull(vm.state.value.mode)
            assertTrue(
                vm.state.value.availableModels
                    .isEmpty(),
            )
            vm.dispatch(AgentIntent.SetModel("deep"))
            runCurrent()
            val request = agent.requests.last()
            assertEquals("session/set_config_option", request.getValue("method").jsonPrimitive.content)
            assertEquals(
                "engine",
                request
                    .getValue("params")
                    .jsonObject
                    .getValue("configId")
                    .jsonPrimitive.content,
            )
            assertEquals(
                "deep",
                vm.state.value.configOptions!!
                    .first()
                    .currentValue,
            )
            assertNull(vm.state.value.changingConfigId)
            val count = agent.requests.size
            vm.dispatch(AgentIntent.SetConfigOption("engine", "invented"))
            runCurrent()
            assertEquals(count, agent.requests.size)
        }

    @Test
    fun earlyAndLoadedConfigurationIsRetainedAndNotificationsReplaceTheEntireList() =
        runTest {
            val agent = ConfigAgent(early = true)
            val vm = model(agent)
            AgentHistoryStore(
                fileSystem,
                "/project",
            ).save(AgentSessionRecord(id = "saved", agentSessionId = "s1"))
            vm.dispatch(AgentIntent.RestoreSession("saved"))
            runCurrent()
            assertEquals(
                2,
                vm.state.value.configOptions!!
                    .size,
            )
            agent.update("[]", session = "unrelated")
            runCurrent()
            assertEquals(
                2,
                vm.state.value.configOptions!!
                    .size,
            )
            agent.update("[]")
            runCurrent()
            assertTrue(
                vm.state.value.configOptions!!
                    .isEmpty(),
            )
            assertNull(vm.state.value.mode)
        }

    @Test
    fun rejectionAndTimeoutKeepConfirmedValuesAndAllowRetry() =
        runTest {
            val agent = ConfigAgent().also { it.reject = true }
            val vm = model(agent)
            vm.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            vm.dispatch(AgentIntent.SetConfigOption("engine", "deep"))
            runCurrent()
            assertEquals(
                "fast",
                vm.state.value.configOptions!!
                    .first()
                    .currentValue,
            )
            assertNotNull(vm.state.value.configError)
            agent.reject = false
            agent.hold = true
            vm.dispatch(AgentIntent.SetConfigOption("engine", "deep"))
            runCurrent()
            assertEquals("engine", vm.state.value.changingConfigId)
            advanceTimeBy(60_000)
            runCurrent()
            assertNull(vm.state.value.changingConfigId)
            assertNotNull(vm.state.value.configError)
            assertEquals(
                "fast",
                vm.state.value.configOptions!!
                    .first()
                    .currentValue,
            )
            agent.hold = false
            vm.dispatch(AgentIntent.SetConfigOption("engine", "deep"))
            runCurrent()
            assertEquals(
                "deep",
                vm.state.value.configOptions!!
                    .first()
                    .currentValue,
            )
        }

    @Test
    fun lateResponseCannotOverwriteNewerNotificationOrAnotherConversation() =
        runTest {
            val agent = ConfigAgent().also { it.hold = true }
            val vm = model(agent)
            vm.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            vm.dispatch(AgentIntent.SetConfigOption("engine", "deep"))
            runCurrent()
            agent.update("[]")
            runCurrent()
            agent.finish()
            runCurrent()
            assertTrue(
                vm.state.value.configOptions!!
                    .isEmpty(),
            )
            agent.update(CONFIG)
            runCurrent()
            vm.dispatch(AgentIntent.SetConfigOption("engine", "deep"))
            runCurrent()
            vm.dispatch(AgentIntent.NewChat)
            runCurrent()
            agent.finish()
            runCurrent()
            assertNull(vm.state.value.configOptions)
            assertNull(vm.state.value.configError)
            assertFalse(vm.state.value.isStreaming)
        }

    @Test
    fun modeCategorySupportsCyclingAndUnknownCategoriesRemainSelectable() =
        runTest {
            val agent = ConfigAgent()
            val vm = model(agent)
            vm.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            agent.update(CONFIG.replace("\"category\":\"model\"", "\"category\":\"mode\""))
            runCurrent()
            assertEquals(
                listOf("fast", "deep"),
                vm.state.value.availableModes
                    .map { it.id },
            )
            vm.dispatch(AgentIntent.SetMode("deep"))
            runCurrent()
            assertEquals(
                "session/set_config_option",
                agent.requests
                    .last()
                    .getValue("method")
                    .jsonPrimitive.content,
            )
            agent.update(CONFIG.replace("thought_level", "custom_category"))
            runCurrent()
            assertEquals(
                "custom_category",
                vm.state.value.configOptions!![1]
                    .category,
            )
            vm.dispatch(AgentIntent.SetConfigOption("reasoning", "high"))
            runCurrent()
            assertEquals(
                "reasoning",
                agent.requests
                    .last()
                    .getValue("params")
                    .jsonObject
                    .getValue("configId")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun booleansAreNegotiatedAndTypedWithoutOptimisticChanges() =
        runTest {
            val off = """[{"id":"compact","name":"Auto compact","type":"boolean","currentValue":false}]"""
            val on = off.replace("false", "true")
            val agent =
                ConfigAgent(initialConfig = off).also {
                    it.hold = true
                    it.responseConfig = on
                }
            val vm = model(agent)
            vm.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            val capabilities =
                agent.requests
                    .first()
                    .getValue(
                        "params",
                    ).jsonObject
                    .getValue("clientCapabilities")
                    .jsonObject
            assertEquals(
                JsonObject(emptyMap()),
                capabilities
                    .getValue("session")
                    .jsonObject
                    .getValue("configOptions")
                    .jsonObject
                    .getValue("boolean"),
            )
            assertEquals(
                false,
                vm.state.value.configOptions!!
                    .single()
                    .booleanValue,
            )
            val requestCount = agent.requests.size
            vm.dispatch(AgentIntent.SetConfigOption("compact", "true"))
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", false))
            runCurrent()
            assertEquals(requestCount, agent.requests.size)
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", true))
            runCurrent()
            val params =
                agent.requests
                    .last()
                    .getValue("params")
                    .jsonObject
            assertEquals(JsonPrimitive("boolean"), params["type"])
            assertEquals(JsonPrimitive(true), params["value"])
            assertEquals(
                false,
                vm.state.value.configOptions!!
                    .single()
                    .booleanValue,
            )
            assertEquals("compact", vm.state.value.changingConfigId)
            agent.finish()
            runCurrent()
            assertEquals(
                true,
                vm.state.value.configOptions!!
                    .single()
                    .booleanValue,
            )
            agent.responseConfig = off
            agent.hold = false
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", false))
            runCurrent()
            assertEquals(
                JsonPrimitive(false),
                agent.requests
                    .last()
                    .getValue("params")
                    .jsonObject["value"],
            )
            assertEquals(
                false,
                vm.state.value.configOptions!!
                    .single()
                    .booleanValue,
            )
        }

    @Test
    fun booleanRejectionTimeoutAndLateResponseKeepConfirmedState() =
        runTest {
            val off = """[{"id":"compact","name":"Auto compact","type":"boolean","currentValue":false}]"""
            val agent =
                ConfigAgent(initialConfig = off).also {
                    it.reject = true
                    it.responseConfig =
                        off.replace("false", "true")
                }
            val vm = model(agent)
            vm.dispatch(AgentIntent.EnsureConnected)
            runCurrent()
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", true))
            runCurrent()
            assertEquals(
                false,
                vm.state.value.configOptions!!
                    .single()
                    .booleanValue,
            )
            assertNotNull(vm.state.value.configError)
            agent.reject = false
            agent.hold = true
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", true))
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(
                false,
                vm.state.value.configOptions!!
                    .single()
                    .booleanValue,
            )
            assertNull(vm.state.value.changingConfigId)
            assertNotNull(vm.state.value.configError)
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", true))
            runCurrent()
            agent.update("[]")
            runCurrent()
            agent.finish()
            runCurrent()
            assertEquals(emptyList(), vm.state.value.configOptions)
            agent.update(off)
            runCurrent()
            vm.dispatch(AgentIntent.SetBooleanConfigOption("compact", true))
            runCurrent()
            vm.dispatch(AgentIntent.NewChat)
            runCurrent()
            agent.finish()
            runCurrent()
            assertNull(vm.state.value.configOptions)
        }

    @Test
    fun loadedBooleanUpdatesReplaceConfigurationAndRejectWrongTypes() =
        runTest {
            val config = """[
            {"id":"valid","name":"Valid","type":"boolean","currentValue":false},
            {"id":"quoted","name":"Quoted","type":"boolean","currentValue":"true"},
            {"id":"number","name":"Number","type":"boolean","currentValue":1},
            {"id":"missing","name":"Missing","type":"boolean"},
            {"id":"select","name":"Select","type":"select","currentValue":"false",
             "options":[{"name":"True","value":"true"}]}
        ]"""
            val agent = ConfigAgent(initialConfig = config)
            val vm = model(agent)
            AgentHistoryStore(
                fileSystem,
                "/project",
            ).save(AgentSessionRecord(id = "boolean-saved", agentSessionId = "s1"))
            vm.dispatch(AgentIntent.RestoreSession("boolean-saved"))
            runCurrent()
            assertEquals(
                listOf("valid", "select"),
                vm.state.value.configOptions!!
                    .map { it.id },
            )
            val count = agent.requests.size
            vm.dispatch(AgentIntent.SetBooleanConfigOption("select", true))
            runCurrent()
            assertEquals(count, agent.requests.size)
            agent.update(config.replace("currentValue\":false", "currentValue\":true"))
            runCurrent()
            assertEquals(
                true,
                vm.state.value.configOptions!!
                    .first()
                    .booleanValue,
            )
        }

    private fun model(agent: ConfigAgent): AgentSessionViewModel =
        AgentSessionViewModel("/project", fileSystem, transportFactory = { agent }).also { models += it }
}

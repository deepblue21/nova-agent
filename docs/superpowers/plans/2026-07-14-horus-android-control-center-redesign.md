# Horus Android Control Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the NOVA Android client into a task-first Horus control center with a fixed mobile navigation bar, explicit PC Gateway connectivity, clearer task lifecycle, accessible chat/voice controls, and a verified installable APK.

**Architecture:** Keep `MainActivity` as the Android lifecycle and microphone-permission entry point, then move the pure Compose surfaces into focused app, task, chat, voice, and settings files. Add a small authenticated Gateway probe that calls the existing `/v1/models` endpoint, expose its state from `NovaViewModel`, and feed that state into the shell, task empty state, and settings panel without changing Gateway or Mobile Worker contracts.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose BOM 2024.10.01, Material 3, AndroidX ViewModel/DataStore, OkHttp 4.12.0, JUnit 4, Compose UI Test, Gradle 8.9, Android SDK 35, ADB.

## Global Constraints

- Keep `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`, Java/JVM 17, and the existing dependency set; do not add a new library.
- Preserve the existing Gateway, SSE, mobile-task, worker-policy, and token contracts; the only new request is an authenticated `GET /v1/models` connectivity probe.
- Preserve the shipped adaptive launcher icon resources and manifest icon references.
- Default to `Mode.TASKS`; keep exactly three fixed bottom destinations: `Görevler`, `Sohbet`, and `Ses`.
- Remove horizontal scrolling from primary navigation and keep every interactive target at least 48 x 48 dp.
- Never display or log the Gateway token, provider keys, worker token, raw credentials, or secret-bearing failure bodies.
- Keep existing unrelated dirty Gateway, Mobile Worker, and smoke-script changes untouched and out of Android commits.
- Implement test-first: observe a focused RED failure, add the smallest production change, then observe GREEN before each task commit.
- Use only user-facing Turkish copy in the Android UI; wire names and internal enum names remain English.

---

### Task 1: Add an authenticated Gateway connection probe

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/net/GatewayConnectionClient.kt`
- Create: `nova-android/app/src/test/java/com/nova/agent/GatewayConnectionClientTest.kt`

**Interfaces:**
- Consumes: a Gateway base URL such as `http://10.0.2.2:8088/v1` and an optional Gateway token.
- Produces: `GatewayConnectionStatus`, `GatewayConnectionUiState`, `GatewayConnectionResult`, `GatewayConnectionClient.test(baseUrl, token, callback): okhttp3.Call?`, and `GatewayConnectionClient.modelsUrl(baseUrl): HttpUrl?`.

- [ ] **Step 1: Write URL and result-mapping tests**

Create `GatewayConnectionClientTest.kt` with focused tests that require root and `/v1` normalization, reject unsupported paths, verify the bearer header against a loopback `ServerSocket`, and map 200/401/network failures to safe results:

```kotlin
package com.nova.agent

import com.nova.agent.net.GatewayConnectionClient
import com.nova.agent.net.GatewayConnectionResult
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConnectionClientTest {
    @Test fun buildsAuthenticatedModelsUrl() {
        assertEquals(
            "http://127.0.0.1:8088/v1/models",
            GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088/v1/")?.toString(),
        )
        assertEquals(
            "http://127.0.0.1:8088/v1/models",
            GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088")?.toString(),
        )
        assertNull(GatewayConnectionClient.modelsUrl("not a url"))
        assertNull(GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088/private"))
        assertNull(GatewayConnectionClient.modelsUrl("http://user:pass@127.0.0.1:8088/v1"))
        assertNull(GatewayConnectionClient.modelsUrl("http://127.0.0.1:8088/v1?token=secret"))
    }

    @Test fun sendsBearerTokenAndMapsReady() {
        val exchange = exchange(200, "{\"data\":[]}")
        val result = awaitResult(exchange.baseUrl, "secret-token")
        assertEquals(GatewayConnectionResult.Ready, result)
        assertTrue(exchange.request.contains("GET /v1/models HTTP/1.1"))
        assertTrue(exchange.request.contains("Authorization: Bearer secret-token"))
        exchange.close()
    }

    @Test fun mapsUnauthorizedWithoutLeakingResponseBody() {
        val exchange = exchange(401, "{\"error\":\"secret upstream detail\"}")
        assertEquals(GatewayConnectionResult.AuthRequired, awaitResult(exchange.baseUrl, "bad"))
        exchange.close()
    }

    @Test fun mapsNetworkFailureToSafeMessage() {
        val closed = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = closed.localPort
        closed.close()
        assertEquals(
            GatewayConnectionResult.Failure("PC Gateway'e ulaşılamadı"),
            awaitResult("http://127.0.0.1:$port/v1", ""),
        )
    }

    private fun awaitResult(baseUrl: String, token: String): GatewayConnectionResult {
        val latch = CountDownLatch(1)
        var result: GatewayConnectionResult? = null
        GatewayConnectionClient().test(baseUrl, token) { result = it; latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return requireNotNull(result)
    }

    private fun exchange(code: Int, body: String): Exchange {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val requestLatch = CountDownLatch(1)
        val lines = mutableListOf<String>()
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    lines += line
                }
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val reason = if (code == 200) "OK" else "Unauthorized"
                socket.getOutputStream().use { out ->
                    out.write("HTTP/1.1 $code $reason\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes)
                }
            }
            requestLatch.countDown()
        }
        return Exchange(server, "http://127.0.0.1:${server.localPort}/v1", lines, requestLatch)
    }

    private data class Exchange(
        val server: ServerSocket,
        val baseUrl: String,
        val lines: List<String>,
        val requestLatch: CountDownLatch,
    ) {
        val request: String get() { requestLatch.await(5, TimeUnit.SECONDS); return lines.joinToString("\n") }
        fun close() = server.close()
    }
}
```

- [ ] **Step 2: Run the focused test and record RED**

Run:

```powershell
cd C:\Users\salih\Project_Horus\nova-android
.\gradlew.bat :app:testDebugUnitTest --tests com.nova.agent.GatewayConnectionClientTest --console=plain
```

Expected: compilation fails because `GatewayConnectionClient` and its result types do not exist.

- [ ] **Step 3: Implement the minimal safe probe**

Create `GatewayConnectionClient.kt`:

```kotlin
package com.nova.agent.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class GatewayConnectionStatus { UNKNOWN, CHECKING, READY, AUTH_REQUIRED, UNREACHABLE, INVALID_URL }

data class GatewayConnectionUiState(
    val status: GatewayConnectionStatus = GatewayConnectionStatus.UNKNOWN,
    val message: String = "Bağlantı henüz test edilmedi",
)

sealed interface GatewayConnectionResult {
    data object Ready : GatewayConnectionResult
    data object AuthRequired : GatewayConnectionResult
    data object InvalidUrl : GatewayConnectionResult
    data class Failure(val message: String) : GatewayConnectionResult
}

class GatewayConnectionClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    fun test(baseUrl: String, token: String, callback: (GatewayConnectionResult) -> Unit): Call? {
        val url = modelsUrl(baseUrl) ?: run { callback(GatewayConnectionResult.InvalidUrl); return null }
        val builder = Request.Builder().url(url).get()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer ${token.trim()}")
        return client.newCall(builder.build()).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = callback(
                    GatewayConnectionResult.Failure("PC Gateway'e ulaşılamadı"),
                )
                override fun onResponse(call: Call, response: Response) = response.use {
                    callback(
                        when (it.code) {
                            200 -> GatewayConnectionResult.Ready
                            401, 403 -> GatewayConnectionResult.AuthRequired
                            else -> GatewayConnectionResult.Failure("Gateway yanıt vermedi (${it.code})")
                        },
                    )
                }
            })
        }
    }

    companion object {
        fun modelsUrl(baseUrl: String): HttpUrl? {
            val parsed = baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
            if (parsed.scheme !in setOf("http", "https")) return null
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
            if (parsed.query != null || parsed.fragment != null) return null
            val segments = parsed.pathSegments.filter { it.isNotBlank() }
            return when (segments) {
                emptyList<String>() -> parsed.newBuilder().addPathSegments("v1/models").build()
                listOf("v1") -> parsed.newBuilder().addPathSegment("models").build()
                else -> null
            }
        }
    }
}
```

- [ ] **Step 4: Run focused and full unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.nova.agent.GatewayConnectionClientTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected: both commands end in `BUILD SUCCESSFUL`; the focused class reports all tests passing and no response body appears in assertions or output.

- [ ] **Step 5: Commit the connection probe**

```powershell
git add nova-android/app/src/main/java/com/nova/agent/net/GatewayConnectionClient.kt nova-android/app/src/test/java/com/nova/agent/GatewayConnectionClientTest.kt
git commit -m "feat: add Android gateway connection probe"
```

---

### Task 2: Build the fixed task-first application shell

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/ui/app/NovaAppShell.kt`
- Create: `nova-android/app/src/androidTest/java/com/nova/agent/NovaAppShellTest.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: `Mode`, `GatewayConnectionUiState`, navigation callbacks, settings callback, and slot content.
- Produces: `NovaAppShell(...)` and a fixed three-item bottom navigation tagged `primary_navigation`.

- [ ] **Step 1: Write a failing Compose shell test**

Create `NovaAppShellTest.kt`:

```kotlin
package com.nova.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.Mode
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.app.NovaAppShell
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NovaAppShellTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsFixedDestinationsAndRoutesClicks() {
        var selected = Mode.TASKS
        composeRule.setContent {
            NovaTheme {
                NovaAppShell(
                    mode = Mode.TASKS,
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                    onModeChange = { selected = it },
                    onSettings = {},
                    onNewChat = {},
                ) { Box { Text("İçerik") } }
            }
        }
        composeRule.onNodeWithTag("primary_navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Görevler").assertIsDisplayed()
        composeRule.onNodeWithText("Sohbet").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Ses").assertIsDisplayed()
        composeRule.onNodeWithText("PC hazır").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ayarlar").assertIsDisplayed()
        assertEquals(Mode.CHAT, selected)
    }
}
```

- [ ] **Step 2: Run the connected test and record RED**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.NovaAppShellTest --console=plain
```

Expected: test compilation fails because `NovaAppShell` does not exist.

- [ ] **Step 3: Add shell colors and implement the fixed navigation**

Add to `Theme.kt`:

```kotlin
val Amber = Color(0xFFFFC857)
val Success = Color(0xFF53D6A6)
```

Create `NovaAppShell.kt` with this public contract and Material 3 navigation structure:

```kotlin
@Composable
fun NovaAppShell(
    mode: Mode,
    connection: GatewayConnectionUiState,
    onModeChange: (Mode) -> Unit,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Bg,
        topBar = { NovaTopBar(mode, connection, onSettings, onNewChat) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("primary_navigation"),
                containerColor = Bg2,
            ) {
                listOf(
                    Triple(Mode.TASKS, "Görevler", Icons.Filled.PlayArrow),
                    Triple(Mode.CHAT, "Sohbet", Icons.Filled.ChatBubbleOutline),
                    Triple(Mode.VOICE, "Ses", Icons.Filled.Mic),
                ).forEach { (destination, label, icon) ->
                    NavigationBarItem(
                        selected = mode == destination,
                        onClick = { onModeChange(destination) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF04121A),
                            selectedTextColor = TextMain,
                            indicatorColor = Cyan,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}
```

`NovaTopBar` must show the existing NOVA gradient mark, `NOVA`, `connection.message`, a settings button with `contentDescription = "Ayarlar"`, and a `Yeni sohbet` action only when `mode == Mode.CHAT`. Do not use `horizontalScroll` anywhere in this file.

Map status color inside the file without changing the message supplied by the ViewModel:

```kotlin
private fun GatewayConnectionStatus.tint(): Color = when (this) {
    GatewayConnectionStatus.READY -> Success
    GatewayConnectionStatus.CHECKING -> Amber
    GatewayConnectionStatus.AUTH_REQUIRED,
    GatewayConnectionStatus.UNREACHABLE,
    GatewayConnectionStatus.INVALID_URL -> Coral
    GatewayConnectionStatus.UNKNOWN -> Muted
}
```

- [ ] **Step 4: Run the focused shell test**

Run the Step 2 command again.

Expected: `BUILD SUCCESSFUL`; the test finds all three destinations, connection text, and settings action, and the click records `Mode.CHAT`.

- [ ] **Step 5: Commit the shell**

```powershell
git add nova-android/app/src/main/java/com/nova/agent/ui/app/NovaAppShell.kt nova-android/app/src/main/java/com/nova/agent/ui/theme/Theme.kt nova-android/app/src/androidTest/java/com/nova/agent/NovaAppShellTest.kt
git commit -m "feat: add task-first Android app shell"
```

---

### Task 3: Redesign task empty, active, confirmation, and terminal states

**Files:**
- Modify: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskScreen.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskViewModel.kt`
- Modify: `nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt`
- Modify: `nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt`

**Interfaces:**
- Consumes: `MobileTaskUiState`, `GatewayConnectionUiState`, existing prompt/create/command/decision callbacks, settings/retry callbacks.
- Produces: `MobileTaskStatus.userLabel`, `MobileTaskEvent.userLabel`, `MobileTaskMutation.Reset`, `MobileTaskViewModel.newTask()`, quick-prompt callbacks, a task-first empty state, active status card, event timeline, terminal card, and modal confirmation panel.

- [ ] **Step 1: Extend the tests with the required user states**

Add these focused cases before changing production code:

```kotlin
@Test fun showsTaskFirstEmptyStateAndQuickPrompt() {
    var prompt = ""
    composeRule.setContent {
        NovaTheme {
            MobileTaskScreen(
                state = MobileTaskUiState(),
                connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                onPromptChange = { prompt = it },
                onCreateTask = {}, onCommand = {}, onDecision = {}, onNewTask = {},
                onOpenSettings = {}, onRetryConnection = {},
            )
        }
    }
    composeRule.onNodeWithText("Telefonunda ne yapmamı istersin?").assertIsDisplayed()
    composeRule.onNodeWithText("Android sürümünü bul").performClick()
    assertEquals("Android sürümünü bul", prompt)
}

@Test fun blocksSubmissionAndOffersSettingsWhenGatewayIsUnavailable() {
    var opened = false
    composeRule.setContent {
        NovaTheme {
            MobileTaskScreen(
                state = MobileTaskUiState(prompt = "Ayarlar'ı aç"),
                connection = GatewayConnectionUiState(GatewayConnectionStatus.UNREACHABLE, "Bağlantı yok"),
                onPromptChange = {}, onCreateTask = {}, onCommand = {}, onDecision = {}, onNewTask = {},
                onOpenSettings = { opened = true }, onRetryConnection = {},
            )
        }
    }
    composeRule.onNodeWithText("Bağlantıyı ayarla").performClick()
    assertEquals(true, opened)
}

@Test fun showsLocalizedLifecycleAndNewTaskAction() {
    var newTask = false
    val state = MobileTaskUiState(
        task = MobileTask("task-1", "Android sürümünü bul", MobileTaskStatus.COMPLETED),
        events = listOf(MobileTaskEvent("1", "task-1", "worker.completed", "Android 17", MobileTaskStatus.COMPLETED)),
    )
    composeRule.setContent {
        NovaTheme {
            MobileTaskScreen(
                state = state,
                connection = GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır"),
                onPromptChange = {}, onCreateTask = {}, onCommand = {}, onDecision = {},
                onNewTask = { newTask = true }, onOpenSettings = {}, onRetryConnection = {},
            )
        }
    }
    composeRule.onNodeWithText("Tamamlandı").assertIsDisplayed()
    composeRule.onNodeWithText("Android 17").assertIsDisplayed()
    composeRule.onNodeWithText("Yeni görev").performClick()
    assertEquals(true, newTask)
}
```

Add a unit assertion in `MobileTaskReducerTest.kt` that terminal state remains intact while user-facing labels are derived without mutating wire values:

```kotlin
@Test fun exposesTurkishLabelsWithoutChangingWireStatus() {
    assertEquals("Sıraya alındı", MobileTaskStatus.QUEUED.userLabel)
    assertEquals("Eylem uygulanıyor", MobileTaskStatus.EXECUTING.userLabel)
    assertEquals("Tamamlandı", MobileTaskStatus.COMPLETED.userLabel)
    assertEquals("worker.completed", event("1", "worker.completed", "Android 17").type)
}
```

- [ ] **Step 2: Run focused unit and connected tests to verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.nova.agent.MobileTaskReducerTest --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.MobileTaskScreenTest --console=plain
```

Expected: compilation fails on `userLabel`, the new `connection` argument, and the new empty-state callbacks.

- [ ] **Step 3: Add stable Turkish status labels**

Add to `MobileTaskModels.kt`:

```kotlin
val MobileTaskStatus.userLabel: String
    get() = when (this) {
        MobileTaskStatus.QUEUED -> "Sıraya alındı"
        MobileTaskStatus.ROUTING -> "Yönlendiriliyor"
        MobileTaskStatus.OBSERVING -> "Cihaz inceleniyor"
        MobileTaskStatus.PLANNING -> "Plan hazırlanıyor"
        MobileTaskStatus.EXECUTING -> "Eylem uygulanıyor"
        MobileTaskStatus.VERIFYING -> "Sonuç doğrulanıyor"
        MobileTaskStatus.WAITING_FOR_CONFIRMATION -> "Onay bekleniyor"
        MobileTaskStatus.WAITING_FOR_DEVICE -> "Telefon bekleniyor"
        MobileTaskStatus.WAITING_FOR_COMPUTE -> "PC bekleniyor"
        MobileTaskStatus.PAUSED -> "Duraklatıldı"
        MobileTaskStatus.COMPLETED -> "Tamamlandı"
        MobileTaskStatus.FAILED -> "Başarısız"
        MobileTaskStatus.CANCELLED -> "İptal edildi"
    }

val MobileTaskEvent.userLabel: String
    get() = status?.userLabel ?: when (type) {
        "worker.claimed" -> "Görev alındı"
        "worker.executing", "worker.running" -> "Eylem uygulanıyor"
        "worker.observing" -> "Cihaz inceleniyor"
        "worker.completed" -> "Tamamlandı"
        "confirmation.requested" -> "Onay bekleniyor"
        else -> "Görev güncellendi"
    }
```

Add the reset mutation to `MobileTaskReducer.kt` and expose it from `MobileTaskViewModel`:

```kotlin
sealed interface MobileTaskMutation {
    data class PromptChanged(val value: String) : MobileTaskMutation
    data class TaskLoaded(val task: MobileTask) : MobileTaskMutation
    data class EventReceived(val event: MobileTaskEvent) : MobileTaskMutation
    data class Failed(val message: String) : MobileTaskMutation
    data object Loading : MobileTaskMutation
    data object ErrorCleared : MobileTaskMutation
    data object Reset : MobileTaskMutation
}

// Add this branch to reduceMobileTask:
MobileTaskMutation.Reset -> MobileTaskUiState()

fun newTask() {
    disconnect()
    update(MobileTaskMutation.Reset)
}
```

- [ ] **Step 4: Replace the task screen with the task-first layout**

Change the public signature to:

```kotlin
@Composable
fun MobileTaskScreen(
    state: MobileTaskUiState,
    connection: GatewayConnectionUiState,
    onPromptChange: (String) -> Unit,
    onCreateTask: () -> Unit,
    onCommand: (String) -> Unit,
    onDecision: (String) -> Unit,
    onNewTask: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Implement these exact state rules:

```kotlin
val connected = connection.status == GatewayConnectionStatus.READY
if (state.task == null && state.events.isEmpty()) {
    TaskEmptyState(
        prompt = state.prompt,
        connected = connected,
        loading = state.loading,
        onPromptChange = onPromptChange,
        onQuickPrompt = onPromptChange,
        onCreateTask = onCreateTask,
        onOpenSettings = onOpenSettings,
        onRetryConnection = onRetryConnection,
    )
} else {
    ActiveTaskContent(state, onCommand)
}
state.pendingConfirmation?.let { ConfirmationPanel(it, onDecision) }
```

`TaskEmptyState` must include the exact title `Telefonunda ne yapmamı istersin?`, the three exact chips `Android sürümünü bul`, `Ayarlar'ı aç`, and `Bir uygulamayı aç`, a multi-line field tagged `task_prompt`, and a 52 dp start button tagged `task_submit`. When disconnected, replace start with `Bağlantıyı ayarla` for `UNKNOWN`, `AUTH_REQUIRED`, `INVALID_URL`, or `UNREACHABLE`, and show `Tekrar dene` for `UNREACHABLE`.

`ActiveTaskContent` must use `task.status.userLabel`, show pause/resume/cancel only for non-terminal states, render `event.userLabel` plus sanitized `event.summary`, and call `onNewTask` from `Yeni görev` when terminal. Keep confirmation tags `confirmation_panel`, `confirmation_approve`, and `confirmation_reject`; make the panel full-width above navigation with a scrim and two 48 dp buttons.

- [ ] **Step 5: Run task tests and commit**

Run the two Step 2 commands again, then:

```powershell
git add nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskScreen.kt nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskModels.kt nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskReducer.kt nova-android/app/src/main/java/com/nova/agent/feature/tasks/MobileTaskViewModel.kt nova-android/app/src/test/java/com/nova/agent/MobileTaskReducerTest.kt nova-android/app/src/androidTest/java/com/nova/agent/MobileTaskScreenTest.kt
git commit -m "feat: redesign Android mobile task flow"
```

Expected: focused unit and Compose tests pass; commit contains only task UI/model/test files.

---

### Task 4: Add connection testing and model controls to Settings

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/settings/SettingsPanel.kt`
- Create: `nova-android/app/src/androidTest/java/com/nova/agent/SettingsPanelTest.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/NovaViewModel.kt`

**Interfaces:**
- Consumes: `AppSettings`, `GatewayConnectionUiState`, `MODELS`, `EFFORTS`, and callbacks for test/save/model/effort/reasoning/close.
- Produces: `SettingsPanel(...)`; `NovaViewModel.connectionState`; `NovaViewModel.testConnection(baseUrl, token)`; `NovaViewModel.setReasoning(enabled)`; `NovaViewModel.mode` defaulting to `Mode.TASKS`.

- [ ] **Step 1: Write the failing Settings Compose test**

Create `SettingsPanelTest.kt`:

```kotlin
package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nova.agent.data.AppSettings
import com.nova.agent.feature.settings.SettingsPanel
import com.nova.agent.net.GatewayConnectionStatus
import com.nova.agent.net.GatewayConnectionUiState
import com.nova.agent.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPanelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun testsConnectionAndExposesModelControls() {
        var tested: Pair<String, String>? = null
        composeRule.setContent {
            NovaTheme {
                SettingsPanel(
                    settings = AppSettings(baseUrl = "http://10.0.2.2:8088/v1", token = "secret"),
                    connection = GatewayConnectionUiState(GatewayConnectionStatus.UNKNOWN, "Bağlantı henüz test edilmedi"),
                    onTestConnection = { url, token -> tested = url to token },
                    onSaveConnection = { _, _ -> }, onModelChange = {}, onEffortChange = {},
                    onReasoningChange = {}, onClose = {},
                )
            }
        }
        composeRule.onNodeWithText("PC bağlantısı").assertIsDisplayed()
        composeRule.onNodeWithTag("gateway_token").assertIsDisplayed()
        composeRule.onNodeWithText("Bağlantıyı test et").performClick()
        composeRule.onNodeWithText("Model ve çalışma biçimi").assertIsDisplayed()
        assertEquals("http://10.0.2.2:8088/v1" to "secret", tested)
    }
}
```

- [ ] **Step 2: Run the Settings test and record RED**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.SettingsPanelTest --console=plain
```

Expected: compilation fails because `SettingsPanel` does not exist.

- [ ] **Step 3: Implement the ViewModel connection state**

Add to `NovaViewModel`:

```kotlin
private val connectionClient = GatewayConnectionClient()
private var connectionCall: Call? = null
var connectionState by mutableStateOf(GatewayConnectionUiState())
    private set
var mode by mutableStateOf(Mode.TASKS)

fun setReasoning(enabled: Boolean) = persist(settings.copy(reasoning = enabled))

fun saveConnection(baseUrl: String, token: String) {
    val updated = settings.copy(baseUrl = baseUrl.trim(), token = token.trim())
    persist(updated)
    testConnection(updated.baseUrl, updated.token)
}

fun testConnection(baseUrl: String = settings.baseUrl, token: String = settings.token) {
    connectionCall?.cancel()
    connectionState = GatewayConnectionUiState(GatewayConnectionStatus.CHECKING, "Bağlanıyor")
    connectionCall = connectionClient.test(baseUrl, token) { result ->
        onMain {
            connectionState = when (result) {
                GatewayConnectionResult.Ready -> GatewayConnectionUiState(GatewayConnectionStatus.READY, "PC hazır")
                GatewayConnectionResult.AuthRequired -> GatewayConnectionUiState(GatewayConnectionStatus.AUTH_REQUIRED, "Kimlik doğrulama gerekli")
                GatewayConnectionResult.InvalidUrl -> GatewayConnectionUiState(GatewayConnectionStatus.INVALID_URL, "Gateway adresi geçersiz")
                is GatewayConnectionResult.Failure -> GatewayConnectionUiState(GatewayConnectionStatus.UNREACHABLE, result.message)
            }
        }
    }
}
```

Replace the existing settings-load block and extend cleanup exactly as follows:

```kotlin
init {
    speech.initTts()
    viewModelScope.launch {
        settings = store.load()
        if (settings.baseUrl.isNotBlank()) testConnection(settings.baseUrl, settings.token)
    }
}

override fun onCleared() {
    connectionCall?.cancel()
    es?.cancel()
    speech.destroy()
    super.onCleared()
}
```

Do not include token or response bodies in `connectionState.message`.

- [ ] **Step 4: Implement the scrollable Settings panel**

Create `SettingsPanel.kt` with this public signature:

```kotlin
@Composable
fun SettingsPanel(
    settings: AppSettings,
    connection: GatewayConnectionUiState,
    onTestConnection: (String, String) -> Unit,
    onSaveConnection: (String, String) -> Unit,
    onModelChange: (String) -> Unit,
    onEffortChange: (String) -> Unit,
    onReasoningChange: (Boolean) -> Unit,
    onClose: () -> Unit,
)
```

Use a full-height `Surface` with `verticalScroll`, a close button named `Ayarları kapat`, section headings `PC bağlantısı`, `Model ve çalışma biçimi`, and `Uygulama bilgisi`, URL field tag `gateway_url`, password-transformed token field tag `gateway_token`, and two 48 dp actions `Bağlantıyı test et` and `Kaydet`. Render `connection.message` with both icon and text. Reuse `MODELS` in a dropdown, `EFFORTS` as wrapped or vertically safe controls, and a Material `Switch` for reasoning; do not add horizontal scrolling.

Inside `Uygulama bilgisi`, render the build version without hard-coding it:

```kotlin
Text("NOVA ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
Text("Yerel öncelikli Android kontrol merkezi")
```

- [ ] **Step 5: Run Settings and unit regression tests, then commit**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.SettingsPanelTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected: both pass.

```powershell
git add nova-android/app/src/main/java/com/nova/agent/feature/settings/SettingsPanel.kt nova-android/app/src/main/java/com/nova/agent/NovaViewModel.kt nova-android/app/src/androidTest/java/com/nova/agent/SettingsPanelTest.kt
git commit -m "feat: add Android gateway setup flow"
```

---

### Task 5: Split and polish Chat and Voice surfaces

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/chat/ChatScreen.kt`
- Create: `nova-android/app/src/main/java/com/nova/agent/feature/voice/VoiceScreen.kt`
- Create: `nova-android/app/src/androidTest/java/com/nova/agent/AssistantScreensTest.kt`

**Interfaces:**
- Consumes: chat messages/busy callbacks and voice state/level/callbacks from `NovaViewModel`.
- Produces: stateless `ChatScreen(...)` and `VoiceScreen(...)` Compose surfaces with named controls and 48 dp minimum actions.

- [ ] **Step 1: Write accessibility-first Compose tests**

Create `AssistantScreensTest.kt` with two tests:

```kotlin
@Test fun chatComposerHasNamedSendAndStopActions() {
    composeRule.setContent {
        NovaTheme {
            ChatScreen(emptyList(), busy = false, onSend = {}, onStop = {}, onRegenerate = {})
        }
    }
    composeRule.onNodeWithText("Merhaba, ben NOVA").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Mesaj gönder").assertExists()
}

@Test fun voiceControlNameFollowsState() {
    composeRule.setContent {
        NovaTheme {
            VoiceScreen(VoiceState.IDLE, "Konuşmak için mikrofona dokun", 0.08f, onStart = {}, onStop = {})
        }
    }
    composeRule.onNodeWithContentDescription("Dinlemeyi başlat").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the Assistant screen test and record RED**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.AssistantScreensTest --console=plain
```

Expected: compilation fails because both screen functions do not exist.

- [ ] **Step 3: Move Chat into a stateless focused file**

Create `ChatScreen.kt` and move the current message list, message row, clipboard, regenerate, and composer behavior behind this contract:

```kotlin
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    busy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
)
```

Keep streaming auto-scroll and the existing NOVA empty-state mark. Use a local composer draft, `imePadding`, a 52 dp send/stop button, `contentDescription = if (busy) "Yanıtı durdur" else "Mesaj gönder"`, and 48 dp copy/regenerate actions with text labels. Do not expose raw chain-of-thought.

- [ ] **Step 4: Move Voice into a stateless focused file**

Create `VoiceScreen.kt`:

```kotlin
@Composable
fun VoiceScreen(
    state: VoiceState,
    subtitle: String,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
)
```

Preserve `Orb`, size it responsively with `widthIn(max = 280.dp).aspectRatio(1f)`, and set the 76 dp control description to `Dinlemeyi başlat`, `Dinlemeyi durdur`, or `Konuşmayı durdur`. Show `Hazır`, `Dinliyorum`, `Düşünüyorum`, or `Konuşuyorum` as visible text so state is never color-only.

- [ ] **Step 5: Run the test and commit**

Run the Step 2 command again; expect `BUILD SUCCESSFUL`.

```powershell
git add nova-android/app/src/main/java/com/nova/agent/feature/chat/ChatScreen.kt nova-android/app/src/main/java/com/nova/agent/feature/voice/VoiceScreen.kt nova-android/app/src/androidTest/java/com/nova/agent/AssistantScreensTest.kt
git commit -m "feat: polish Android chat and voice screens"
```

---

### Task 6: Wire the focused screens into the Android app

**Files:**
- Create: `nova-android/app/src/main/java/com/nova/agent/ui/app/NovaApp.kt`
- Modify: `nova-android/app/src/main/java/com/nova/agent/MainActivity.kt`
- Modify: `nova-android/app/src/androidTest/java/com/nova/agent/NovaAppShellTest.kt`
- Create: `nova-android/app/src/androidTest/java/com/nova/agent/NovaAppLaunchTest.kt`

**Interfaces:**
- Consumes: `NovaViewModel`, `MobileTaskViewModel`, microphone permission callback, and all screen interfaces from Tasks 2-5.
- Produces: `NovaApp(vm, taskVm, onRequestMic)` as the only screen composition entry from `MainActivity`.

- [ ] **Step 1: Add an integration-oriented launch assertion**

Extend `NovaAppShellTest` so a shell rendered with `mode = Mode.TASKS` displays the task slot and clicking each destination records exactly `Mode.CHAT`, `Mode.VOICE`, and `Mode.TASKS`. Add an assertion that `primary_navigation` has no horizontal-scroll semantics:

```kotlin
composeRule.onNodeWithTag("primary_navigation")
    .assertIsDisplayed()
    .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
```

Create `NovaAppLaunchTest.kt` to prove the real activity uses the new shell:

```kotlin
package com.nova.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NovaAppLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun coldLaunchUsesTaskFirstNonScrollingShell() {
        composeRule.onNodeWithTag("primary_navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Telefonunda ne yapmamı istersin?").assertIsDisplayed()
        composeRule.onNodeWithText("Görevler").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the shell test and record RED for old integration assumptions**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nova.agent.NovaAppLaunchTest --console=plain
```

Expected: the test fails because the real activity still renders the old `Dock` and does not expose `primary_navigation`.

- [ ] **Step 3: Create the app composition root**

Create `NovaApp.kt`:

```kotlin
@Composable
fun NovaApp(
    vm: NovaViewModel,
    taskVm: MobileTaskViewModel,
    onRequestMic: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    NovaAppShell(
        mode = vm.mode,
        connection = vm.connectionState,
        onModeChange = { vm.mode = it },
        onSettings = { showSettings = true },
        onNewChat = vm::newChat,
    ) {
        when (vm.mode) {
            Mode.TASKS -> MobileTaskScreen(
                state = taskVm.state,
                connection = vm.connectionState,
                onPromptChange = taskVm::updatePrompt,
                onCreateTask = taskVm::createTask,
                onCommand = { if (it == "pause") taskVm.pause() else if (it == "resume") taskVm.resume() else taskVm.cancel() },
                onDecision = { if (it == "approve") taskVm.approve() else taskVm.reject() },
                onNewTask = taskVm::newTask,
                onOpenSettings = { showSettings = true },
                onRetryConnection = vm::testConnection,
            )
            Mode.CHAT -> ChatScreen(vm.messages, vm.busy, vm::send, vm::stop, vm::regenerate)
            Mode.VOICE -> VoiceScreen(vm.voiceState, vm.voiceSub, vm.level, onRequestMic, vm::stopListeningOrSpeaking)
        }
    }
    if (showSettings) {
        SettingsPanel(
            settings = vm.settings,
            connection = vm.connectionState,
            onTestConnection = vm::testConnection,
            onSaveConnection = vm::saveConnection,
            onModelChange = vm::setModel,
            onEffortChange = vm::setEffort,
            onReasoningChange = vm::setReasoning,
            onClose = { showSettings = false },
        )
    }
}
```

- [ ] **Step 4: Reduce MainActivity to lifecycle and permission wiring**

Keep the two ViewModels and replace all private Compose UI functions with only the permission launcher and:

```kotlin
setContent {
    NovaTheme {
        NovaApp(vm, taskVm) {
            if (micGranted) vm.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
```

Remove the old `TopBar`, `Dock`, `ModeTab`, `ModelSelector`, `EffortSegmented`, `ReasoningToggle`, `SettingsOverlay`, `ChatView`, `VoiceView`, and duplicate helper composables from `MainActivity.kt`.

- [ ] **Step 5: Run all Android tests and commit the integration**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --console=plain
```

Expected: all unit and instrumentation tests pass.

```powershell
git add nova-android/app/src/main/java/com/nova/agent/MainActivity.kt nova-android/app/src/main/java/com/nova/agent/ui/app/NovaApp.kt nova-android/app/src/androidTest/java/com/nova/agent/NovaAppShellTest.kt nova-android/app/src/androidTest/java/com/nova/agent/NovaAppLaunchTest.kt
git commit -m "refactor: wire focused Android control center screens"
```

---

### Task 7: Verify packaging, install, usability, and PC LLM communication

**Files:**
- Modify: `nova-android/README.md`
- Modify: `README.md`
- Modify: `README.tr.md`
- Create: `.superpowers/sdd/android-control-center-qa.md` (ignored local QA ledger; never stage)

**Interfaces:**
- Consumes: completed Android implementation, existing Gradle wrapper, `emulator-5554`, optional physical Android ADB serial, loopback/Tailscale Gateway URL, and existing Mobile Worker runtime.
- Produces: `app-debug.apk`, installed launcher-resolvable app, screenshot/UI-tree evidence, chat latency evidence, mobile-task evidence, and honest documentation of any physical-device or worker blocker.

- [ ] **Step 1: Run the complete build gate**

Run:

```powershell
cd C:\Users\salih\Project_Horus\nova-android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`; APK exists at `nova-android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Install and run the connected-device gate**

Run:

```powershell
.\gradlew.bat :app:installDebug :app:connectedDebugAndroidTest --console=plain
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell cmd package resolve-activity --brief com.nova.agent
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell am start -S -n com.nova.agent/.MainActivity
```

Expected: install and connected tests succeed; activity resolves to `com.nova.agent/.MainActivity` and launch succeeds. If a physical serial is listed, repeat install, resolve, and launch with that serial. If only `emulator-5554` exists, record physical-device installation as a named external blocker rather than claiming it was tested.

- [ ] **Step 3: Capture and inspect the four accepted UI states**

For `Görevler`, `Sohbet`, `Ses`, and `Ayarlar`, use UI-tree-derived taps, then save `screencap` PNG and `uiautomator dump` XML under `.codex-remote-attachments/horus-control-center-qa/`. Inspect every PNG before acceptance. Reject blank, loading, mid-transition, or cropped frames. Confirm:

```text
primary navigation is fully visible without horizontal scrolling
Görevler is selected after a cold start
PC connection text is visible and not color-only
task quick prompts and composer are visible
chat send/stop and voice start/stop have content descriptions
settings show connection, model, effort, reasoning, and no token text
```

At the same 1080 x 2280 viewport, compare the new captures against the accepted baseline captures in `C:\Users\salih\Documents\Project_Horus\.codex-remote-attachments\horus-audit-current\`. Judge the paired images together and record whether navigation overflow, empty-state ambiguity, top-bar hierarchy, target sizing, and settings density improved; a screenshot alone is not the QA result.

Check text scaling and keyboard reflow, then restore the emulator setting:

```powershell
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell settings put system font_scale 1.3
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell am force-stop com.nova.agent
& 'C:\Users\salih\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell am start -n com.nova.agent/.MainActivity
```

At 1.3x, confirm the top status, all three navigation labels, the task composer, settings actions, and confirmation buttons remain visible without horizontal scrolling. Focus the task and chat text fields, verify the IME does not cover the composer or send action, then restore `font_scale` to `1.0` and relaunch.

- [ ] **Step 4: Verify the PC Gateway and streaming LLM path**

Start only the required local services using the repository's approved runtime procedure, then verify health without printing credentials:

```powershell
wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus -- docker compose up -d --build postgres redis migrate gateway
wsl.exe -d Ubuntu -- curl --fail http://127.0.0.1:8088/health
```

In the app, save the emulator URL `http://10.0.2.2:8088/v1` or the physical device's private Tailscale URL, tap `Bağlantıyı test et`, and require `PC hazır`. Send the fixed prompt `Tek cümleyle bağlantının çalıştığını doğrula.` Capture:

```text
t0 = send tap time
t1 = first non-empty assistant token
t2 = stream completion
TTFT = t1 - t0
total = t2 - t0
route = visible sanitized x-nova-route value
```

Pass condition: a non-empty streamed response completes without crash, token leakage, or duplicate user message. Record metrics in the ignored QA ledger.

- [ ] **Step 5: Verify the controlled Mobile Worker task**

Run the existing safe worker preflight/tests first:

```powershell
cd C:\Users\salih\Project_Horus
node --test scripts/smoke-mobilerun-worker.test.mjs
wsl.exe -d Ubuntu --cd /mnt/c/Users/salih/Project_Horus/mobile-worker -- ./.venv/bin/python -m unittest discover -s tests -v
```

Only when the worker preflight, ADB bridge, and safe credentials are ready, use the quick prompt `Android sürümünü bul` and require the visible lifecycle to reach `Tamamlandı` with a sanitized Android version summary. Record total duration, event count, worker step count, retries, and terminal status. If bridge/worker readiness fails, stop the live task and record the exact non-secret blocker; do not expose ADB publicly, start a second ADB server, or fall back to unapproved USB/network paths.

- [ ] **Step 6: Update documentation with verified facts only**

Update the three README files with:

```text
- task-first fixed Android navigation delivered
- adaptive launcher icon retained and launcher-resolved
- Gateway connection test behavior
- exact automated build/test results
- emulator and, only if actually completed, physical-device install result
- measured TTFT/total chat latency and worker task result, or the exact remaining blocker
```

Do not copy tokens, IP addresses that are meant to stay private, raw model output, or secret environment values into documentation.

- [ ] **Step 7: Commit docs and prepare the APK handoff**

```powershell
git add nova-android/README.md README.md README.tr.md
git commit -m "docs: record Android control center verification"
```

Verify final scope:

```powershell
git status --short
git log --oneline -8
Get-FileHash nova-android/app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
```

Expected: only the user's pre-existing unrelated dirty files remain; the APK hash is reported for the exact build installed and tested.

---

## Final Verification Checklist

- [ ] `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lintDebug`, and `:app:assembleDebug` all pass from a clean Android diff.
- [ ] Launcher icon remains present and `com.nova.agent/.MainActivity` resolves.
- [ ] Cold launch selects `Görevler` and the bottom navigation never scrolls horizontally.
- [ ] Gateway probe distinguishes ready, invalid URL, auth required, and unreachable states without leaking secrets.
- [ ] Task empty, active, confirmation, terminal, chat, voice, and settings states have accepted screenshots and UI trees.
- [ ] Streaming PC LLM response is measured; worker task is completed or its external blocker is evidenced.
- [ ] APK path and SHA-256 are reported; physical-phone validation is claimed only if a non-emulator ADB serial was actually installed and exercised.

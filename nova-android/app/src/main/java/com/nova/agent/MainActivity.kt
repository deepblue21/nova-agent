package com.nova.agent

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nova.agent.feature.tasks.MobileTaskViewModel
import com.nova.agent.ui.app.NovaApp
import com.nova.agent.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    private val vm: NovaViewModel by viewModels()
    private val taskVm: MobileTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 36'dan itibaren kenardan kenara çizim ZORUNLU; devre dışı
        // bırakma seçeneği (windowOptOutEdgeToEdgeEnforcement) kaldırıldı ve
        // themes.xml'deki statusBarColor/navigationBarColor yok sayılıyor.
        // Bunu açıkça çağırmak sistem çubuğu ikonlarının kontrastını bizim
        // belirlememizi sağlar: NOVA'nın yüzeyi koyu, dolayısıyla ikonlar açık.
        // Inset'ler ekran tarafında karşılanıyor (NovaAppShell'de
        // statusBarsPadding, Material3 NavigationBar'ın kendi navigationBars
        // inset'i, sohbet girişinde imePadding).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            NovaTheme(themeId = vm.settings.themeId) {
                var micGranted by remember {
                    mutableStateOf(
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }
                val micLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    micGranted = granted
                    if (granted) vm.startListening()
                }
                NovaApp(vm, taskVm) {
                    if (micGranted) {
                        vm.startListening()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }
}

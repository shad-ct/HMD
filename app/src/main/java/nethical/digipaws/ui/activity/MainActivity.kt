package nethical.digipaws.ui.activity

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nethical.digipaws.Constants
import nethical.digipaws.R
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.databinding.ActivityMainBinding
import nethical.digipaws.databinding.DialogPermissionInfoBinding
import nethical.digipaws.databinding.DialogRemoveAntiUninstallBinding
import nethical.digipaws.receivers.AdminReceiver
import nethical.digipaws.services.AppBlockerService
import nethical.digipaws.services.GeneralFeaturesService
import nethical.digipaws.services.KeywordBlockerService
import nethical.digipaws.services.UsageTrackingService
import nethical.digipaws.services.ViewBlockerService
import nethical.digipaws.ui.dialogs.StartFocusMode
import nethical.digipaws.ui.dialogs.TweakAppBlockerWarning
import nethical.digipaws.ui.dialogs.TweakKeywordBlocker
import nethical.digipaws.ui.dialogs.TweakKeywordPack
import nethical.digipaws.ui.dialogs.TweakUsageTracker
import nethical.digipaws.ui.dialogs.TweakViewBlockerCheatHours
import nethical.digipaws.ui.dialogs.TweakViewBlockerWarning
import nethical.digipaws.ui.fragments.anti_uninstall.ChooseModeFragment
import nethical.digipaws.ui.fragments.installation.AccessibilityGuide
import nethical.digipaws.ui.fragments.installation.WelcomeFragment
import nethical.digipaws.ui.fragments.usage.AllAppsUsageFragment
import nethical.digipaws.utils.SavedPreferencesLoader
import java.io.File
import java.util.Calendar


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var selectPinnedAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectBlockedAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectFocusModeUnblockedAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectOverlayAppsLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectBlockedKeywords: ActivityResultLauncher<Intent>

    private lateinit var addCheatHoursActivity: ActivityResultLauncher<Intent>

    private lateinit var addAutoFocusHoursActivity: ActivityResultLauncher<Intent>

    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private lateinit var options: ActivityOptionsCompat
    private var isDeviceAdminOn = false
    private var isAntiUninstallOn = false

    private var isUpdatingModuleToggles = false
    private var isAppBlockerServiceEnabled = false
    private var isViewBlockerServiceEnabled = false
    private var isKeywordBlockerServiceEnabled = false
    private var isUsageTrackerServiceEnabled = false

    private var isGeneralSettingsOn = false
    private var isDisplayOverOtherAppsOn = false

    private var appBlockerExpanded = false
    private var focusModeExpanded = false
    private var viewBlockerExpanded = false
    private var keywordBlockerExpanded = false
    private var usageTrackerExpanded = false
    private var antiUninstallExpanded = false

    private var lastAppBlockerEnabled = false
    private var lastViewBlockerEnabled = false
    private var lastKeywordBlockerEnabled = false
    private var lastUsageTrackerReady = false
    private var lastAntiUninstallEnabled = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted, show notifications
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()

//                makeStartFocusModeDialog()
            } else {
                // Permission denied
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()

            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
        setupActivityLaunchers()
        setupClickListeners()

        if (!isFirstLaunchComplete()) {
            val intent = Intent(this, FragmentActivity::class.java)
            intent.putExtra("fragment", WelcomeFragment.FRAGMENT_ID)
            startActivity(intent, options.toBundle())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun setupActivityLaunchers() {

        selectPinnedAppsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                selectedApps?.let {
                    savedPreferencesLoader.savePinned(it.toSet())
                }
            }
        }

        selectBlockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        savedPreferencesLoader.saveBlockedApps(it.toSet())
                        sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                    }
                }
            }


        selectFocusModeUnblockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        savedPreferencesLoader.saveFocusModeSelectedApps(selectedApps)
                    }
                }
            }

        selectOverlayAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    selectedApps?.let {
                        savedPreferencesLoader.setOverlayApps(it.toSet())
                    }
                }
            }

        selectBlockedKeywords =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val blockedKeywords = result.data?.getStringArrayListExtra("SELECTED_KEYWORDS")
                    blockedKeywords?.let {
                        savedPreferencesLoader.saveBlockedKeywords(it.toSet())
                        sendRefreshRequest(KeywordBlockerService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
                    }
                }
            }

        addCheatHoursActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_APP_BLOCKER)
            }

        addAutoFocusHoursActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            }
    }

    private fun setupClickListeners() {
        // click listeners for configuration options
        binding.selectPinnedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.loadPinnedApps())
            )

            selectPinnedAppsLauncher.launch(intent, options)

        }
        binding.selectBlockedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.loadBlockedApps())
            )
            selectBlockedAppsLauncher.launch(intent, options)
        }
        binding.selectBlockedKeywords.setOnClickListener {
            val intent = Intent(this, ManageKeywordsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SAVED_KEYWORDS",
                ArrayList(savedPreferencesLoader.loadBlockedKeywords())
            )
            selectBlockedKeywords.launch(intent, options)
        }


        binding.appBlockerSelectCheatHours.setOnClickListener {
            val intent = Intent(this, TimedActionActivity::class.java)
            intent.putExtra("selected_mode", TimedActionActivity.MODE_APP_BLOCKER_CHEAT_HOURS)
            addCheatHoursActivity.launch(intent, options)
        }
        binding.btnConfigAppblockerWarning.setOnClickListener {
            TweakAppBlockerWarning(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_app_blocker_warning"
            )
        }
        binding.btnConfigViewblockerWarning.setOnClickListener {
            TweakViewBlockerWarning(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_view_blocker_warning"
            )
        }
        binding.btnConfigViewblockerCheatHours.setOnClickListener {
            TweakViewBlockerCheatHours(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_view_blocker_cheat_hours"
            )
        }
        binding.btnConfigTracker.setOnClickListener{
            TweakUsageTracker(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_usage_tracker"
            )
        }
        binding.btnUnlockAntiUninstall.setOnClickListener {
            makeRemoveAntiUninstallDialog()
        }
        binding.btnManagePreinstalledKeywords.setOnClickListener {
            TweakKeywordPack().show(supportFragmentManager, "tweak_keyword_pack")
        }
        binding.btnManageKeywordBlocker.setOnClickListener {
            TweakKeywordBlocker(savedPreferencesLoader).show(
                supportFragmentManager,
                "tweak_keyword_blocker"
            )
        }
        binding.selectAppUsageStats.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java)
            intent.putExtra("fragment", AllAppsUsageFragment.FRAGMENT_ID)
            startActivity(intent, options.toBundle())
        }

        binding.selectReelUsageStats.setOnClickListener {
            val intent = Intent(this, UsageMetricsActivity::class.java)
            startActivity(intent, options.toBundle())
        }
        binding.btnSelectAppsToShowOverlay.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.getOverlayApps())
            )
            selectOverlayAppsLauncher.launch(intent, options)
        }
        binding.selectFocusBlockedApps.setOnClickListener {
            val intent = Intent(this, SelectAppsActivity::class.java)
            intent.putStringArrayListExtra(
                "PRE_SELECTED_APPS",
                ArrayList(savedPreferencesLoader.getFocusModeSelectedApps())
            )
            selectFocusModeUnblockedAppsLauncher.launch(intent, options)
        }
        binding.autoFocus.setOnClickListener {
            val intent = Intent(this, TimedActionActivity::class.java)
            intent.putExtra("selected_mode", TimedActionActivity.MODE_AUTO_FOCUS)
            addAutoFocusHoursActivity.launch(intent, options)
        }


        binding.startFocusMode.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS,options)
                    return@setOnClickListener
                }
            }


            createFocusModeShortcut()

            StartFocusMode(savedPreferencesLoader, onPositiveButtonPressed = {
                binding.selectFocusBlockedApps.isEnabled = false
                binding.startFocusMode.isEnabled = false

            }).show(
                supportFragmentManager,
                "start_focus_mode"
            )

        }

        // module toggles
        binding.appBlockerStatusChip.setOnCheckedChangeListener { toggle, _ ->
            if (isUpdatingModuleToggles) return@setOnCheckedChangeListener
            makeAccessibilityInfoDialog("App Blocker", AppBlockerService::class.java)
            resetToggle(toggle, isAppBlockerServiceEnabled)
        }

        binding.viewBlockerStatusChip.setOnCheckedChangeListener { toggle, _ ->
            if (isUpdatingModuleToggles) return@setOnCheckedChangeListener
            makeAccessibilityInfoDialog("View Blocker", ViewBlockerService::class.java)
            resetToggle(toggle, isViewBlockerServiceEnabled)
        }

        binding.keywordBlockerStatusChip.setOnCheckedChangeListener { toggle, _ ->
            if (isUpdatingModuleToggles) return@setOnCheckedChangeListener
            makeAccessibilityInfoDialog("Keyword Blocker", KeywordBlockerService::class.java)
            resetToggle(toggle, isKeywordBlockerServiceEnabled)
        }

        binding.usageTrackerStatusChip.setOnCheckedChangeListener { toggle, _ ->
            if (isUpdatingModuleToggles) return@setOnCheckedChangeListener
            if (!isDisplayOverOtherAppsOn) {
                makeDrawOverOtherAppsDialog()
            } else {
                makeAccessibilityInfoDialog("Usage Tracker", UsageTrackingService::class.java)
            }
            resetToggle(toggle, isUsageTrackerServiceEnabled && isDisplayOverOtherAppsOn)
        }

        binding.antiUninstallCardChip.setOnCheckedChangeListener { toggle, isChecked ->
            if (isUpdatingModuleToggles) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!isDeviceAdminOn) {
                    makeDeviceAdminPermissionDialog()
                } else if (!isGeneralSettingsOn) {
                    makeAccessibilityInfoDialog("General Features", GeneralFeaturesService::class.java)
                } else if (!isAntiUninstallOn) {
                    val intent = Intent(this, FragmentActivity::class.java)
                    intent.putExtra("fragment", ChooseModeFragment.FRAGMENT_ID)
                    startActivity(intent, options.toBundle())
                }
            } else {
                if (isAntiUninstallOn) {
                    makeRemoveAntiUninstallDialog()
                }
            }
            resetToggle(toggle, isAntiUninstallOn)
        }

        binding.focusModeStatusChip.setOnCheckedChangeListener { toggle, isChecked ->
            if (isUpdatingModuleToggles) return@setOnCheckedChangeListener

            if (!isAppBlockerServiceEnabled) {
                makeAccessibilityInfoDialog("App Blocker", AppBlockerService::class.java)
                resetToggle(toggle, false)
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                StartFocusMode(savedPreferencesLoader, onPositiveButtonPressed = {
                    isUpdatingModuleToggles = true
                    binding.focusModeStatusChip.isChecked = true
                    isUpdatingModuleToggles = false
                    binding.selectFocusBlockedApps.isEnabled = false
                    binding.startFocusMode.isEnabled = false
                }).show(supportFragmentManager, "start_focus_mode_toggle")
            } else {
                val previous = savedPreferencesLoader.getFocusModeData()
                savedPreferencesLoader.saveFocusModeData(
                    FocusModeBlocker.FocusModeData(
                        isTurnedOn = false,
                        endTime = -1,
                        modeType = previous.modeType,
                        selectedApps = previous.selectedApps
                    )
                )
                sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            }
        }

        binding.helpReelBlocker.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_view_blocker))
                .setMessage(getString(R.string.this_option_has_the_ability_to_block_youtube_shorts_and_instagram_reels_while_allowing_access_to_other_app_features))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        binding.helpAppBlocker.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_app_blocker))
                .setMessage(getString(R.string.about_app_blocker_desc))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        binding.helpFocusMode.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_focus_mode))
                .setMessage(getString(R.string.about_focus_mode_desc))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        binding.helpKeywordBlocker.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_keyword_blocker))
                .setMessage(getString(R.string.about_keyword_blocker_desc))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        binding.helpUsageTracker.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_usage_tracker))
                .setMessage(getString(R.string.about_usage_tracker_desc))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        binding.helpAntiUninstall.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_anti_uninstall))
                .setMessage(getString(R.string.about_anti_uninstall_desc))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        // Expand/collapse module action sections
        binding.appBlockerHeader.setOnClickListener {
            if (isAppBlockerServiceEnabled) {
                appBlockerExpanded = !appBlockerExpanded
                binding.appBlockerActions.visibility = if (appBlockerExpanded) View.VISIBLE else View.GONE
            } else {
                makeAccessibilityInfoDialog("App Blocker", AppBlockerService::class.java)
            }
        }

        binding.focusModeHeader.setOnClickListener {
            if (isAppBlockerServiceEnabled) {
                focusModeExpanded = !focusModeExpanded
                binding.focusModeActions.visibility = if (focusModeExpanded) View.VISIBLE else View.GONE
            } else {
                makeAccessibilityInfoDialog("App Blocker", AppBlockerService::class.java)
            }
        }

        binding.viewBlockerHeader.setOnClickListener {
            if (isViewBlockerServiceEnabled) {
                viewBlockerExpanded = !viewBlockerExpanded
                binding.viewBlockerActions.visibility = if (viewBlockerExpanded) View.VISIBLE else View.GONE
            } else {
                makeAccessibilityInfoDialog("View Blocker", ViewBlockerService::class.java)
            }
        }

        binding.keywordBlockerHeader.setOnClickListener {
            if (isKeywordBlockerServiceEnabled) {
                keywordBlockerExpanded = !keywordBlockerExpanded
                binding.keywordBlockerActions.visibility = if (keywordBlockerExpanded) View.VISIBLE else View.GONE
            } else {
                makeAccessibilityInfoDialog("Keyword Blocker", KeywordBlockerService::class.java)
            }
        }

        binding.usageTrackerHeader.setOnClickListener {
            if (!isDisplayOverOtherAppsOn) {
                makeDrawOverOtherAppsDialog()
                return@setOnClickListener
            }

            if (isUsageTrackerServiceEnabled) {
                usageTrackerExpanded = !usageTrackerExpanded
                binding.usageTrackerActions.visibility = if (usageTrackerExpanded) View.VISIBLE else View.GONE
            } else {
                makeAccessibilityInfoDialog("Usage Tracker", UsageTrackingService::class.java)
            }
        }

        binding.antiUninstallHeader.setOnClickListener {
            if (isAntiUninstallOn) {
                antiUninstallExpanded = !antiUninstallExpanded
                binding.antiUninstallActions.visibility = if (antiUninstallExpanded) View.VISIBLE else View.GONE
            } else if (!isDeviceAdminOn) {
                makeDeviceAdminPermissionDialog()
            } else if (!isGeneralSettingsOn) {
                makeAccessibilityInfoDialog("General Features", GeneralFeaturesService::class.java)
            } else {
                val intent = Intent(this, FragmentActivity::class.java)
                intent.putExtra("fragment", ChooseModeFragment.FRAGMENT_ID)
                startActivity(intent, options.toBundle())
            }
        }
    }

    private fun checkPermissions() {

        isDisplayOverOtherAppsOn = Settings.canDrawOverlays(this)
        lifecycleScope.launch {
            isAppBlockerServiceEnabled =
                withContext(Dispatchers.IO) { isAccessibilityServiceEnabled(AppBlockerService::class.java) }
            isViewBlockerServiceEnabled =
                withContext(Dispatchers.IO) { isAccessibilityServiceEnabled(ViewBlockerService::class.java) }
            isKeywordBlockerServiceEnabled =
                withContext(Dispatchers.IO) { isAccessibilityServiceEnabled(KeywordBlockerService::class.java) }
            isUsageTrackerServiceEnabled =
                withContext(Dispatchers.IO) { isAccessibilityServiceEnabled(UsageTrackingService::class.java) }
            isGeneralSettingsOn =
                withContext(Dispatchers.IO) { isAccessibilityServiceEnabled(GeneralFeaturesService::class.java) }

            val devicePolicyManager =
                getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(applicationContext, AdminReceiver::class.java)

            // Check if Device Admin is active
            isDeviceAdminOn = devicePolicyManager.isAdminActive(componentName)

            val antiUninstallInfo = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            isAntiUninstallOn = antiUninstallInfo.getBoolean("is_anti_uninstall_on", false)
            val doesAntiUninstallBlockView =
                antiUninstallInfo.getBoolean("is_configuring_blocked", false)

            withContext(Dispatchers.Main) {
                val usageTrackerReady = isUsageTrackerServiceEnabled && isDisplayOverOtherAppsOn

                if (isAppBlockerServiceEnabled && !lastAppBlockerEnabled) {
                    appBlockerExpanded = true
                    focusModeExpanded = true
                }
                if (isViewBlockerServiceEnabled && !lastViewBlockerEnabled) viewBlockerExpanded = true
                if (isKeywordBlockerServiceEnabled && !lastKeywordBlockerEnabled) keywordBlockerExpanded = true
                if (usageTrackerReady && !lastUsageTrackerReady) usageTrackerExpanded = true
                if (isAntiUninstallOn && !lastAntiUninstallEnabled) antiUninstallExpanded = true

                if (!isAppBlockerServiceEnabled) {
                    appBlockerExpanded = false
                    focusModeExpanded = false
                }
                if (!isViewBlockerServiceEnabled) viewBlockerExpanded = false
                if (!isKeywordBlockerServiceEnabled) keywordBlockerExpanded = false
                if (!usageTrackerReady) usageTrackerExpanded = false
                if (!isAntiUninstallOn) antiUninstallExpanded = false

                // App Blocker
                updateModuleToggle(isAppBlockerServiceEnabled, binding.appBlockerStatusChip, binding.appBlockerWarning)
                binding.appBlockerActions.visibility = if (isAppBlockerServiceEnabled && appBlockerExpanded) View.VISIBLE else View.GONE
                binding.apply {
                    selectBlockedApps.isEnabled = isAppBlockerServiceEnabled
                    btnConfigAppblockerWarning.isEnabled = isAppBlockerServiceEnabled
                    appBlockerSelectCheatHours.isEnabled = isAppBlockerServiceEnabled
                }

                // View Blocker
                updateModuleToggle(isViewBlockerServiceEnabled, binding.viewBlockerStatusChip, binding.viewBlockerWarning)
                binding.viewBlockerActions.visibility = if (isViewBlockerServiceEnabled && viewBlockerExpanded) View.VISIBLE else View.GONE
                binding.apply {
                    btnConfigViewblockerCheatHours.isEnabled = isViewBlockerServiceEnabled
                    btnConfigViewblockerWarning.isEnabled = isViewBlockerServiceEnabled
                }

                // Keyword Blocker
                updateModuleToggle(isKeywordBlockerServiceEnabled, binding.keywordBlockerStatusChip, binding.keywordBlockerWarning)
                binding.keywordBlockerActions.visibility = if (isKeywordBlockerServiceEnabled && keywordBlockerExpanded) View.VISIBLE else View.GONE
                binding.apply {
                    selectBlockedKeywords.isEnabled = isKeywordBlockerServiceEnabled
                    btnManagePreinstalledKeywords.isEnabled = isKeywordBlockerServiceEnabled
                    btnManageKeywordBlocker.isEnabled = isKeywordBlockerServiceEnabled
                }

                // Usage Tracker
                updateModuleToggle(
                    usageTrackerReady,
                    binding.usageTrackerStatusChip,
                    binding.usageTrackerWarning
                )
                binding.usageTrackerActions.visibility =
                    if (usageTrackerReady && usageTrackerExpanded) View.VISIBLE else View.GONE
                if (usageTrackerReady) {
                    binding.apply {
                        selectReelUsageStats.isEnabled = true
                        btnSelectAppsToShowOverlay.isEnabled = true
                        btnConfigTracker.isEnabled = true
                    }
                } else {
                    binding.apply {
                        selectReelUsageStats.isEnabled = false
                        btnSelectAppsToShowOverlay.isEnabled = false
                        btnConfigTracker.isEnabled = false
                    }
                }


                // General Settings
                val isFocusedModeOn = if (isAppBlockerServiceEnabled) savedPreferencesLoader.getFocusModeData().isTurnedOn else false
                updateModuleToggle(isFocusedModeOn, binding.focusModeStatusChip, binding.focusModeWarning)
                binding.focusModeActions.visibility = if (isAppBlockerServiceEnabled && focusModeExpanded) View.VISIBLE else View.GONE
                binding.apply {
                    startFocusMode.isEnabled = isAppBlockerServiceEnabled
                    selectFocusBlockedApps.isEnabled = isAppBlockerServiceEnabled
                    autoFocus.isEnabled = isAppBlockerServiceEnabled
                }

                // Anti-Uninstall settings
                binding.btnUnlockAntiUninstall.isEnabled = isAntiUninstallOn
                binding.antiUninstallActions.visibility = if (isAntiUninstallOn && antiUninstallExpanded) View.VISIBLE else View.GONE

                // Handle anti-uninstall UI changes
                isUpdatingModuleToggles = true
                binding.antiUninstallCardChip.isChecked = isAntiUninstallOn
                isUpdatingModuleToggles = false
                binding.antiUninstallWarning.visibility = View.GONE

                if (doesAntiUninstallBlockView && isAntiUninstallOn) {
                    binding.apply {
                        btnConfigAppblockerWarning.isEnabled = false
                        btnManagePreinstalledKeywords.isEnabled = false
                        btnManageKeywordBlocker.isEnabled = false
                        btnConfigViewblockerCheatHours.isEnabled = false
                        selectBlockedKeywords.isEnabled = false
                        selectBlockedApps.isEnabled = false
                        appBlockerSelectCheatHours.isEnabled = false
                        btnConfigViewblockerWarning.isEnabled = false
                        startFocusMode.isEnabled = false
                    }
                }
                if (isAppBlockerServiceEnabled) {
                    val isFocusedModeCurrentlyOn = savedPreferencesLoader.getFocusModeData().isTurnedOn
                    binding.selectFocusBlockedApps.isEnabled = !isFocusedModeCurrentlyOn
                    binding.startFocusMode.isEnabled = !isFocusedModeCurrentlyOn
                }

                lastAppBlockerEnabled = isAppBlockerServiceEnabled
                lastViewBlockerEnabled = isViewBlockerServiceEnabled
                lastKeywordBlockerEnabled = isKeywordBlockerServiceEnabled
                lastUsageTrackerReady = usageTrackerReady
                lastAntiUninstallEnabled = isAntiUninstallOn

            }
        }
    }
    private fun isFirstLaunchComplete(): Boolean {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("isFirstLaunchComplete", false)
    }

    private fun updateModuleToggle(isEnabled: Boolean, toggle: CompoundButton, warningText: TextView) {
        isUpdatingModuleToggles = true
        toggle.isChecked = isEnabled
        isUpdatingModuleToggles = false
        warningText.visibility = View.GONE
    }

    private fun resetToggle(toggle: CompoundButton, checked: Boolean) {
        isUpdatingModuleToggles = true
        toggle.isChecked = checked
        isUpdatingModuleToggles = false
    }
    private fun sendRefreshRequest(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }
    private fun isAccessibilityServiceEnabled(serviceClass: Class<out AccessibilityService>): Boolean {
        val serviceName = ComponentName(this, serviceClass).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val isAccessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        return isAccessibilityEnabled == 1 && enabledServices.contains(serviceName)
    }

    private fun makeDeviceAdminPermissionDialog() {
        val dialogDeviceAdmin =
            DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogDeviceAdmin.title.text = getString(R.string.enable_2, "Device Admin")
        dialogDeviceAdmin.desc.text = getString(R.string.device_admin_perm)
        dialogDeviceAdmin.point1.text =
            getString(R.string.prevent_uninstallation_attempts_until_a_set_condition_is_met)
        dialogDeviceAdmin.point2.visibility = View.GONE
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogDeviceAdmin.root)
            .show()

        dialogDeviceAdmin.btnReject.setOnClickListener {
            dialog.dismiss()
        }
        dialogDeviceAdmin.btnAccept.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            val componentName = ComponentName(this, AdminReceiver::class.java)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable admin to enable anti uninstall."
            )
            startActivity(intent, options.toBundle())

        }
    }

    private fun makeDrawOverOtherAppsDialog() {
        val dialogDisplayOverOtherApps =
            DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogDisplayOverOtherApps.title.text =
            getString(R.string.enable_2, "Display Over Other Apps")
        dialogDisplayOverOtherApps.desc.text = getString(R.string.device_perm_draw_over_other_apps)
        dialogDisplayOverOtherApps.point1.text = getString(R.string.show_time_elapsed_on_phone)
        dialogDisplayOverOtherApps.point2.text =
            getString(R.string.calculate_how_many_reels_tiktok_short_videos_you_scroll_per_day)
        dialogDisplayOverOtherApps.point4.text = getString(R.string.plan_a_robbery)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogDisplayOverOtherApps.root)
            .show()

        dialogDisplayOverOtherApps.btnReject.setOnClickListener {
            dialog.dismiss()
        }
        dialogDisplayOverOtherApps.btnAccept.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(
                this,
                getString(R.string.find_digipaws_and_press_enable), Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent, options.toBundle())

        }
    }

    private fun makeAccessibilityInfoDialog(title: String, cls: Class<*>) {
        val dialogAccessibilityServiceInfoBinding =
            DialogPermissionInfoBinding.inflate(layoutInflater)
        dialogAccessibilityServiceInfoBinding.title.text = getString(R.string.enable_2, title)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogAccessibilityServiceInfoBinding.root)
            .show()

        dialogAccessibilityServiceInfoBinding.btnReject.setOnClickListener {
            dialog.dismiss()
        }
        dialogAccessibilityServiceInfoBinding.btnAccept.setOnClickListener {
            Toast.makeText(this, "Find '$title' and press enable", Toast.LENGTH_LONG).show()
            openAccessibilityServiceScreen(cls)
            dialog.dismiss()
        }
        dialogAccessibilityServiceInfoBinding.btnGuide.visibility = View.VISIBLE
        dialogAccessibilityServiceInfoBinding.btnGuide.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java)
            intent.putExtra("fragment", AccessibilityGuide.FRAGMENT_ID)
            startActivity(intent, options.toBundle())
        }
    }


    private fun createFocusModeShortcut() {

        val sp = getSharedPreferences("shortcuts",Context.MODE_PRIVATE)
        if(sp.getBoolean("focus_mode",false)){
            return
        }
        val intent = Intent(this, ShortcutActivity::class.java).apply {
            action = Intent.ACTION_CREATE_SHORTCUT
        }
        val shortcutInfo = ShortcutInfoCompat.Builder(this, "digipaws_focus_mode")
            .setShortLabel(getString(R.string.focus_mode))
            .setLongLabel(getString(R.string.focus_mode))
            .setIntent(intent)
            .setIcon(IconCompat.createWithResource(this, R.drawable.focus_mode_icon))
            .build()


        val supported = ShortcutManagerCompat.isRequestPinShortcutSupported(this)
        val dynamicShortcuts = ShortcutManagerCompat.getDynamicShortcuts(this)

        if(supported){
            if(dynamicShortcuts.contains(shortcutInfo)){
                return
            }
        }
        MaterialAlertDialogBuilder(this).apply {
            setTitle("Add Focus Mode to Home Screen")
            setMessage("Would you like to add Focus Mode to your home screen for quick access?")
            setPositiveButton("Ok") { dialog, _ ->
                sp.edit().putBoolean("focus_mode",true).apply()
                val pinnedShortcutCallbackIntent = Intent("example.intent.action.SHORTCUT_CREATED")

                val successCallback = PendingIntent.getBroadcast(
                    this@MainActivity,
                    1000,
                    pinnedShortcutCallbackIntent,
                    FLAG_IMMUTABLE
                )

                ShortcutManagerCompat.requestPinShortcut(
                    this@MainActivity,
                    shortcutInfo,
                    successCallback.intentSender
                )

            }
            setNegativeButton("Cancel", { _,_ ->
                sp.edit().putBoolean("focus_mode",false).apply()
            })
            show()
        }

    }

    private fun openAccessibilityServiceScreen(cls: Class<*>) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(this, cls)
            intent.putExtra(":settings:fragment_args_key", componentName.flattenToString())
            val bundle = Bundle()
            bundle.putString(":settings:fragment_args_key", componentName.flattenToString())
            intent.putExtra(":settings:show_fragment_args", bundle)
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general Accessibility Settings
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun makeRemoveAntiUninstallDialog() {
        val antiUninstallInfo = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val mode = antiUninstallInfo.getInt("mode", -1)
        when (mode) {

            Constants.ANTI_UNINSTALL_TIMED_MODE -> {
                val dateString = antiUninstallInfo.getString("date", null)
                val parts: List<String> = dateString!!.split("/")
                val selectedDate = Calendar.getInstance()
                selectedDate.set(
                    Integer.parseInt(parts[2]),  // Year
                    Integer.parseInt(parts[0]) - 1,  // Month (0-based)
                    Integer.parseInt(parts[1])  // Day
                )


                val today = Calendar.getInstance()

                val daysDiff =
                    (selectedDate.timeInMillis - today.timeInMillis) / (1000 * 60 * 60 * 24)
                if (selectedDate.before(today) || daysDiff.toInt() == 0) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.anti_uninstall_removed),
                        Snackbar.LENGTH_SHORT
                    )
                        .show()
                    antiUninstallInfo.edit().putBoolean("is_anti_uninstall_on", false).commit()
                    sendRefreshRequest(GeneralFeaturesService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)

                } else {

                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.failed))
                        .setMessage(getString(R.string.remaining_time_anti_uninstall, daysDiff))
                        .setPositiveButton("Ok", null)
                        .show()
                }

            }

            Constants.ANTI_UNINSTALL_PASSWORD_MODE -> {
                val dialogRemoveAntiUninstall =
                    DialogRemoveAntiUninstallBinding.inflate(layoutInflater)
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.remove_anti_uninstall))
                    .setView(dialogRemoveAntiUninstall.root)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        if (antiUninstallInfo.getString(
                                "password",
                                "pass"
                            ) == dialogRemoveAntiUninstall.password.text.toString()
                        ) {
                            antiUninstallInfo.edit().putBoolean("is_anti_uninstall_on", false)
                                .commit()
                            sendRefreshRequest(GeneralFeaturesService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)

                            Snackbar.make(
                                binding.root,
                                "Anti Uninstall removed",
                                Snackbar.LENGTH_SHORT
                            )
                                .show()

                            checkPermissions()
                        } else {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.incorrect_password_please_try_again),
                                Snackbar.LENGTH_SHORT
                            )
                                .setAction(getString(R.string.retry)) {
                                    makeRemoveAntiUninstallDialog()
                                }
                                .show()
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }

    }
    data class WarningData(
        val message: String = "",
        val timeInterval: Int = 120000, // default cooldown period
        val isDynamicIntervalSettingAllowed: Boolean = false,
        val isProceedDisabled: Boolean = false,
        val isWarningDialogHidden:Boolean = false // perform back/home action directly without showing warning screen
    )


}
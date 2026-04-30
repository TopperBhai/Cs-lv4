package com.tungsten.fcl.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.tungsten.fcl.R
import com.tungsten.fcl.activity.SplashActivity
import com.tungsten.fcl.databinding.FragmentRuntimeBinding
import com.tungsten.fcl.util.RuntimeUtils
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fcllibrary.component.FCLFragment
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog
import com.tungsten.fcllibrary.component.view.FCLImageView
import com.tungsten.fcllibrary.component.view.FCLProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeFragment : FCLFragment(), View.OnClickListener {

    // =========================================================
    // View Binding
    // =========================================================
    private lateinit var bind: FragmentRuntimeBinding

    // =========================================================
    // Runtime State
    // =========================================================
    private data class RuntimeState(
        var lwjgl:   Boolean = false,
        var cacio:   Boolean = false,
        var cacio17: Boolean = false,
        var java8:   Boolean = false,
        var java17:  Boolean = false,
        var java21:  Boolean = false,
        var java25:  Boolean = false,
        var jna:     Boolean = false
    ) {
        val isAllInstalled: Boolean
            get() = lwjgl && cacio && cacio17
                 && java8  && java17 && java21
                 && java25 && jna
    }

    private var state = RuntimeState()

    // =========================================================
    // Runtime Config
    // =========================================================
    private data class RuntimeConfig(
        val rowId:        Int,
        val displayName:  String,
        val isInstalled:  () -> Boolean,
        val setInstalled: (Boolean) -> Unit,
        val installer:    suspend () -> Unit
    )

    private val runtimeConfigs: List<RuntimeConfig> by lazy {
        listOf(
            RuntimeConfig(
                rowId       = R.id.row_lwjgl,
                displayName = "LWJGL 3",
                isInstalled  = { state.lwjgl },
                setInstalled = { state.lwjgl = it },
                installer    = {
                    RuntimeUtils.install(
                        requireContext(),
                        FCLPath.LWJGL_DIR,
                        "app_runtime/lwjgl"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_cacio,
                displayName = "Caciocavallo",
                isInstalled  = { state.cacio },
                setInstalled = { state.cacio = it },
                installer    = {
                    RuntimeUtils.install(
                        requireContext(),
                        FCLPath.CACIOCAVALLO_8_DIR,
                        "app_runtime/caciocavallo"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_cacio17,
                displayName = "Caciocavallo 17",
                isInstalled  = { state.cacio17 },
                setInstalled = { state.cacio17 = it },
                installer    = {
                    RuntimeUtils.install(
                        requireContext(),
                        FCLPath.CACIOCAVALLO_17_DIR,
                        "app_runtime/caciocavallo17"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_java8,
                displayName = "Java 8",
                isInstalled  = { state.java8 },
                setInstalled = { state.java8 = it },
                installer    = {
                    RuntimeUtils.installJava(
                        requireContext(),
                        FCLPath.JAVA_8_PATH,
                        "app_runtime/java/jre8"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_java17,
                displayName = "Java 17",
                isInstalled  = { state.java17 },
                setInstalled = { state.java17 = it },
                installer    = {
                    RuntimeUtils.installJava(
                        requireContext(),
                        FCLPath.JAVA_17_PATH,
                        "app_runtime/java/jre17"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_java21,
                displayName = "Java 21",
                isInstalled  = { state.java21 },
                setInstalled = { state.java21 = it },
                installer    = {
                    RuntimeUtils.installJava(
                        requireContext(),
                        FCLPath.JAVA_21_PATH,
                        "app_runtime/java/jre21"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_java25,
                displayName = "Java 25",
                isInstalled  = { state.java25 },
                setInstalled = { state.java25 = it },
                installer    = {
                    RuntimeUtils.installJava(
                        requireContext(),
                        FCLPath.JAVA_25_PATH,
                        "app_runtime/java/jre25"
                    )
                }
            ),
            RuntimeConfig(
                rowId       = R.id.row_jna,
                displayName = "JNA",
                isInstalled  = { state.jna },
                setInstalled = { state.jna = it },
                installer    = {
                    RuntimeUtils.installJna(
                        requireContext(),
                        FCLPath.JNA_PATH,
                        "app_runtime/jna"
                    )
                }
            )
        )
    }

    // =========================================================
    // State Flags
    // =========================================================
    private var isInstalling  = false
    private val installJobs   = mutableMapOf<Int, Job>()

    // =========================================================
    // Colors - Ek jagah define, baar baar parse nahi
    // =========================================================
    private object Colors {
        val GREEN   = Color.parseColor("#66BB6A")
        val AMBER   = Color.parseColor("#FFA726")
        val RED     = Color.parseColor("#EF5350")
        val GRAY    = Color.parseColor("#78909C")
    }

    // =========================================================
    // Lifecycle
    // =========================================================
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(
            R.layout.fragment_runtime,
            container,
            false
        )
        bind = FragmentRuntimeBinding.bind(view)

        initClickListeners()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                loadStateFromActivity()
            }
            refreshAllRows()
            checkAndProceed()
        }

        return view
    }

    override fun onDestroyView() {
        // Sab pending jobs cancel karo
        installJobs.values.forEach { it.cancel() }
        installJobs.clear()
        super.onDestroyView()
    }

    // =========================================================
    // Init
    // =========================================================

    private fun initClickListeners() {
        bind.install.setOnClickListener(this)
    }

    /**
     * SplashActivity se runtime states load karo.
     */
    private fun loadStateFromActivity() {
        val splash = activity as? SplashActivity ?: return
        state = RuntimeState(
            lwjgl   = splash.lwjgl,
            cacio   = splash.cacio,
            cacio17 = splash.cacio17,
            java8   = splash.java8,
            java17  = splash.java17,
            java21  = splash.java21,
            java25  = splash.java25,
            jna     = splash.jna
        )
    }

    // =========================================================
    // Row UI Helpers
    // =========================================================

    private fun getRow(rowId: Int): View? =
        bind.root.findViewById(rowId)

    /**
     * Loading spinner show/hide karo.
     */
    private fun setRowLoading(row: View, isLoading: Boolean) {
        row.findViewById<FCLProgressBar>(R.id.runtime_progress)
            ?.visibility = if (isLoading) View.VISIBLE else View.GONE

        row.findViewById<FCLImageView>(R.id.runtime_state)
            ?.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    /**
     * Row icon + dot update karo install status se.
     */
    private fun updateRowIcon(row: View, isInstalled: Boolean) {
        val ctx = context ?: return

        val drawableRes = if (isInstalled) {
            R.drawable.ic_baseline_done_24
        } else {
            R.drawable.ic_baseline_update_24
        }

        val color = if (isInstalled) Colors.GREEN else Colors.AMBER

        AppCompatResources
            .getDrawable(ctx, drawableRes)
            ?.mutate()
            ?.apply { setTint(color) }
            ?.also { drawable ->
                row.findViewById<FCLImageView>(R.id.runtime_state)
                    ?.setImageDrawable(drawable)
            }

        // Status dot bhi update karo
        row.findViewById<View>(R.id.status_dot)
            ?.background
            ?.setTint(color)
    }

    /**
     * Error state - Red icon + Red dot.
     */
    private fun setRowError(row: View) {
        val ctx = context ?: return

        AppCompatResources
            .getDrawable(ctx, R.drawable.ic_baseline_update_24)
            ?.mutate()
            ?.apply { setTint(Colors.RED) }
            ?.also { drawable ->
                row.findViewById<FCLImageView>(R.id.runtime_state)
                    ?.setImageDrawable(drawable)
            }

        row.findViewById<View>(R.id.status_dot)
            ?.background
            ?.setTint(Colors.RED)
    }

    /**
     * Row label text set karo.
     * ✅ FIX: displayName ab RuntimeConfig mein hai
     * getRuntimeName() call ki zaroorat nahi
     */
    private fun setRowLabel(row: View, name: String) {
        row.findViewById<TextView>(R.id.runtime_label)
            ?.text = name
    }

    // =========================================================
    // Refresh All Rows
    // =========================================================

    /**
     * Sabhi rows refresh karo - naam + icon + dot.
     */
    private fun refreshAllRows() {
        if (context == null) return

        runtimeConfigs.forEach { config ->
            getRow(config.rowId)?.let { row ->

                // ✅ FIX 1: Naam set karo (displayName direct config mein)
                setRowLabel(row, config.displayName)

                // ✅ FIX 2: Icon + dot color set karo
                updateRowIcon(row, config.isInstalled())
            }
        }
    }

    // =========================================================
    // Check & Proceed
    // =========================================================

    private fun checkAndProceed() {
        if (state.isAllInstalled) {
            (activity as? SplashActivity)?.enterLauncher()
        }
    }

    // =========================================================
    // Install Logic
    // =========================================================

    private fun install() {
        if (isInstalling) return
        isInstalling = true

        val pendingConfigs = runtimeConfigs.filter { !it.isInstalled() }

        if (pendingConfigs.isEmpty()) {
            isInstalling = false
            checkAndProceed()
            return
        }

        pendingConfigs.forEach { config ->
            installSingleRuntime(config)
        }
    }

    private fun installSingleRuntime(config: RuntimeConfig) {
        val row = getRow(config.rowId) ?: return

        // Pehle se chal raha hai to skip karo
        if (installJobs[config.rowId]?.isActive == true) return

        val job = lifecycleScope.launch {

            // Loading show karo
            setRowLoading(row, true)

            // Background mein install karo
            val error = withContext(Dispatchers.IO) {
                runCatching { config.installer() }
                    .exceptionOrNull()
            }

            // View abhi bhi attached hai?
            if (!isAdded) return@launch

            // UI update karo
            setRowLoading(row, false)

            if (error == null) {
                config.setInstalled(true)
                updateRowIcon(row, true)
            } else {
                setRowError(row)
                showErrorDialog(config, error.message ?: "Unknown error")
            }

            checkAndProceed()
        }

        installJobs[config.rowId] = job
    }

    // =========================================================
    // Error Dialog
    // =========================================================

    private fun showErrorDialog(config: RuntimeConfig, message: String) {
        isInstalling = false

        lifecycleScope.launch(Dispatchers.Main) {
            if (!isAdded) return@launch

            FCLAlertDialog.Builder(requireContext())
                .setTitle("${config.displayName} Install Failed")
                .setMessage(message)
                .setPositiveButton("Retry") {
                    isInstalling = false
                    installSingleRuntime(config)
                }
                .setNegativeButton("Cancel") {
                    isInstalling = false
                }
                .create()
                .show()
        }
    }

    // =========================================================
    // Click Listener
    // =========================================================

    override fun onClick(view: View) {
        when (view) {
            bind.install -> install()
        }
    }
}

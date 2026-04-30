package com.tungsten.fcl.ui.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.tungsten.fcl.databinding.FragmentSkinBinding

class CSSkinFragment : Fragment() {

    // =========================================================
    // View Binding
    // =========================================================
    private var _binding: FragmentSkinBinding? = null
    private val binding get() = _binding!!

    // =========================================================
    // Fields
    // =========================================================
    private lateinit var skinManager: CSSkinManager
    private lateinit var username:    String

    // =========================================================
    // Image Picker
    // =========================================================
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        skinManager.uploadSkinFromFile(username, uri)
    }

    // =========================================================
    // Lifecycle
    // =========================================================
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSkinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get username
        username = requireContext()
            .getSharedPreferences("cs_account", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""

        if (username.isBlank()) {
            showMessage("Please login first!", isError = true)
            return
        }

        // Init skin manager
        skinManager = CSSkinManager(
            context   = requireContext(),
            scope     = viewLifecycleOwner.lifecycleScope,
            onSuccess = { msg -> showMessage(msg, isError = false) },
            onError   = { msg -> showMessage(msg, isError = true) },
            onLoading = { loading -> setLoading(loading) }
        )

        // Load current skin preview
        loadCurrentSkin()

        // Setup buttons
        setupButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // =========================================================
    // Setup
    // =========================================================

    private fun setupButtons() {
        binding.apply {

            // ✅ Update by URL
            btnUpdateUrl.setOnClickListener {
                val url = skinUrlInput.text.toString().trim()
                if (url.isBlank()) {
                    showMessage("URL daalo pehle!", isError = true)
                    return@setOnClickListener
                }
                skinManager.updateSkinByUrl(username, url)
            }

            // ✅ Upload from phone
            btnUploadFile.setOnClickListener {
                imagePicker.launch("image/png")
            }

            // ✅ Copy from MC player
            btnCopySkin.setOnClickListener {
                val mcUser = mcUsernameInput.text.toString().trim()
                if (mcUser.isBlank()) {
                    showMessage("Minecraft username daalo!", isError = true)
                    return@setOnClickListener
                }
                skinManager.copySkinFromPlayer(username, mcUser)
            }

            // ✅ Default Steve
            btnDefaultSteve.setOnClickListener {
                skinManager.resetToDefault(username, useAlex = false)
            }

            // ✅ Default Alex
            btnDefaultAlex.setOnClickListener {
                skinManager.resetToDefault(username, useAlex = true)
            }
        }
    }

    // =========================================================
    // Skin Preview
    // =========================================================

    private fun loadCurrentSkin() {
        skinManager.getCurrentSkin(username) { url ->
            if (isAdded && _binding != null) {
                Glide.with(this)
                    .load(url)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(binding.skinPreview)
            }
        }
    }

    // =========================================================
    // UI Helpers
    // =========================================================

    private fun showMessage(msg: String, isError: Boolean) {
        binding.skinMessage.apply {
            text      = msg
            setTextColor(
                if (isError) Color.parseColor("#EF5350")
                else         Color.parseColor("#66BB6A")
            )
            isVisible = true
        }

        // Skin preview refresh karo agar success tha
        if (!isError) {
            loadCurrentSkin()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.apply {
            btnUpdateUrl.isEnabled   = !isLoading
            btnUploadFile.isEnabled  = !isLoading
            btnCopySkin.isEnabled    = !isLoading
            btnDefaultSteve.isEnabled = !isLoading
            btnDefaultAlex.isEnabled  = !isLoading

            loadingIndicator.isVisible = isLoading
        }
    }
}
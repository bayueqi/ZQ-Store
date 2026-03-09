package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ActivityIconViewerBinding

class IconViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIconViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityIconViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val iconUrl = intent.getStringExtra(EXTRA_ICON_URL) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""

        if (iconUrl.isEmpty()) {
            finish()
            return
        }

        setupToolbar(appName)
        loadIcon(iconUrl)
    }

    private fun setupToolbar(appName: String) {
        binding.toolbar.title = appName
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadIcon(iconUrl: String) {
        Glide.with(this)
            .load(iconUrl)
            .placeholder(R.drawable.ic_app_placeholder)
            .error(R.drawable.ic_app_placeholder)
            .into(binding.ivIcon)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    companion object {
        private const val EXTRA_ICON_URL = "icon_url"
        private const val EXTRA_APP_NAME = "app_name"

        fun newIntent(context: Context, iconUrl: String, appName: String): Intent {
            return Intent(context, IconViewerActivity::class.java).apply {
                putExtra(EXTRA_ICON_URL, iconUrl)
                putExtra(EXTRA_APP_NAME, appName)
            }
        }
    }
}

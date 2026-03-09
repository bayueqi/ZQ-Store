package com.samyak.repostore.ui.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.samyak.repostore.R
import com.samyak.repostore.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private lateinit var binding: FragmentAboutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    private fun setupViews() {
        val versionName = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireActivity().packageManager.getPackageInfo(
                    requireActivity().packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                requireActivity().packageManager.getPackageInfo(
                    requireActivity().packageName,
                    0
                )
            }
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
        binding.tvAppVersion.text = getString(R.string.version_format, versionName)

        // 作者信息
        binding.tvAuthorName.text = "作者: bayueqi"
        binding.btnAuthorGithub.setOnClickListener {
            openUrl("https://github.com/bayueqi")
        }

        // 仓库信息
        binding.tvRepoName.text = "仓库: ZQ-Store"
        binding.btnRepoGithub.setOnClickListener {
            openUrl("https://github.com/bayueqi/ZQ-Store")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    companion object {
        @JvmStatic
        fun newInstance() = AboutFragment()
    }
}

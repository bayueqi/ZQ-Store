package com\.bayueqi\.zqstore.ui.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com\.bayueqi\.zqstore.R
import com\.bayueqi\.zqstore.data.api.RetrofitClient
import com\.bayueqi\.zqstore.data.auth.GitHubAuth

import com\.bayueqi\.zqstore.databinding.FragmentSettingsBinding



import com\.bayueqi\.zqstore.ui.activity.GitHubSignInActivity

import com\.bayueqi\.zqstore.ui.activity.FavoriteActivity
import com\.bayueqi\.zqstore.ui.activity.MyAppsActivity
import com\.bayueqi\.zqstore.ui.activity.DownloadSettingsActivity


class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAccountSection()
        setupMyAppsSection()
        setupManageAppsSection()
        setupDownloadSettingsSection()
    }

    override fun onResume() {
        super.onResume()
        updateAccountStatus()
        // Refresh API client to pick up new auth
        RetrofitClient.refreshAuth()
    }

    private fun setupAccountSection() {
        updateAccountStatus()

        binding.accountCard.setOnClickListener {
            startActivity(Intent(requireContext(), GitHubSignInActivity::class.java))
        }
    }

    private fun updateAccountStatus() {
        val user = GitHubAuth.getUser(requireContext())
        val isSignedIn = GitHubAuth.isSignedIn(requireContext())

        if (isSignedIn && user != null) {
            // Show user info
            binding.tvAccountName.text = user.login
            binding.tvAccountStatus.text = getString(R.string.rate_limit_increased)
            
            // Load avatar
            if (!user.avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(user.avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_account)
                    .into(binding.ivAccountAvatar)
            }
        } else {
            // Show sign in prompt
            binding.tvAccountName.text = getString(R.string.github_sign_in)
            binding.tvAccountStatus.text = getString(R.string.sign_in_to_increase_limit)
            binding.ivAccountAvatar.setImageResource(R.drawable.ic_account)
        }
    }



    private fun setupMyAppsSection() {
        binding.myAppsCard.setOnClickListener {
            startActivity(Intent(requireContext(), FavoriteActivity::class.java))
        }
    }

    private fun setupManageAppsSection() {
        binding.manageAppsCard.setOnClickListener {
            startActivity(Intent(requireContext(), MyAppsActivity::class.java))
        }
    }

    private fun setupDownloadSettingsSection() {
        binding.downloadSettingsCard.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadSettingsActivity::class.java))
        }
    }





    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // TODO: Replace with your actual GitHub repository URL
        private const val SOURCE_CODE_URL = "https://github.com/samyak2403/RepoStore"

        fun newInstance() = SettingsFragment()
    }
}


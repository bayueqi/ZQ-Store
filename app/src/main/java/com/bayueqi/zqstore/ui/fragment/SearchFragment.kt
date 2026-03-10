package com\.bayueqi\.zqstore.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com\.bayueqi\.zqstore.ui.widget.ShimmerFrameLayout
import com\.bayueqi\.zqstore.R
import com\.bayueqi\.zqstore.RepoStoreApp
import com\.bayueqi\.zqstore.data.model.AppItem
import com\.bayueqi\.zqstore.data.model.SearchFilters
import com\.bayueqi\.zqstore.data.model.SortOption
import com\.bayueqi\.zqstore.data.model.UpdatedWithin
import com\.bayueqi\.zqstore.data.model.Platform
import com\.bayueqi\.zqstore.databinding.FragmentSearchBinding
import com\.bayueqi\.zqstore.ui.activity.DetailActivity
import com\.bayueqi\.zqstore.ui.activity.DeveloperActivity
import com\.bayueqi\.zqstore.ui.adapter.RankedAppAdapter
import com\.bayueqi\.zqstore.ui.viewmodel.SearchUiState
import com\.bayueqi\.zqstore.ui.viewmodel.SearchViewModel
import com\.bayueqi\.zqstore.ui.viewmodel.SearchViewModelFactory
import com\.bayueqi\.zqstore.util.RateLimitDialog
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as RepoStoreApp).repository)
    }

    private lateinit var appAdapter: RankedAppAdapter
    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize shimmer layout
        shimmerLayout = view.findViewById(R.id.skeleton_layout)
        
        setupSearchBar()
        setupRecyclerView()
        setupFilterChips()
        observeViewModel()

        // Focus search field
        binding.etSearch.post {
            binding.etSearch.requestFocus()

            val controller = WindowCompat.getInsetsController(
                requireActivity().window,
                binding.etSearch
            )

            controller.show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun setupSearchBar() {
        binding.btnBack.setOnClickListener {
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                activity?.finish()
            }
        }

        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString() ?: ""
            viewModel.search(query)
            binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.etSearch.text?.toString() ?: "")
                true
            } else false
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearSearch()
        }

        binding.btnFilter.setOnClickListener {
            viewModel.toggleShowFilters()
        }
    }

    private fun setupRecyclerView() {
        appAdapter = RankedAppAdapter(
            onItemClick = { appItem ->
                navigateToDetail(appItem)
            },
            onDeveloperClick = { developer, avatarUrl ->
                val intent = DeveloperActivity.newIntent(requireContext(), developer, avatarUrl)
                startActivity(intent)
            }
        )

        binding.rvSearchResults.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(requireContext())
            
            // Pagination - load more when scrolling near the end
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    if (dy > 0) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 5) {
                            viewModel.loadMore()
                        }
                    }
                }
            })
        }
    }

    private fun setupFilterChips() {
        // Sort chip
        binding.chipSort.setOnClickListener {
            showSortDialog()
        }

        // Language chip
        binding.chipLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // Stars chip
        binding.chipStars.setOnClickListener {
            showStarsDialog()
        }

        // Updated chip
        binding.chipUpdated.setOnClickListener {
            showUpdatedDialog()
        }

        // Platforms chip
        binding.chipHasApk.text = "平台"
        binding.chipHasApk.setOnClickListener {
            showPlatformsDialog()
        }

        // Reset chip
        binding.chipReset.setOnClickListener {
            viewModel.resetFilters()
            updateChipStates(SearchFilters.DEFAULT)
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.best_match),
            getString(R.string.most_stars),
            getString(R.string.most_forks),
            getString(R.string.recently_updated_sort)
        )
        val sortOptions = arrayOf(
            SortOption.BEST_MATCH,
            SortOption.STARS,
            SortOption.FORKS,
            SortOption.UPDATED
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setItems(options) { _, which ->
                viewModel.updateSortOption(sortOptions[which])
                binding.chipSort.text = options[which]
                binding.chipSort.isChecked = which != 0
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = listOf(getString(R.string.any_language)) + SearchFilters.POPULAR_LANGUAGES
        val options = languages.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language)
            .setItems(options) { _, which ->
                val selectedLanguage = if (which == 0) null else languages[which]
                viewModel.updateLanguageFilter(selectedLanguage)
                binding.chipLanguage.text = if (which == 0) getString(R.string.language) else languages[which]
                binding.chipLanguage.isChecked = which != 0
            }
            .show()
    }

    private fun showStarsDialog() {
        val starOptions = SearchFilters.MIN_STAR_OPTIONS
        val options = starOptions.map { stars ->
            if (stars == 0) getString(R.string.any_stars) else getString(R.string.min_stars_format, stars)
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stars)
            .setItems(options) { _, which ->
                val selectedStars = if (which == 0) null else starOptions[which]
                viewModel.updateMinStars(selectedStars)
                binding.chipStars.text = options[which]
                binding.chipStars.isChecked = which != 0
            }
            .show()
    }

    private fun showUpdatedDialog() {
        val timeOptions = UpdatedWithin.entries
        val options = arrayOf(
            getString(R.string.any_time),
            getString(R.string.last_week),
            getString(R.string.last_month),
            getString(R.string.last_3_months),
            getString(R.string.last_year)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.updated)
            .setItems(options) { _, which ->
                val selectedUpdated = if (which == 0) null else timeOptions[which]
                viewModel.updateUpdatedWithin(selectedUpdated)
                binding.chipUpdated.text = if (which == 0) getString(R.string.updated) else options[which]
                binding.chipUpdated.isChecked = which != 0
            }
            .show()
    }

    private fun showPlatformsDialog() {
        val platforms = Platform.entries
        val platformNames = platforms.map { it.displayName }.toTypedArray()
        val selectedPlatforms = viewModel.filters.value.platforms
        val checkedItems = BooleanArray(platforms.size) {
            selectedPlatforms.contains(platforms[it])
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("平台")
            .setMultiChoiceItems(platformNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val selected = mutableListOf<Platform>()
                for (i in platforms.indices) {
                    if (checkedItems[i]) {
                        selected.add(platforms[i])
                    }
                }
                viewModel.updatePlatformsFilter(selected)
                updatePlatformsChip(selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePlatformsChip(platforms: List<Platform>) {
        binding.chipHasApk.text = if (platforms.isEmpty()) {
            "平台"
        } else if (platforms.size == 1) {
            platforms[0].displayName
        } else {
            "${platforms.size} 个平�?
        }
        binding.chipHasApk.isChecked = platforms.isNotEmpty()
        binding.chipHasApk.setChipIconResource(R.drawable.ic_platform)
        binding.chipHasApk.setCheckedIconVisible(false)
        binding.chipHasApk.setChipIconVisible(true)
    }

    private fun updateChipStates(filters: SearchFilters) {
        binding.chipSort.isChecked = filters.sortBy != SortOption.BEST_MATCH
        binding.chipSort.text = if (filters.sortBy != SortOption.BEST_MATCH) {
            filters.sortBy.displayName
        } else {
            getString(R.string.sort_by)
        }

        binding.chipLanguage.isChecked = filters.language != null
        binding.chipLanguage.text = filters.language ?: getString(R.string.language)

        binding.chipStars.isChecked = filters.minStars != null && filters.minStars > 0
        binding.chipStars.text = if (filters.minStars != null && filters.minStars > 0) {
            getString(R.string.min_stars_format, filters.minStars)
        } else {
            getString(R.string.stars)
        }

        binding.chipUpdated.isChecked = filters.updatedWithin != null
        binding.chipUpdated.text = if (filters.updatedWithin != null) {
            filters.updatedWithin.displayName
        } else {
            getString(R.string.updated)
        }

        updatePlatformsChip(filters.platforms)

        binding.chipReset.isVisible = viewModel.hasActiveFilters()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }
                
                launch {
                    viewModel.showFilters.collect { show ->
                        binding.filterChipsContainer.isVisible = show
                    }
                }
                
                launch {
                    viewModel.totalResults.collect { count ->
                        if (count > 0) {
                            binding.tvResultsCount.text = getString(R.string.results_count, count)
                            binding.tvResultsCount.isVisible = true
                        } else {
                            binding.tvResultsCount.isVisible = false
                        }
                    }
                }
                
                launch {
                    viewModel.filters.collect { filters ->
                        updateChipStates(filters)
                    }
                }
            }
        }
    }

    private fun handleUiState(state: SearchUiState) {
        when (state) {
            is SearchUiState.Idle -> {
                hideSkeleton()
                binding.rvSearchResults.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvMessage.text = getString(R.string.search_hint)
            }
            is SearchUiState.Loading -> {
                showSkeleton()
                binding.rvSearchResults.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            }
            is SearchUiState.Empty -> {
                hideSkeleton()
                binding.rvSearchResults.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvMessage.text = getString(R.string.no_results)
            }
            is SearchUiState.Success -> {
                hideSkeleton()
                binding.rvSearchResults.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
                appAdapter.submitList(state.apps)
            }
            is SearchUiState.Error -> {
                hideSkeleton()
                binding.rvSearchResults.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvMessage.text = state.message
                
                // Show rate limit dialog if applicable
                RateLimitDialog.showIfNeeded(requireContext(), state.message)
            }
        }
    }
    
    private fun showSkeleton() {
        shimmerLayout?.apply {
            visibility = View.VISIBLE
            startShimmer()
        }
    }
    
    private fun hideSkeleton() {
        shimmerLayout?.apply {
            stopShimmer()
            visibility = View.GONE
        }
    }

    private fun navigateToDetail(appItem: AppItem) {
        val intent = DetailActivity.newIntent(
            requireContext(),
            appItem.repo.owner.login,
            appItem.repo.name
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shimmerLayout = null
        _binding = null
    }

    companion object {
        fun newInstance() = SearchFragment()
    }
}


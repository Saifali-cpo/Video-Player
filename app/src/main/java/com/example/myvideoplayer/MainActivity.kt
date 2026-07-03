package com.example.myvideoplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myvideoplayer.databinding.ActivityMainBinding
import com.example.myvideoplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: VideoAdapter

    private var allVideos: List<VideoItem> = emptyList()
    private var currentQuery: String = ""
    private var currentSort: SortType = SortType.RECENT

    private enum class SortType { RECENT, NAME, SIZE, DURATION }

    private val requiredPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadVideos() else showEmptyState(showPermissionButton = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = VideoAdapter { position ->
            openPlayer(adapter.currentList, position)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.isNestedScrollingEnabled = false

        binding.grantPermissionButton.setOnClickListener {
            permissionLauncher.launch(requiredPermission)
        }

        setupSearch()
        setupSortChips()
        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) loadVideos()
    }

    // ---------- الصلاحيات ----------

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, requiredPermission) ==
            PackageManager.PERMISSION_GRANTED

    private fun checkPermissionAndLoad() {
        if (hasPermission()) loadVideos() else permissionLauncher.launch(requiredPermission)
    }

    // ---------- تحميل الفيديوهات ----------

    private fun loadVideos() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE

        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                VideoRepository.getAllVideos(this@MainActivity)
            }
            allVideos = videos
            binding.progressBar.visibility = View.GONE
            applyFilterAndSort()
        }
    }

    // ---------- البحث ----------

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                binding.clearSearchButton.visibility =
                    if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilterAndSort()
            }
        })

        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text.clear()
        }
    }

    // ---------- الفرز ----------

    private fun setupSortChips() {
        val chips = mapOf(
            binding.chipRecent to SortType.RECENT,
            binding.chipName to SortType.NAME,
            binding.chipSize to SortType.SIZE,
            binding.chipDuration to SortType.DURATION
        )

        chips.forEach { (chip, type) ->
            chip.setOnClickListener {
                currentSort = type
                updateChipStyles(chips)
                applyFilterAndSort()
            }
        }
        updateChipStyles(chips)
    }

    private fun updateChipStyles(chips: Map<android.widget.TextView, SortType>) {
        chips.forEach { (chip, type) ->
            if (type == currentSort) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    // ---------- تطبيق الفلترة والفرز معاً ----------

    private fun applyFilterAndSort() {
        var result = allVideos

        if (currentQuery.isNotBlank()) {
            result = result.filter { it.title.contains(currentQuery, ignoreCase = true) }
        }

        result = when (currentSort) {
            SortType.RECENT -> result // مرتبة أصلاً من الأحدث عبر MediaStore
            SortType.NAME -> result.sortedBy { it.title.lowercase() }
            SortType.SIZE -> result.sortedByDescending { it.sizeBytes }
            SortType.DURATION -> result.sortedByDescending { it.durationMs }
        }

        binding.videoCountText.text = getString(R.string.videos_count, allVideos.size)

        if (result.isEmpty()) {
            if (allVideos.isEmpty()) {
                showEmptyState(showPermissionButton = false)
            } else {
                showEmptyState(showPermissionButton = false, isSearchEmpty = true)
            }
        } else {
            binding.emptyStateLayout.visibility = View.GONE
        }

        adapter.submitList(result)
    }

    private fun showEmptyState(showPermissionButton: Boolean, isSearchEmpty: Boolean = false) {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.grantPermissionButton.visibility =
            if (showPermissionButton) View.VISIBLE else View.GONE
        binding.emptyStateText.text = when {
            showPermissionButton -> getString(R.string.permission_needed)
            isSearchEmpty -> getString(R.string.no_search_results)
            else -> getString(R.string.no_videos_found)
        }
    }

    private fun openPlayer(videos: List<VideoItem>, position: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putParcelableArrayListExtra(PlayerActivity.EXTRA_VIDEOS, ArrayList(videos))
        intent.putExtra(PlayerActivity.EXTRA_INDEX, position)
        startActivity(intent)
    }
}

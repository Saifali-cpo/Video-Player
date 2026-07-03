package com.example.myvideoplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var currentVideos: List<VideoItem> = emptyList()

    private val requiredPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadVideos()
        } else {
            showEmptyState(showPermissionButton = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = VideoAdapter { position ->
            openPlayer(position)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.grantPermissionButton.setOnClickListener {
            permissionLauncher.launch(requiredPermission)
        }

        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        // إعادة تحميل القائمة عند الرجوع من شاشة التشغيل (في حال حذف/إضافة فيديوهات)
        if (hasPermission()) {
            loadVideos()
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, requiredPermission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionAndLoad() {
        if (hasPermission()) {
            loadVideos()
        } else {
            permissionLauncher.launch(requiredPermission)
        }
    }

    private fun loadVideos() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.emptyStateLayout.visibility = android.view.View.GONE

        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                VideoRepository.getAllVideos(this@MainActivity)
            }
            currentVideos = videos
            binding.progressBar.visibility = android.view.View.GONE

            if (videos.isEmpty()) {
                showEmptyState(showPermissionButton = false)
            } else {
                binding.emptyStateLayout.visibility = android.view.View.GONE
                adapter.submitList(videos)
            }
        }
    }

    private fun showEmptyState(showPermissionButton: Boolean) {
        binding.emptyStateLayout.visibility = android.view.View.VISIBLE
        binding.grantPermissionButton.visibility =
            if (showPermissionButton) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyStateText.text = if (showPermissionButton)
            getString(R.string.permission_needed) else getString(R.string.no_videos_found)
    }

    private fun openPlayer(position: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putParcelableArrayListExtra(PlayerActivity.EXTRA_VIDEOS, ArrayList(currentVideos))
        intent.putExtra(PlayerActivity.EXTRA_INDEX, position)
        startActivity(intent)
    }
}

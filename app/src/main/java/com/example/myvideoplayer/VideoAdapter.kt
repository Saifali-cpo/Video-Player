package com.example.myvideoplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myvideoplayer.databinding.ItemVideoBinding
import com.example.myvideoplayer.model.VideoItem
import java.util.concurrent.TimeUnit

class VideoAdapter(
    private val onClick: (position: Int) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener { onClick(position) }
    }

    class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoItem) {
            binding.titleText.text = video.title
            binding.durationText.text = formatDuration(video.durationMs)
            binding.sizeText.text = formatSize(video.sizeBytes)

            Glide.with(binding.thumbnailImage.context)
                .load(video.uri)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_placeholder)
                .into(binding.thumbnailImage)
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        private fun formatSize(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else String.format("%.1f MB", mb)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem == newItem
    }
}

package com.example.myvideoplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.myvideoplayer.model.VideoItem

object VideoRepository {

    /**
     * يقرأ كل الفيديوهات المتوفرة على الجهاز باستخدام MediaStore
     * ويعيدها مرتبة من الأحدث إلى الأقدم.
     */
    fun getAllVideos(context: Context): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "فيديو"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)

                val contentUri = ContentUris.withAppendedId(collection, id)

                videos.add(
                    VideoItem(
                        id = id,
                        title = name,
                        uri = contentUri,
                        durationMs = duration,
                        sizeBytes = size
                    )
                )
            }
        }

        return videos
    }
}

package com.example.caroapp

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class MusicManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    // Phát nhạc nền từ tài nguyên
    fun playMusic(musicResId: Int) {
        // Nếu có nhạc đang phát, dừng lại trước khi phát nhạc mới
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.release()
            }
        }

        mediaPlayer = MediaPlayer.create(context, musicResId)
        mediaPlayer?.isLooping = true // Lặp lại nhạc nền
        mediaPlayer?.start()
    }


    // Dừng nhạc nền
    fun stopMusic() {
        mediaPlayer?.let {
            it.stop()
            it.release()
            mediaPlayer = null
        }
    }

    // Tiếp tục nhạc nền (sử dụng khi Activity được phục hồi)
    fun resumeMusic() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    // Tạm dừng nhạc nền (khi Activity bị tạm dừng)
    fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }
    // Thêm vào class MusicManager
    @RequiresApi(Build.VERSION_CODES.N)
    fun playShortSound(soundResId: Int) {
        try {
            Log.d("MusicManager", "Chuẩn bị phát âm thanh: $soundResId")

            // Kiểm tra file âm thanh có tồn tại
            context.resources.openRawResourceFd(soundResId)?.use { fd ->
                Log.d("MusicManager", "File âm thanh tồn tại và có kích thước: ${fd.length}")
            }

            // Dọn dẹp MediaPlayer cũ nếu có
            mediaPlayer?.release()
            mediaPlayer = null

            // Tạo MediaPlayer mới
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context.resources.openRawResourceFd(soundResId))
                prepare()
                setOnPreparedListener {
                    Log.d("MusicManager", "MediaPlayer đã sẵn sàng, bắt đầu phát")
                    start()
                }
                setOnCompletionListener {
                    Log.d("MusicManager", "Phát âm thanh hoàn tất")
                    release()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicManager", "Lỗi MediaPlayer: what=$what, extra=$extra")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("MusicManager", "Lỗi khi phát âm thanh", e)
            e.printStackTrace()
        }
    }

}

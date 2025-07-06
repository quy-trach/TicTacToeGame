package com.example.caroapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity

class SelectModeActivity : ComponentActivity() {

    private lateinit var musicManager: MusicManager

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_mode)

        val playWithFriendButton: Button = findViewById(R.id.playWithFriendButton)
        val playWithAIButton: Button = findViewById(R.id.playWithAIButton)
        val playOnlineButton: Button = findViewById(R.id.playOnlineButton)
        var playRandomButton: Button = findViewById(R.id.playRandomButton)


        // Khởi tạo MusicManager
        musicManager = MusicManager(this)
        musicManager.playMusic(R.raw.musictictac) // Nhạc nền cho màn hình chọn chế độ
        // Xử lý khi nhấn "Chơi 2 Người"
        playWithFriendButton.setOnClickListener {
            musicManager.stopMusic()
            // Chuyển đến màn hình chơi với 2 người
            val intent = Intent(this, MainActivity::class.java)  // MainActivity là nơi bạn đã làm bàn cờ
            startActivity(intent)

        }

        // Xử lý khi nhấn "Chơi với Máy"
        playWithAIButton.setOnClickListener {
            musicManager.stopMusic()
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("mode", "AI")  // Chế độ chơi với AI
            startActivity(intent)

        }
        // Xử lý khi nhấn "Chơi Trực Tuyến"
        playOnlineButton.setOnClickListener {
            musicManager.stopMusic()
            // Chuyển đến màn hình OnlineModeActivity
            val intent = Intent(this, OnlineGameActivity::class.java)
            startActivity(intent)

        }

        // Xử lý khi nhấn "Chơi Ngẫu Nhiên"
        playRandomButton.setOnClickListener {
            musicManager.stopMusic()
            val matchMaker = RandomMatchMaker(this)
            matchMaker.startRandomMatch()
        }


    }
}

package com.example.caroapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.caroapp.R.color.redstatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// OnlineGameActivity.kt
@Suppress("DEPRECATION")
class OnlineGameActivity : ComponentActivity() {
    private lateinit var backButton: ImageButton
    private lateinit var gameIdInput: EditText
    private lateinit var createGameButton: Button
    private lateinit var joinGameButton: Button
    private lateinit var roomsLayout: LinearLayout
    private lateinit var musicManager: MusicManager
    private lateinit var gameId: String
    private lateinit var db: FirebaseFirestore

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_id)

        db = FirebaseFirestore.getInstance()

        gameIdInput = findViewById(R.id.gameIdInput)
        createGameButton = findViewById(R.id.createGameButton)
        joinGameButton = findViewById(R.id.joinGameButton)
        roomsLayout = findViewById(R.id.roomsLayout)
        // Khởi tạo MusicManager
        musicManager = MusicManager(this)

        createGameButton.setOnClickListener {
            createNewGame()
        }

        joinGameButton.setOnClickListener {
            joinGame(gameId)
        }
        backButton = findViewById(R.id.backButton)
        // Xử lý sự kiện khi nhấn nút quay lại
        backButton.setOnClickListener {
            val intent = Intent(this, SelectModeActivity::class.java)
            startActivity(intent)
            finish() // Đảm bảo không giữ lại MainActivity trong stack
        }
        loadRooms()  // Load danh sách phòng khi mở activity
    }


    @SuppressLint("InflateParams", "SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun loadRooms() {
        db.collection("games")
            .get()
            .addOnSuccessListener { result ->
                roomsLayout.removeAllViews()
                for (document in result) {
                    val gameId = document.id
                    val status = document.getString("status") ?: "unknown"
                    val playerX = document.getString("playerX")
                    val playerO = document.getString("playerO")

                    val roomView = LayoutInflater.from(this).inflate(R.layout.room_item, null)
                    val roomIdTextView = roomView.findViewById<TextView>(R.id.roomIdTextView)
                    val roomStatusTextView = roomView.findViewById<TextView>(R.id.roomStatusTextView)
                    val deleteButton = roomView.findViewById<ImageButton>(R.id.deleteButton)

                    roomIdTextView.text = "ID: $gameId"

                    // Cập nhật trạng thái phòng
                    when {
                        playerO?.isNotEmpty() == true -> {
                            roomStatusTextView.text = "Phòng đã đầy"
                            roomStatusTextView.setTextColor(ContextCompat.getColor(this, redstatus))
                            roomStatusTextView.setBackgroundResource(R.drawable.status_red_background)
                            roomView.isClickable = false
                        }
                        status == "waiting" -> {
                            roomStatusTextView.text = "Phòng trống"
                            roomStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.green_dark))
                            roomStatusTextView.setBackgroundResource(R.drawable.status_green_background)
                            roomView.isClickable = true
                        }
                        else -> {
                            roomStatusTextView.text = "Không khả dụng"
                            roomStatusTextView.setTextColor(resources.getColor(android.R.color.darker_gray))
                            roomView.isClickable = false
                        }
                    }

                    // Hiển thị nút xóa nếu người dùng là người tạo (Player X)
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    if (playerX == currentUserId) {
                        deleteButton.visibility = View.VISIBLE
                        deleteButton.setOnClickListener {
                            deleteRoom(gameId)
                        }
                    } else {
                        deleteButton.visibility = View.GONE
                    }

                    // Sự kiện khi nhấn vào phòng
                    roomView.setOnClickListener {
                        if (status == "waiting" && playerO?.isEmpty() == true) {
                            joinGame(gameId)
                        } else {
                            Toast.makeText(this, "Phòng không khả dụng", Toast.LENGTH_SHORT).show()
                        }
                    }

                    roomsLayout.addView(roomView)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error loading rooms: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    @RequiresApi(Build.VERSION_CODES.N)
    private fun deleteRoom(gameId: String) {
        db.collection("games").document(gameId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val playerX = document.getString("playerX")
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

                    if (playerX == currentUserId) {
                        document.reference.delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Phòng $gameId đã được xóa", Toast.LENGTH_SHORT).show()
                                loadRooms() // Refresh danh sách phòng
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Lỗi khi xóa phòng: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Bạn không có quyền xóa phòng này", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Phòng không tồn tại", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createNewGame() {
        val customGameId = gameIdInput.text.toString().trim()

        if (customGameId.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập ID phòng", Toast.LENGTH_SHORT).show()
            return
        }

        val flatBoard = List(15 * 15) { "" }  // Danh sách phẳng thay vì Array

        val gameData = hashMapOf(
            "currentPlayer" to "X",
            "board" to flatBoard,  // Sử dụng List thay vì Array
            "status" to "waiting",  // Trạng thái ban đầu
            "playerX" to FirebaseAuth.getInstance().currentUser?.uid,
            "playerO" to "",
            "winner" to ""
        )

        // Lưu dữ liệu vào Firestore với ID phòng đã nhập
        db.collection("games").document(customGameId)
            .set(gameData)
            .addOnSuccessListener {
                Toast.makeText(this, "Game ID: $customGameId", Toast.LENGTH_LONG).show()
                startGame(customGameId, "X") // Bắt đầu game cho người tạo
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    @RequiresApi(Build.VERSION_CODES.N)
    private fun joinGame(gameId: String) {
        db.collection("games").document(gameId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val playerO = document.getString("playerO")
                    val status = document.getString("status")

                    if (playerO?.isEmpty() == true && status == "waiting") {
                        document.reference.update(
                            mapOf(
                                "playerO" to FirebaseAuth.getInstance().currentUser?.uid,
                                "status" to "playing",
                                "currentPlayer" to "X"
                            )
                        ).addOnSuccessListener {
                            try {
                                Log.d("GameSound", "Chuẩn bị phát âm thanh khi tham gia game")
                                musicManager.playShortSound(R.raw.drum_start)
                            } catch (e: Exception) {
                                Log.e("GameSound", "Lỗi khi phát âm thanh", e)
                            }

                            Toast.makeText(this, "Bạn đã tham gia trò chơi với tư cách là Người chơi O", Toast.LENGTH_SHORT).show()
                            startGame(gameId, "O")
                        }
                    }
                }
            }
    }


    private fun startGame(gameId: String, player: String) {
        val intent = Intent(this, OnlineGameBoardActivity::class.java)
        intent.putExtra("gameId", gameId)
        intent.putExtra("player", player)
        startActivity(intent)
    }
    // Quản lý vòng đời của Activity
    override fun onPause() {
        super.onPause()
        musicManager.pauseMusic()
    }

    override fun onResume() {
        super.onResume()
        musicManager.resumeMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        musicManager.stopMusic()
    }


}
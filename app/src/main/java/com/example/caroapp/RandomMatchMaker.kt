package com.example.caroapp

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

class RandomMatchMaker(private val activity: Activity) {

    private val db = FirebaseFirestore.getInstance()
    private var musicManager: MusicManager = MusicManager(activity)
    private var isMatched = false // Biến để theo dõi trạng thái ghép cặp

    fun startRandomMatch() {
        if (!isMatched) {
            showUsernameDialog()
        }
    }

    private fun showUsernameDialog() {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_input_username)
        dialog.setCancelable(false)

        val editUsername = dialog.findViewById<EditText>(R.id.editUsername)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val confirmButton = dialog.findViewById<Button>(R.id.confirmButton)

        cancelButton.setOnClickListener {
            dialog.dismiss()
            musicManager.playMusic(R.raw.musictictac)
        }

        confirmButton.setOnClickListener {
            val username = editUsername.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(activity, "Vui lòng nhập tên!", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                findRandomOpponent(username)
            }
        }

        dialog.show()
    }

    private fun findRandomOpponent(username: String) {
        Toast.makeText(activity, "Đang tìm kiếm người chơi...", Toast.LENGTH_SHORT).show()
        val playerId = UUID.randomUUID().toString()
        val playerData = mapOf(
            "playerId" to playerId,
            "username" to username,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("waiting_players").document(playerId)
            .set(playerData)
            .addOnSuccessListener {
                db.collection("waiting_players")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(2)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val players = snapshot.documents
                        if (players.size >= 2 && !isMatched) {
                            val player1 = players[0]
                            val player2 = players[1]

                            if (player1.id != playerId && player2.id != playerId) return@addOnSuccessListener

                            isMatched = true // Đánh dấu đã ghép cặp
                            val opponent = if (player1.id == playerId) player2 else player1
                            val opponentUsername = opponent.getString("username") ?: "Opponent"

                            val gameId = UUID.randomUUID().toString()
                            val gameData = mapOf(
                                "player1" to mapOf("id" to player1.id, "username" to player1.getString("username")),
                                "player2" to mapOf("id" to player2.id, "username" to player2.getString("username")),
                                "board" to List(15 * 15) { "" },
                                "currentTurn" to "X",
                                "status" to "active",
                                "createdAt" to System.currentTimeMillis()
                            )

                            db.collection("games").document(gameId)
                                .set(gameData)
                                .addOnSuccessListener {
                                    db.collection("waiting_players").document(player1.id).delete()
                                    db.collection("waiting_players").document(player2.id).delete()

                                    val intent = Intent(activity, OnlineGameBoardActivity::class.java)
                                    intent.putExtra("gameId", gameId)
                                    intent.putExtra("player", if (playerId == player1.id) "X" else "O")
                                    intent.putExtra("myUsername", username) // Truyền tên của người chơi
                                    intent.putExtra("opponentUsername", opponentUsername) // Truyền tên đối thủ
                                    activity.startActivity(intent)
                                    activity.finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(activity, "Lỗi tạo phòng: ${e.message}", Toast.LENGTH_SHORT).show()
                                    db.collection("waiting_players").document(playerId).delete()
                                    musicManager.playMusic(R.raw.musictictac)
                                    isMatched = false // Reset trạng thái nếu thất bại
                                }
                        } else {
                            listenForMatch(playerId, username)
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(activity, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                        db.collection("waiting_players").document(playerId).delete()
                        musicManager.playMusic(R.raw.musictictac)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                musicManager.playMusic(R.raw.musictictac)
            }
    }

    private fun listenForMatch(playerId: String, username: String) {
        if (isMatched) return // Thoát nếu đã ghép cặp

        var listener1: com.google.firebase.firestore.ListenerRegistration? = null
        var listener2: com.google.firebase.firestore.ListenerRegistration? = null

        listener1 = db.collection("games")
            .whereIn("player1.id", listOf(playerId))
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(activity, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty && !isMatched) {
                    val game = snapshot.documents[0]
                    val gameId = game.id
                    val player1Id = game.get("player1.id") as String
                    val player1Username = game.get("player1.username") as String
                    val player2Username = game.get("player2.username") as String
                    val opponentUsername = if (playerId == player1Id) player2Username else player1Username

                    isMatched = true // Đánh dấu đã ghép cặp
                    listener1?.remove() // Hủy listener 1
                    listener2?.remove() // Hủy listener 2
                    db.collection("waiting_players").document(playerId).delete()

                    val intent = Intent(activity, OnlineGameBoardActivity::class.java)
                    intent.putExtra("gameId", gameId)
                    intent.putExtra("player", if (playerId == player1Id) "X" else "O")
                    intent.putExtra("myUsername", username) // Truyền tên của người chơi
                    intent.putExtra("opponentUsername", opponentUsername) // Truyền tên đối thủ
                    activity.startActivity(intent)
                    activity.finish()
                }
            }

        listener2 = db.collection("games")
            .whereIn("player2.id", listOf(playerId))
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(activity, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty && !isMatched) {
                    val game = snapshot.documents[0]
                    val gameId = game.id
                    val player1Id = game.get("player1.id") as String
                    val player1Username = game.get("player1.username") as String
                    val player2Username = game.get("player2.username") as String
                    val opponentUsername = if (playerId == player1Id) player2Username else player1Username

                    isMatched = true // Đánh dấu đã ghép cặp
                    listener1?.remove() // Hủy listener 1
                    listener2?.remove() // Hủy listener 2
                    db.collection("waiting_players").document(playerId).delete()

                    val intent = Intent(activity, OnlineGameBoardActivity::class.java)
                    intent.putExtra("gameId", gameId)
                    intent.putExtra("player", if (playerId == player1Id) "X" else "O")
                    intent.putExtra("myUsername", username) // Truyền tên của người chơi
                    intent.putExtra("opponentUsername", opponentUsername) // Truyền tên đối thủ
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
    }
}
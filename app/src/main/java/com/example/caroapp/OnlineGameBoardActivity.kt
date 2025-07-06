package com.example.caroapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

@Suppress("DEPRECATION")
class OnlineGameBoardActivity : ComponentActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var backButton: ImageButton
    private lateinit var clickSound: MediaPlayer
    private lateinit var playersInfoText: TextView
    private lateinit var db: FirebaseFirestore
    private lateinit var gameId: String
    private lateinit var player: String
    private var currentTurn: String = "X"
    private var isMyTurn: Boolean = false
    private var lastMove: Int = -1
    private var board: MutableList<String> = MutableList(15 * 15) { "" }
    private var isUpdatingUI = false
    private var hasShownWinLosePopup = false
    private var gameListener: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        gameId = intent.getStringExtra("gameId") ?: ""
        player = intent.getStringExtra("player") ?: ""
        val initialMyUsername = intent.getStringExtra("myUsername") ?: "Bạn"
        val initialOpponentUsername = intent.getStringExtra("opponentUsername") ?: "Đối thủ"
        gridLayout = findViewById(R.id.gridLayout)
        backButton = findViewById(R.id.backButton)
        playersInfoText = findViewById(R.id.playersInfoText)

        // Đặt giá trị ban đầu từ Intent
        updatePlayersInfoText(initialMyUsername, initialOpponentUsername)

        initializeSounds()
        createBoard()
        listenForGameUpdates()

        db.collection("games").document(gameId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val newBoard = (snapshot.get("board") as? List<String>)?.toMutableList()
                    if (newBoard != null && newBoard.size == 15 * 15) {
                        board = newBoard
                    }
                    currentTurn = snapshot.getString("currentTurn") ?: "X"
                    isMyTurn = currentTurn == player
                    updateBoardUI()
                }
            }

        backButton.setOnClickListener {
            db.collection("games").document(gameId).update("roomStatus", "ended")
        }
    }

    private fun initializeSounds() {
        clickSound = MediaPlayer.create(this, R.raw.pick)
    }

    private fun playClickSound() {
        if (clickSound.isPlaying) {
            clickSound.seekTo(0)
        } else {
            clickSound.start()
        }
    }

    private fun createBoard() {
        for (row in 0 until 15) {
            for (col in 0 until 15) {
                val button = ImageButton(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 100
                        height = 100
                        setMargins(3, 3, 3, 3)
                    }
                    setBackgroundColor(resources.getColor(android.R.color.background_light))
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    adjustViewBounds = true
                    setOnClickListener { onCellClicked(row, col) }
                }
                gridLayout.addView(button)
            }
        }
    }

    private fun onCellClicked(row: Int, col: Int) {
        if (!isMyTurn) {
            Toast.makeText(this, "Chưa đến lượt bạn!", Toast.LENGTH_SHORT).show()
            return
        }

        val index = row * 15 + col
        if (board[index].isEmpty()) {
            playClickSound()
            board[index] = player

            updateBoardUI()

            val winningPositions = checkWin(row, col, player)
            if (winningPositions != null) {
                db.collection("games").document(gameId).update(
                    mapOf(
                        "board" to board,
                        "winner" to player,
                        "winningPositions" to winningPositions
                    )
                ).addOnSuccessListener {
                    updateBoardUI(winningPositions)
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi cập nhật chiến thắng: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return
            }

            currentTurn = if (player == "X") "O" else "X"
            isMyTurn = false
            updateBoardOnFirestore()
        } else {
            Toast.makeText(this, "Ô này đã được chọn", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBoardOnFirestore() {
        db.collection("games").document(gameId).update(
            mapOf(
                "board" to board,
                "currentTurn" to currentTurn
            )
        ).addOnFailureListener { e ->
            Toast.makeText(this, "Lỗi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun highlightWinningPositions(winningPositions: List<Int>) {
        for (position in winningPositions) {
            val button = gridLayout.getChildAt(position) as ImageButton
            button.setBackgroundColor(resources.getColor(R.color.green_dark))
        }
    }

    private fun listenForGameUpdates() {
        gameListener = db.collection("games").document(gameId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val roomStatus = snapshot.getString("roomStatus") ?: "active"
                    if (roomStatus == "ended") {
                        gameListener?.remove()
                        deleteRoomAndExit()
                        return@addSnapshotListener
                    }

                    val newBoard = (snapshot.get("board") as? List<String>)?.toMutableList()
                    if (newBoard == null || newBoard.size != 15 * 15) return@addSnapshotListener

                    if (board != newBoard) {
                        for (i in board.indices) {
                            if (board[i] != newBoard[i]) {
                                lastMove = i
                                break
                            }
                        }
                        board = newBoard
                    }

                    currentTurn = snapshot.getString("currentTurn") ?: "X"
                    val winner = snapshot.getString("winner")
                    val winningPositions = snapshot.get("winningPositions") as? List<Int>
                    isMyTurn = currentTurn == player

                    // Cập nhật tên người chơi từ Firestore
                    val player1Data = snapshot.get("player1") as? Map<String, String>
                    val player2Data = snapshot.get("player2") as? Map<String, String>
                    val myUsername = if (player == "X") player1Data?.get("username") ?: "Bạn" else player2Data?.get("username") ?: "Bạn"
                    val opponentUsername = if (player == "X") player2Data?.get("username") ?: "Đối thủ" else player1Data?.get("username") ?: "Đối thủ"
                    updatePlayersInfoText(myUsername, opponentUsername)

                    if (!isUpdatingUI) {
                        isUpdatingUI = true
                        updateBoardUI(winningPositions, winner)
                        if (winningPositions != null) {
                            highlightWinningPositions(winningPositions)
                        }
                        if (!winner.isNullOrEmpty() && !hasShownWinLosePopup) {
                            hasShownWinLosePopup = true
                            showWinLosePopup(winner == player)
                        }
                        isUpdatingUI = false
                    }

                    val restartRequests = snapshot.get("restartRequests") as? Map<String, Boolean>
                    if (restartRequests != null) {
                        val playerXDecision = restartRequests["playerX"] ?: return@addSnapshotListener
                        val playerODecision = restartRequests["playerO"] ?: return@addSnapshotListener

                        if (playerXDecision && playerODecision) {
                            hasShownWinLosePopup = false
                            resetBoard()
                        } else if (!playerXDecision || !playerODecision) {
                            db.collection("games").document(gameId).update("roomStatus", "ended")
                        }
                    }
                } else {
                    gameListener?.remove()
                    deleteRoomAndExit()
                }
            }
    }

    private fun updateBoardUI(winningPositions: List<Int>? = null, winner: String? = null) {
        handler.removeCallbacksAndMessages(null)

        for (i in board.indices) {
            val button = gridLayout.getChildAt(i) as ImageButton
            val isLastMove = i == lastMove && lastMove != -1

            when (board[i]) {
                "X" -> {
                    button.setImageResource(if (isLastMove && player != "X") R.drawable.x_light else R.drawable.x)
                    if (isLastMove && player != "X") {
                        handler.postDelayed({
                            button.setImageResource(R.drawable.x)
                        }, 1000)
                    }
                }
                "O" -> {
                    button.setImageResource(if (isLastMove && player != "O") R.drawable.o_light else R.drawable.o)
                    if (isLastMove && player != "O") {
                        handler.postDelayed({
                            button.setImageResource(R.drawable.o)
                        }, 1000)
                    }
                }
                else -> {
                    button.setImageDrawable(null)
                }
            }

            if (winningPositions != null && winningPositions.contains(i)) {
                button.setBackgroundColor(resources.getColor(R.color.green_dark))
                if (winner != null) {
                    when (winner) {
                        "X" -> button.setImageResource(R.drawable.x)
                        "O" -> button.setImageResource(R.drawable.o)
                    }
                }
            } else {
                button.setBackgroundColor(resources.getColor(android.R.color.background_light))
            }

            button.scaleType = ImageView.ScaleType.CENTER_INSIDE
            button.adjustViewBounds = true
        }

        lastMove = -1
    }

    private fun checkWin(row: Int, col: Int, player: String): List<Int>? {
        val directions = listOf(
            Pair(1, 0),
            Pair(0, 1),
            Pair(1, 1),
            Pair(1, -1)
        )

        for ((dx, dy) in directions) {
            val winningPositions = mutableListOf<Int>()
            var count = 1
            winningPositions.add(row * 15 + col)

            var r = row + dx
            var c = col + dy
            while (r in 0..14 && c in 0..14 && board[r * 15 + c] == player) {
                winningPositions.add(r * 15 + c)
                count++
                r += dx
                c += dy
            }

            r = row - dx
            c = col - dy
            while (r in 0..14 && c in 0..14 && board[r * 15 + c] == player) {
                winningPositions.add(r * 15 + c)
                count++
                r -= dx
                c -= dy
            }

            if (count >= 5) {
                println("Winning positions for player $player: ${winningPositions.distinct().sorted()}")
                return winningPositions.distinct().sorted()
            }
        }
        return null
    }

    @SuppressLint("InflateParams")
    private fun showWinLosePopup(isWinner: Boolean) {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val dialogView = layoutInflater.inflate(R.layout.dialog_win_lose, null)
        dialog.setContentView(dialogView)

        val statusImage = dialogView.findViewById<ImageView>(R.id.statusImage)
        val scaleAnimation = ScaleAnimation(
            0f, 1f, 0f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 500 }
        val fadeAnimation = AlphaAnimation(0f, 1f).apply { duration = 500 }
        val animationSet = AnimationSet(true).apply {
            addAnimation(scaleAnimation)
            addAnimation(fadeAnimation)
        }

        statusImage.setImageResource(if (isWinner) R.drawable.win else R.drawable.lose)
        playSound(if (isWinner) R.raw.win_sound else R.raw.lose_sound)

        handler.postDelayed({ statusImage.startAnimation(animationSet) }, 300)

        statusImage.setOnClickListener {
            val fadeOut = AlphaAnimation(1f, 0f).apply {
                duration = 300
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationEnd(animation: Animation?) {
                        dialog.dismiss()
                        showRestartPrompt()
                    }
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            }
            statusImage.startAnimation(fadeOut)
        }
        dialog.show()
    }

    @SuppressLint("InflateParams")
    private fun showRestartPrompt() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val dialogView = layoutInflater.inflate(R.layout.dialog_restart_prompt, null)
        dialog.setContentView(dialogView)
        dialog.setCancelable(false)

        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val playAgainButton = dialogView.findViewById<Button>(R.id.playAgainButton)
        val exitButton = dialogView.findViewById<Button>(R.id.exitButton)

        messageText.text = "Bạn có muốn chơi lại không?"

        playAgainButton.setOnClickListener {
            dialog.dismiss()
            requestPlayAgain(true)
        }

        exitButton.setOnClickListener {
            dialog.dismiss()
            requestPlayAgain(false)
        }
        handler.postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
                requestPlayAgain(false)
            }
        }, 30000)
        dialog.show()
    }

    private fun requestPlayAgain(wantsToPlayAgain: Boolean) {
        val requestData = hashMapOf(
            if (player == "X") "playerX" to wantsToPlayAgain else "playerO" to wantsToPlayAgain
        )

        db.collection("games").document(gameId)
            .update("restartRequests", requestData)
            .addOnSuccessListener {
                // Không cần gọi listener riêng, đã gộp vào listenForGameUpdates
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetBoard() {
        val emptyBoard = List(15 * 15) { "" }
        db.collection("games").document(gameId).update(
            mapOf(
                "board" to emptyBoard,
                "currentTurn" to "X",
                "winner" to "",
                "winningPositions" to null,
                "restartRequests" to null,
                "roomStatus" to "active"
            )
        ).addOnSuccessListener {
            board = emptyBoard.toMutableList()
            currentTurn = "X"
            isMyTurn = player == "X"
            updateBoardUI()
            Toast.makeText(this, "Bàn cờ đã được làm mới", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Lỗi khi làm mới bàn cờ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRoomAndExit() {
        db.collection("games").document(gameId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Phòng đã được xóa", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SelectModeActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi khi xóa phòng: ${e.message}", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SelectModeActivity::class.java)
                startActivity(intent)
                finish()
            }
    }

    private fun playSound(soundResId: Int) {
        val soundPlayer = MediaPlayer.create(this, soundResId)
        soundPlayer.start()
        soundPlayer.setOnCompletionListener { it.release() }
    }

    private fun updatePlayersInfoText(myUsername: String, opponentUsername: String) {
        playersInfoText.text = "$myUsername vs $opponentUsername"
    }

    override fun onResume() {
        super.onResume()
        initializeSounds()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameListener?.remove()
        handler.removeCallbacksAndMessages(null)
        clickSound.release()
    }
}
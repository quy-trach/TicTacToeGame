package com.example.caroapp

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private lateinit var backButton: ImageButton
    private lateinit var resetButton: ImageButton
    private lateinit var clickSound: MediaPlayer
    private lateinit var board: Array<Array<ImageButton>>  // Lưu các nút trên bàn cờ
    private val gridSize = 15  // Kích thước bàn cờ (có thể thay đổi tùy ý)
    private val gameState = Array(gridSize) { Array(gridSize) { "" } }  // Mảng lưu trạng thái của bàn cờ
    private var currentPlayer = "X"  // Người chơi hiện tại
    private var isPlayingWithAI = false // Biến kiểm tra chế độ chơi với AI
    private var isAiThinking = false // Biến kiểm tra xem AI có đang nghĩ đến lượt đi không
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val MAX_DEPTH = 4  // Giới hạn độ sâu của minimax
    // Thêm hằng số cho điểm số
    private val FIVE_IN_ROW = 1000000
    private val OPEN_FOUR = 10000
    private val FOUR_IN_ROW = 1000
    private val OPEN_THREE = 100
    private val THREE_IN_ROW = 10
    private val OPEN_TWO = 5
    private val TWO_IN_ROW = 1

    // Tối ưu
    private var lastMove: Pair<Int, Int>? = null
    private val searchRadius = 3  // Bán kính tìm kiếm xung quanh nước đi gần nhất
    private val transpositionTable = mutableMapOf<String, Int>() // Cache cho các trạng thái đã tính
    private val moveTimeLimit = 1000L // Giới hạn thời gian suy nghĩ (1 giây)
    private var isTimeUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Kiểm tra chế độ chơi từ intent
        val mode = intent.getStringExtra("mode")
        isPlayingWithAI = mode == "AI"

        val gridLayout: GridLayout = findViewById(R.id.gridLayout)
        backButton = findViewById(R.id.backButton)
        resetButton = findViewById(R.id.resetButton)

        // Xử lý sự kiện khi nhấn nút quay lại
        backButton.setOnClickListener {
            val intent = Intent(this, SelectModeActivity::class.java)
            startActivity(intent)
            finish() // Đảm bảo không giữ lại MainActivity trong stack
        }

        // Xử lý sự kiện khi nhấn nút reset (hiển thị và hoạt động trong cả hai chế độ)
        resetButton.visibility = ImageButton.VISIBLE
        resetButton.setOnClickListener {
            resetGame()
            Toast.makeText(this, "Ván chơi đã được reset!", Toast.LENGTH_SHORT).show()
            if (isPlayingWithAI && currentPlayer == "O" && !isAiThinking) {
                aiMove() // Nếu reset khi đang đến lượt AI, gọi AI đi lại
            }
        }

        // Tạo bàn cờ với số lượng hàng và cột linh hoạt
        board = Array(gridSize) { Array(gridSize) { ImageButton(this) } }

        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                val button = ImageButton(this)
                button.layoutParams = GridLayout.LayoutParams().apply {
                    width = 100  // Chiều chỉnh chiều rộng mỗi ô
                    height = 100  // Điều chỉnh chiều cao mỗi ô
                    setMargins(3, 3, 3, 3) // Khoảng cách giữa các ô
                }
                button.setBackgroundColor(resources.getColor(android.R.color.background_light)) // Màu nền của ô
                button.scaleType = ImageView.ScaleType.CENTER_INSIDE // Đảm bảo hình ảnh vừa khít trong ô

                button.setOnClickListener {
                    // Kiểm tra nếu ô trống
                    if (gameState[i][j] == "") {
                        gameState[i][j] = currentPlayer
                        // Phát âm thanh khi người chơi nhấn
                        playClickSound()
                        // Thay đổi hình ảnh cho X và O
                        if (currentPlayer == "X") {
                            button.setImageResource(R.drawable.x)  // Hình ảnh cho X
                        } else {
                            button.setImageResource(R.drawable.o) // Hình ảnh cho O
                        }

                        // Kiểm tra xem có chiến thắng hay không
                        if (checkWin(i, j)) {
                            Toast.makeText(this, "$currentPlayer là người chiến thắng", Toast.LENGTH_LONG).show()
                        } else if (isBoardFull()) {
                            Toast.makeText(this, "Ván này hòa!", Toast.LENGTH_LONG).show()
                        } else {
                            // Chuyển lượt chơi
                            currentPlayer = if (currentPlayer == "X") "O" else "X"

                            // Nếu là chế độ chơi với AI và lượt của AI, thực hiện di chuyển cho AI
                            if (isPlayingWithAI && currentPlayer == "O" && !isAiThinking) {
                                aiMove()
                            }
                        }
                    }
                }

                board[i][j] = button
                gridLayout.addView(button) // Thêm mỗi nút vào GridLayout
            }
        }
    }

    private fun initializeSounds() {
        // Tạo MediaPlayer để phát âm thanh nhấn
        clickSound = MediaPlayer.create(this, R.raw.pick)  // Thay `click_sound` bằng tên tệp âm thanh của bạn
    }

    private fun playClickSound() {
        // Phát âm thanh khi nhấn
        if (clickSound.isPlaying) {
            clickSound.seekTo(0)  // Nếu âm thanh đang phát, đưa về đầu và phát lại
        } else {
            clickSound.start()
        }
    }

    // Reset lại trò chơi
    private fun resetGame() {
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                gameState[i][j] = ""  // Đặt lại trạng thái của ô
                board[i][j].setImageDrawable(null)  // Xóa hình ảnh trên ImageButton
            }
        }
        currentPlayer = "X"  // Bắt đầu lại với người chơi X
        lastMove = null  // Đặt lại lastMove để tránh ảnh hưởng từ ván trước
        isAiThinking = false // Đảm bảo AI không bị khóa sau khi reset
    }

    // Hàm đánh giá trạng thái bàn cờ
    private fun evaluateBoard(state: Array<Array<String>>, player: String): Int {
        var score = 0
        val opponent = if (player == "O") "X" else "O"

        // Đánh giá theo hàng, cột và đường chéo
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                // Kiểm tra các hướng
                score += evaluateDirection(state, i, j, 1, 0, player, opponent) // Ngang
                score += evaluateDirection(state, i, j, 0, 1, player, opponent) // Dọc
                score += evaluateDirection(state, i, j, 1, 1, player, opponent) // Chéo xuống
                score += evaluateDirection(state, i, j, 1, -1, player, opponent) // Chéo lên
            }
        }

        return score
    }

    // Kiểm tra điều kiện thắng
    private fun checkWin(row: Int, col: Int): Boolean {
        // Kiểm tra hàng, cột và đường chéo
        return checkDirection(row, col, 1, 0) || // Kiểm tra hàng
                checkDirection(row, col, 0, 1) || // Kiểm tra cột
                checkDirection(row, col, 1, 1) || // Kiểm tra đường chéo chính
                checkDirection(row, col, 1, -1)   // Kiểm tra đường chéo phụ
    }

    // Kiểm tra một hướng (hàng, cột, hoặc đường chéo)
    private fun checkDirection(row: Int, col: Int, dRow: Int, dCol: Int): Boolean {
        var count = 1

        // Kiểm tra một phía của đường thẳng
        var r = row + dRow
        var c = col + dCol
        while (r in 0 until gridSize && c in 0 until gridSize && gameState[r][c] == currentPlayer) {
            count++
            r += dRow
            c += dCol
            if (count >= 5) return true  // Nếu đã đủ 5 ô liên tiếp thì thắng
        }

        // Kiểm tra phía đối diện
        r = row - dRow
        c = col - dCol
        while (r in 0 until gridSize && c in 0 until gridSize && gameState[r][c] == currentPlayer) {
            count++
            r -= dRow
            c -= dCol
            if (count >= 5) return true  // Nếu đã đủ 5 ô liên tiếp thì thắng
        }

        return count >= 5  // Thắng khi có 5 ô liên tiếp
    }

    // Tìm nước đi tốt nhất cho AI
    private fun findBestMove(state: Array<Array<String>>): Pair<Int, Int>? {
        var bestVal = Int.MIN_VALUE
        var bestMove: Pair<Int, Int>? = null

        // Chỉ xét các ô xung quanh các nước đã đánh
        val validMoves = getValidMoves(state)

        for ((row, col) in validMoves) {
            if (state[row][col] == "") {
                state[row][col] = "O"
                val moveVal = minimax(state, 0, false, Int.MIN_VALUE, Int.MAX_VALUE)
                state[row][col] = ""

                if (moveVal > bestVal) {
                    bestVal = moveVal
                    bestMove = Pair(row, col)
                }
            }
        }
        return bestMove
    }

    // Minimax với alpha-beta pruning và độ sâu giới hạn
    private fun minimax(state: Array<Array<String>>, depth: Int, isMax: Boolean, alpha: Int, beta: Int): Int {
        if (isTimeUp) throw TimeoutException()

        // Kiểm tra cache
        val stateKey = getStateKey(state)
        transpositionTable[stateKey]?.let { return it }

        if (depth >= MAX_DEPTH) {
            val score = evaluateBoard(state, "O") - evaluateBoard(state, "X")
            transpositionTable[stateKey] = score
            return score
        }

        if (checkWinState(state, "O")) return 1000000 - depth
        if (checkWinState(state, "X")) return -1000000 + depth
        if (isBoardFullState(state)) return 0

        val validMoves = getValidMoves(state)

        var best = if (isMax) Int.MIN_VALUE else Int.MAX_VALUE
        for ((row, col) in validMoves) {
            if (state[row][col] == "") {
                state[row][col] = if (isMax) "O" else "X"
                val score = minimax(state, depth + 1, !isMax, alpha, beta)
                state[row][col] = ""

                if (isMax) {
                    best = maxOf(best, score)
                    if (best >= beta) break
                } else {
                    best = minOf(best, score)
                    if (best <= alpha) break
                }
            }
        }

        transpositionTable[stateKey] = best
        return best
    }

    // Kiểm tra xem bàn cờ đã đầy chưa (hoà)
    private fun isBoardFull(): Boolean {
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                if (gameState[i][j] == "") {
                    return false
                }
            }
        }
        return true
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun makeMove(row: Int, col: Int) {
        // Cập nhật trạng thái trò chơi
        gameState[row][col] = "O"

        // Đặt hình ảnh cho ImageButton
        val oImage = resources.getDrawable(R.drawable.o, null)  // Thay R.drawable.o_image bằng tài nguyên hình ảnh "O"
        board[row][col].setImageDrawable(oImage)

        lastMove = Pair(row, col)

        // Kiểm tra người chơi thắng
        if (checkWin(row, col)) {
            Toast.makeText(baseContext, "AI thắng!", Toast.LENGTH_LONG).show()
        } else if (isBoardFull()) {
            Toast.makeText(baseContext, "Ván này hòa!", Toast.LENGTH_LONG).show()
        } else {
            // Đổi người chơi
            currentPlayer = "X"
        }
    }

    // Tìm nước đi dự phòng khi hết thời gian
    private fun findFallbackMove(): Pair<Int, Int>? {
        var bestScore = Int.MIN_VALUE
        var bestMove: Pair<Int, Int>? = null

        // Chỉ đánh giá một lớp
        val validMoves = getValidMoves(gameState)
        for ((row, col) in validMoves) {
            if (gameState[row][col] == "") {
                val score = quickEvaluate(gameState, row, col)
                if (score > bestScore) {
                    bestScore = score
                    bestMove = Pair(row, col)
                }
            }
        }
        return bestMove
    }

    // Đánh giá nhanh một nước đi
    private fun quickEvaluate(state: Array<Array<String>>, row: Int, col: Int): Int {
        // Kiểm tra nếu nước này thắng ngay
        state[row][col] = "O"
        if (checkWin(row, col)) {
            state[row][col] = ""
            return 1000000
        }
        state[row][col] = ""

        var score = 0

        // Kiểm tra phòng thủ
        state[row][col] = "X"
        if (checkWin(row, col)) {
            score += 500000 // Ưu tiên chặn nước thắng của đối thủ
        }
        state[row][col] = ""

        // Đánh giá vị trí
        score += evaluatePosition(state, row, col)

        return score
    }

    // Phương thức gọi Minimax để AI chọn nước đi với timeout
    private fun aiMove() {
        if (isAiThinking) return
        isAiThinking = true

        coroutineScope.launch(Dispatchers.Default) {
            try {
                isTimeUp = false
                transpositionTable.clear() // Xóa cache cũ

                // Tạo job con với timeout
                withTimeout(moveTimeLimit) {
                    val bestMove = findBestMove(gameState)

                    withContext(Dispatchers.Main) {
                        bestMove?.let { (row, col) ->
                            makeMove(row, col)
                        } ?: findFallbackMove()?.let { (row, col) ->
                            makeMove(row, col)
                        }
                    }
                }
            } catch (e: Exception) {
                // Nếu timeout, tìm nước đi dự phòng
                withContext(Dispatchers.Main) {
                    findFallbackMove()?.let { (row, col) ->
                        makeMove(row, col)
                    }
                }
            } finally {
                isAiThinking = false
            }
        }
    }

    // Kiểm tra xem có quân cờ lân cận không
    private fun hasAdjacentPieces(state: Array<Array<String>>, row: Int, col: Int): Boolean {
        for (i in -1..1) {
            for (j in -1..1) {
                val newRow = row + i
                val newCol = col + j
                if (newRow in 0 until gridSize &&
                    newCol in 0 until gridSize &&
                    state[newRow][newCol] != "") {
                    return true
                }
            }
        }
        return false
    }

    // Lấy danh sách các ô có thể đánh
    private fun getValidMoves(state: Array<Array<String>>): List<Pair<Int, Int>> {
        val moves = mutableListOf<Pair<Int, Int>>()
        val checked = Array(gridSize) { Array(gridSize) { false } }

        // Nếu có nước đi trước đó, ưu tiên khu vực xung quanh
        lastMove?.let { (lastRow, lastCol) ->
            val startRow = maxOf(0, lastRow - searchRadius)
            val endRow = minOf(gridSize - 1, lastRow + searchRadius)
            val startCol = maxOf(0, lastCol - searchRadius)
            val endCol = minOf(gridSize - 1, lastCol + searchRadius)

            for (i in startRow..endRow) {
                for (j in startCol..endCol) {
                    if (state[i][j] == "" && !checked[i][j]) {
                        moves.add(Pair(i, j))
                        checked[i][j] = true
                    }
                }
            }

            // Nếu không đủ nước đi, mở rộng tìm kiếm
            if (moves.isEmpty()) {
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        if (state[i][j] == "" && !checked[i][j] && hasAdjacentPieces(state, i, j)) {
                            moves.add(Pair(i, j))
                            checked[i][j] = true
                        }
                    }
                }
            }
        } ?: run {
            // Nước đi đầu tiên, chọn khu vực trung tâm
            val center = gridSize / 2
            moves.add(Pair(center, center))
        }

        return moves.sortedByDescending { (row, col) -> evaluatePosition(state, row, col) }
    }

    // Đánh giá nhanh vị trí để sắp xếp nước đi
    private fun evaluatePosition(state: Array<Array<String>>, row: Int, col: Int): Int {
        var score = 0

        // Ưu tiên vị trí gần với nước đi gần nhất
        lastMove?.let { (lastRow, lastCol) ->
            val distance = kotlin.math.abs(row - lastRow) + kotlin.math.abs(col - lastCol)
            score -= distance * 2
        }

        // Ưu tiên vị trí có nhiều quân xung quanh
        for (i in -1..1) {
            for (j in -1..1) {
                val newRow = row + i
                val newCol = col + j
                if (newRow in 0 until gridSize && newCol in 0 until gridSize) {
                    when (state[newRow][newCol]) {
                        "O" -> score += 3
                        "X" -> score += 2
                    }
                }
            }
        }

        return score
    }

    // Đánh giá một hướng cụ thể
    private fun evaluateDirection(
        state: Array<Array<String>>,
        startRow: Int,
        startCol: Int,
        dRow: Int,
        dCol: Int,
        player: String,
        opponent: String
    ): Int {
        var score = 0
        var count = 0
        var blocked = false
        var openEnds = 2

        // Kiểm tra điểm bắt đầu
        if (startRow - dRow in 0 until gridSize &&
            startCol - dCol in 0 until gridSize &&
            state[startRow - dRow][startCol - dCol] == opponent) {
            openEnds--  // Đối thủ chặn một đầu
        }

        // Đếm số quân liên tiếp
        var row = startRow
        var col = startCol
        while (row in 0 until gridSize && col in 0 until gridSize) {
            when (state[row][col]) {
                player -> count++  // Đếm quân của player
                "" -> break  // Dừng khi gặp ô trống
                else -> {
                    blocked = true
                    openEnds--  // Bị chặn bởi đối thủ
                    break
                }
            }
            row += dRow
            col += dCol
        }

        // Kiểm tra phía ngược lại
        row = startRow - dRow
        col = startCol - dCol
        while (row in 0 until gridSize && col in 0 until gridSize) {
            when (state[row][col]) {
                player -> count++  // Đếm quân của player
                "" -> break  // Dừng khi gặp ô trống
                else -> {
                    blocked = true
                    openEnds--  // Bị chặn bởi đối thủ
                    break
                }
            }
            row -= dRow
            col -= dCol
        }

        // Tính điểm dựa trên số quân và trạng thái (bị chặn/không bị chặn)
        when {
            count >= 5 -> score += FIVE_IN_ROW
            count == 4 -> {
                if (openEnds == 2) score += OPEN_FOUR // Cả 2 đầu trống
                else if (openEnds == 1) score += FOUR_IN_ROW // Một đầu trống
            }
            count == 3 -> {
                if (openEnds == 2) score += OPEN_THREE // Cả 2 đầu trống
                else if (openEnds == 1) score += THREE_IN_ROW // Một đầu trống
            }
            count == 2 -> {
                if (openEnds == 2) score += OPEN_TWO // Cả 2 đầu trống
                else if (openEnds == 1) score += TWO_IN_ROW // Một đầu trống
            }
        }

        // Tính điểm cho tình trạng bị chặn (blocked)
        if (blocked) {
            score /= 2  // Nếu bị chặn thì điểm thấp hơn
        }

        return score
    }

    // Tạo key cho trạng thái bàn cờ
    private fun getStateKey(state: Array<Array<String>>): String {
        return state.joinToString("") { row -> row.joinToString("") }
    }

    // Hàm kiểm tra nếu AI hoặc người chơi X thắng
    private fun checkWinState(state: Array<Array<String>>, player: String): Boolean {
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                if (state[i][j] == player && checkWin(i, j)) {
                    return true
                }
            }
        }
        return false
    }

    // Hàm kiểm tra xem bàn cờ đã đầy chưa
    private fun isBoardFullState(state: Array<Array<String>>): Boolean {
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                if (state[i][j] == "") {
                    return false
                }
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        // Khởi tạo lại âm thanh khi Activity được phục hồi
        initializeSounds()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel() // Hủy coroutine khi activity bị destroy
    }
}
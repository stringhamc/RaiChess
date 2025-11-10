# RaiChess (来Chess) - Offline Android Chess App

## Executive Summary

**RaiChess (来Chess)** - "Rai" (来) means "next" in Japanese, representing the next evolution in chess training. It also sounds like "Righteous!" - that awesome 80s exclamation of excellence!

A fully offline Android chess application featuring strong AI opponents, comprehensive game recording, position analysis, and targeted practice from weak points in previous games.

---

## 1. Core Technology Stack

### Chess Engine
**Stockfish** - The best free, open-source chess engine
- **Why Stockfish:** Rated 3500+ ELO, fully offline, open source
- **Implementation:** Use Stockfish 16 (latest stable)
- **Integration:** Stockfish Android NDK library or DroidFish's implementation
- **Adjustable strength:** Configure UCI options to provide beginner to expert levels

### Android Development
- **Language:** Kotlin (modern, recommended for Android)
- **Minimum SDK:** API 24 (Android 7.0) - covers 95%+ devices
- **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture
- **UI Framework:** Jetpack Compose for modern, reactive UI
- **UI Theme:** Minimal black and white (OLED-friendly, power efficient)
  - Pure black background (#000000) for OLED power savings
  - White text and UI elements (#FFFFFF)
  - High contrast for readability
  - No colors, gradients, or unnecessary visual effects

### Database
**Room Database** (SQLite wrapper)
- Fully offline
- Type-safe queries
- Built-in migration support
- Perfect for storing games and positions

### Libraries & Dependencies
```kotlin
// Chess engine
implementation 'com.github.bhlangonijr:chesslib:1.3.3' // PGN parsing, move validation

// Database
implementation 'androidx.room:room-runtime:2.6.0'
kapt 'androidx.room:room-compiler:2.6.0'
implementation 'androidx.room:room-ktx:2.6.0'

// UI
implementation 'androidx.compose.ui:ui:1.5.4'
implementation 'androidx.compose.material3:material3:1.1.2'

// Coroutines for async operations
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

---

## 2. Architecture Design

### Layer Structure

```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│  (Jetpack Compose UI + ViewModels)  │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│          Domain Layer               │
│    (Use Cases + Business Logic)     │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│           Data Layer                │
│  (Repository + Room DB + Stockfish) │
└─────────────────────────────────────┘
```

### Key Components

**1. Chess Engine Manager**
- Manages Stockfish process/native library
- Handles UCI protocol communication
- Provides move suggestions and position evaluation
- Configurable strength levels (1-20 for different skill levels)

**2. Game Manager**
- Maintains current game state
- Validates moves
- Detects check, checkmate, stalemate
- Generates legal moves
- Handles PGN import/export

**3. Database Schema**
```sql
-- User Profile Table
CREATE TABLE user_profile (
    id INTEGER PRIMARY KEY,
    current_elo INTEGER DEFAULT 1200,
    peak_elo INTEGER DEFAULT 1200,
    games_played INTEGER DEFAULT 0,
    wins INTEGER DEFAULT 0,
    losses INTEGER DEFAULT 0,
    draws INTEGER DEFAULT 0,
    last_updated INTEGER
);

-- ELO History Table (for tracking progress)
CREATE TABLE elo_history (
    id INTEGER PRIMARY KEY,
    game_id INTEGER,
    elo_before INTEGER NOT NULL,
    elo_after INTEGER NOT NULL,
    elo_change INTEGER NOT NULL,
    opponent_elo INTEGER NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (game_id) REFERENCES games(id)
);

-- Games Table
CREATE TABLE games (
    id INTEGER PRIMARY KEY,
    pgn TEXT NOT NULL,
    date_played INTEGER NOT NULL,
    result TEXT, -- "1-0", "0-1", "1/2-1/2"
    player_color TEXT, -- "white" or "black"
    opponent_elo INTEGER NOT NULL, -- AI's ELO for this game
    player_elo_before INTEGER NOT NULL,
    player_elo_after INTEGER NOT NULL,
    accuracy REAL, -- Player's accuracy percentage
    time_control TEXT,
    opening_name TEXT,
    created_at INTEGER
);

-- Positions Table (for analysis)
CREATE TABLE positions (
    id INTEGER PRIMARY KEY,
    game_id INTEGER,
    move_number INTEGER,
    fen TEXT NOT NULL,
    evaluation REAL, -- Stockfish evaluation in centipawns
    best_move TEXT,
    player_move TEXT,
    is_mistake BOOLEAN,
    is_blunder BOOLEAN,
    FOREIGN KEY (game_id) REFERENCES games(id)
);

-- Practice Positions Table
CREATE TABLE practice_positions (
    id INTEGER PRIMARY KEY,
    source_game_id INTEGER,
    source_move_number INTEGER,
    fen TEXT NOT NULL,
    category TEXT, -- "tactics", "endgame", "opening", "mistake_correction"
    difficulty INTEGER,
    times_practiced INTEGER DEFAULT 0,
    success_rate REAL DEFAULT 0.0,
    last_practiced INTEGER,
    FOREIGN KEY (source_game_id) REFERENCES games(id)
);

-- Opening Repertoire
CREATE TABLE openings (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    eco_code TEXT, -- ECO classification
    fen TEXT NOT NULL,
    move_sequence TEXT, -- PGN moves to reach position
    times_played INTEGER DEFAULT 0,
    win_rate REAL DEFAULT 0.0,
    avg_accuracy REAL DEFAULT 0.0
);
```

**4. Analysis Engine**
- Post-game analysis using Stockfish
- Identifies mistakes, blunders, good moves
- Calculates accuracy percentage
- Suggests improvements
- Extracts tactical opportunities

**5. Practice Generator**
- Creates training positions from weak points
- Categorizes by skill area (tactics, endgames, openings)
- Adaptive difficulty based on performance
- Spaced repetition for failed positions

---

## 3. Feature Implementation Plan

### Phase 1: Core Chess Functionality (Weeks 1-3)

**Week 1: Board & Movement**
- Chessboard UI component (Jetpack Compose)
- Piece rendering (SVG or PNG assets)
- Drag-and-drop or tap-to-move input
- Move validation
- Legal move highlighting

**Week 2: Game Logic**
- Complete chess rules implementation
- Check/checkmate/stalemate detection
- Castling, en passant, promotion
- Move history display
- Undo/redo functionality

**Week 3: Stockfish Integration**
- Integrate Stockfish native library
- UCI protocol implementation
- AI move generation
- Difficulty level adjustment
- Position evaluation display

### Phase 2: Database & Game Recording (Weeks 4-5)

**Week 4: Room Database Setup**
- Database schema implementation
- DAO (Data Access Objects) creation
- Repository pattern
- Game saving/loading
- PGN export functionality

**Week 5: Game History**
- Game list UI (filter, sort, search)
- Game replay viewer
- Move-by-move navigation
- Position search within games
- Delete/archive games

### Phase 3: Analysis Features (Weeks 6-7)

**Week 6: Post-Game Analysis**
- Automated game analysis with Stockfish
- Move classification (brilliant, good, inaccuracy, mistake, blunder)
- Accuracy calculation
- Critical position identification
- Alternative move suggestions

**Week 7: Analysis UI**
- Analysis report screen
- Move annotations
- Evaluation graph (position over time)
- Best move vs played move comparison
- Opening recognition and stats

### Phase 4: Practice & Training (Weeks 8-9)

**Week 8: Practice Position Generation**
- Extract tactical positions from games
- Identify mistake patterns
- Categorize by theme (pins, forks, checkmates, endgames)
- Create position database from user's games
- Difficulty rating algorithm

**Week 9: Practice Mode**
- Practice session UI
- Position puzzle interface
- Hint system (progressive hints)
- Solution showing
- Performance tracking
- Spaced repetition scheduling

### Phase 5: Polish & Advanced Features (Weeks 10-12)

**Week 10: User Experience**
- Settings (board themes, piece sets, sounds)
- Animation polish
- Sound effects (move, capture, check)
- Dark/light mode
- Tutorial/onboarding

**Week 11: Statistics & Insights**
- Player statistics dashboard
- Win/loss/draw rates
- Performance by opening
- Common mistake patterns
- Progress over time graphs
- Heatmaps (where pieces captured, weak squares)

**Week 12: Additional Features**
- Clock/time controls
- Position setup (FEN editor)
- Import games from PGN files
- Cloud backup option (optional online feature)
- Export analysis reports

---

## 4. Detailed Feature Specifications

### A. AI Opponent System

**ELO-Based Difficulty System:**

The app will estimate the user's ELO rating and adjust Stockfish accordingly. Stockfish will be configured to play at specific ELO strengths from 800-2800+.

**ELO Range Configuration:**
- **800-1000:** Beginner - Stockfish limited depth 1, Skill Level 0-2
- **1000-1200:** Novice - Depth 1-2, Skill Level 3-5
- **1200-1400:** Intermediate - Depth 3-5, Skill Level 6-8
- **1400-1600:** Club Player - Depth 5-8, Skill Level 9-11
- **1600-1800:** Advanced - Depth 8-10, Skill Level 12-14
- **1800-2000:** Expert - Depth 10-12, Skill Level 15-17
- **2000-2200:** Master - Depth 12-15, Skill Level 18-19
- **2200-2400:** International Master - Depth 15-18, Skill Level 20
- **2400-2600:** Grandmaster - Depth 18-20, Skill Level 20
- **2600+:** Super GM - Full strength Stockfish, Depth 20+, Skill Level 20

**Stockfish Configuration by ELO:**
```kotlin
data class EloConfiguration(
    val targetElo: Int,
    val depth: Int,
    val skillLevel: Int, // UCI_LimitStrength + UCI_Elo
    val thinkingTime: Long // milliseconds
) {
    companion object {
        fun forElo(elo: Int): EloConfiguration {
            return when {
                elo < 1000 -> EloConfiguration(elo, 1, elo / 100, 500)
                elo < 1400 -> EloConfiguration(elo, 3, elo / 100, 1000)
                elo < 1800 -> EloConfiguration(elo, 8, elo / 100, 2000)
                elo < 2200 -> EloConfiguration(elo, 12, 18, 3000)
                elo < 2600 -> EloConfiguration(elo, 18, 20, 5000)
                else -> EloConfiguration(elo, 20, 20, 10000)
            }
        }
    }
}

// UCI commands to set ELO
fun setStockfishElo(targetElo: Int) {
    val config = EloConfiguration.forElo(targetElo)
    sendCommand("setoption name UCI_LimitStrength value true")
    sendCommand("setoption name UCI_Elo value ${config.targetElo}")
    sendCommand("setoption name Skill Level value ${config.skillLevel}")
}
```

**User ELO Estimation:**

Initial ELO: 1200 (default for new players)

The app will estimate the user's ELO using a modified Elo rating system:

```kotlin
class EloCalculator {
    companion object {
        const val K_FACTOR = 32 // Standard chess K-factor
        
        fun calculateNewElo(
            currentElo: Int,
            opponentElo: Int,
            result: GameResult, // WIN, LOSS, DRAW
            moveAccuracy: Double // 0.0-100.0
        ): Int {
            // Expected score formula
            val expectedScore = 1.0 / (1.0 + Math.pow(10.0, (opponentElo - currentElo) / 400.0))
            
            // Actual score
            val actualScore = when (result) {
                GameResult.WIN -> 1.0
                GameResult.DRAW -> 0.5
                GameResult.LOSS -> 0.0
            }
            
            // Accuracy modifier (rewards good play even in losses)
            val accuracyBonus = (moveAccuracy - 50) / 100.0 // -0.5 to +0.5
            val adjustedActualScore = (actualScore + accuracyBonus * 0.3).coerceIn(0.0, 1.0)
            
            // Calculate new ELO
            val change = (K_FACTOR * (adjustedActualScore - expectedScore)).toInt()
            return (currentElo + change).coerceIn(400, 3000)
        }
    }
}
```

**ELO Display:**
- Prominently displayed on home screen
- Updated after each game
- Shows ±change after game completion
- Confidence interval shown for first 20 games
- Separate ELO tracking for different time controls (optional)

### B. Game Analysis Algorithm

**Step-by-Step Process:**

1. **Load game from database**
2. **For each position in the game:**
   - Get Stockfish evaluation of current position
   - Get best move from Stockfish
   - Compare with player's actual move
   - Calculate centipawn loss
   - Classify move quality:
     - **Brilliant:** Best move in complex position
     - **Good:** Within 0.3 pawn advantage of best
     - **Inaccuracy:** 0.3-1.0 pawn loss
     - **Mistake:** 1.0-3.0 pawn loss
     - **Blunder:** 3.0+ pawn loss

3. **Calculate overall metrics:**
   - Average centipawn loss (ACPL)
   - Accuracy percentage: `max(0, 100 - ACPL)`
   - Move quality distribution

4. **Identify critical moments:**
   - Positions where evaluation swung significantly
   - Missed tactical opportunities
   - Time pressure mistakes (if timed)

**Example Accuracy Calculation:**
```kotlin
fun calculateAccuracy(centipawnLoss: Double): Double {
    return max(0.0, 100.0 - (centipawnLoss * 2.5))
}
```

### C. Practice Position Generation

**Extraction Criteria:**

1. **Tactical Positions:**
   - Evaluation swing > 3.0 pawns available
   - Player missed the best move
   - Contains tactical motifs (pin, fork, skewer, discovered attack)
   - Depth: 2-5 moves to solution

2. **Endgame Practice:**
   - <= 7 pieces on board
   - Clear winning/drawing technique available
   - Player made mistake in similar position before

3. **Opening Training:**
   - Positions within first 15 moves
   - Common positions in player's repertoire
   - Deviations from theory that led to disadvantage

4. **Mistake Correction:**
   - Exact positions where player blundered
   - Similar pawn structures where mistakes occurred
   - Recurring pattern mistakes

**Categorization & Difficulty:**
```kotlin
data class PracticePosition(
    val fen: String,
    val category: PracticeCategory,
    val difficulty: Int, // 1-10 based on Stockfish depth needed
    val theme: TacticalTheme?,
    val sourceGameId: Long,
    val movesToSolution: Int
)

enum class PracticeCategory {
    TACTICS,
    ENDGAME,
    OPENING,
    MISTAKE_CORRECTION
}

enum class TacticalTheme {
    PIN, FORK, SKEWER, DISCOVERED_ATTACK,
    BACK_RANK, DEFLECTION, DECOY,
    CHECKMATE_PATTERN, PAWN_BREAKTHROUGH
}
```

### D. Spaced Repetition System

**Algorithm:**
- New positions: Show after 1 day
- Correct answer: Multiply interval by 2.5
- Incorrect answer: Reset interval to 1 day
- Maximum interval: 90 days

```kotlin
fun calculateNextReview(
    currentInterval: Int,
    wasCorrect: Boolean,
    difficulty: Int
): Int {
    return if (wasCorrect) {
        min(currentInterval * 2.5, 90)
    } else {
        1
    }
}
```

---

## 5. UI/UX Design - Minimal Black & White Theme

### Design Philosophy
- **Power Efficiency:** Pure black (#000000) background for OLED screens saves battery
- **High Contrast:** White (#FFFFFF) elements on black background for maximum readability
- **Minimal Elements:** No decorations, gradients, or unnecessary UI chrome
- **Focus on Content:** Chess board and essential information only

### Color Palette
```kotlin
object ChessColors {
    val Background = Color(0xFF000000)        // Pure black
    val Surface = Color(0xFF000000)           // Pure black
    val OnBackground = Color(0xFFFFFFFF)      // Pure white
    val OnSurface = Color(0xFFFFFFFF)         // Pure white
    
    // Board colors (grayscale)
    val LightSquare = Color(0xFFFFFFFF)       // White squares
    val DarkSquare = Color(0xFF000000)        // Black squares
    val SquareBorder = Color(0xFFFFFFFF)      // Thin white borders
    
    // Pieces (use Unicode chess symbols or simple SVG)
    val WhitePiece = Color(0xFFFFFFFF)        // White pieces
    val BlackPiece = Color(0xFF000000)        // Black pieces with white outline
    
    // UI accents (minimal use)
    val SelectedSquare = Color(0xFF666666)    // Gray for selection
    val LegalMoveIndicator = Color(0xFF888888) // Lighter gray for legal moves
}
```

### Screen Layout

**1. Main Menu**
```
┌──────────────────────────────┐
│                              │
│      Your ELO: 1547          │
│       (±23)                  │
│                              │
│   ┌──────────────────────┐   │
│   │    NEW GAME          │   │
│   └──────────────────────┘   │
│                              │
│   ┌──────────────────────┐   │
│   │    PRACTICE          │   │
│   └──────────────────────┘   │
│                              │
│   ┌──────────────────────┐   │
│   │    HISTORY           │   │
│   └──────────────────────┘   │
│                              │
│   ┌──────────────────────┐   │
│   │    STATISTICS        │   │
│   └──────────────────────┘   │
│                              │
└──────────────────────────────┘
```

**2. Game Screen**
```
┌──────────────────────────────┐
│ Stockfish 2100  You 1547     │
├──────────────────────────────┤
│ ┌────────────────────────┐   │
│ │                        │   │
│ │   8x8 CHESSBOARD       │   │
│ │   White squares: ▢     │   │
│ │   Black squares: ■     │   │
│ │   Simple pieces        │   │
│ │                        │   │
│ └────────────────────────┘   │
├──────────────────────────────┤
│ Move 15  Eval: +0.3          │
├──────────────────────────────┤
│ 1. e4 e5                     │
│ 2. Nf3 Nc6                   │
│ 3. Bb5 ...                   │
│                              │
│ [<] [?] [≡]                  │
└──────────────────────────────┘
```

**3. Game Setup Screen**
```
┌──────────────────────────────┐
│       NEW GAME               │
├──────────────────────────────┤
│                              │
│  Your ELO: 1547              │
│                              │
│  Opponent ELO:               │
│  [────•────] 1600            │
│   800         2800           │
│                              │
│  Play as:                    │
│  ( ) White  (•) Random       │
│  ( ) Black                   │
│                              │
│  ┌──────────────────────┐    │
│  │    START GAME        │    │
│  └──────────────────────┘    │
│                              │
└──────────────────────────────┘
```

**4. Analysis Screen**
```
┌──────────────────────────────┐
│  GAME ANALYSIS               │
├──────────────────────────────┤
│                              │
│  Result: Win (+24 ELO)       │
│  Accuracy: 87.3%             │
│  Opponent: 1623              │
│                              │
│  Mistakes: 2                 │
│  Blunders: 0                 │
│  Good moves: 31              │
│                              │
│  ▁▂▃▅▄▃▅▆▇█▇▆▅▄▃▂▁          │
│  Eval graph (text-based)     │
│                              │
│  Critical moments: 3         │
│  Practice positions: 5       │
│                              │
│  [VIEW MOVES] [PRACTICE]     │
│                              │
└──────────────────────────────┘
```

**5. Practice Screen**
```
┌──────────────────────────────┐
│  TACTICAL PRACTICE           │
├──────────────────────────────┤
│                              │
│  Find the best move          │
│  Difficulty: ★★★☆☆           │
│                              │
│ ┌────────────────────────┐   │
│ │                        │   │
│ │     POSITION BOARD     │   │
│ │                        │   │
│ └────────────────────────┘   │
│                              │
│  White to move               │
│                              │
│  [HINT] [SUBMIT]             │
│                              │
│  Streak: 7  Today: 12/15     │
│                              │
└──────────────────────────────┘
```

### Typography
```kotlin
object ChessTypography {
    val displayLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White
    )
    val titleLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White
    )
    val bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White
    )
    val labelMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White
    )
}
```

### Chess Pieces Rendering

**Option 1: Unicode Chess Symbols** (Most power efficient)
```kotlin
val chessPieces = mapOf(
    'K' to "♔", 'Q' to "♕", 'R' to "♖", 
    'B' to "♗", 'N' to "♘", 'P' to "♙",
    'k' to "♚", 'q' to "♛", 'r' to "♜",
    'b' to "♝", 'n' to "♞", 'p' to "♟"
)
// White pieces: white fill
// Black pieces: black fill with white outline
```

**Option 2: Minimal SVG** (If Unicode not clear enough)
- Simple geometric shapes
- Black and white only
- No shadows or gradients

### Interaction Design
- **Tap to select:** Piece glows with gray selection
- **Legal moves:** Small gray dots on legal squares
- **Move animation:** Instant (no fade/slide to save power)
- **Sound:** Optional clicks (disabled by default for power)

### Power Saving Features
```kotlin
// Dim brightness recommendation
WindowCompat.setDecorFitsSystemWindows(window, false)
window.attributes = window.attributes.apply {
    screenBrightness = 0.3f // Suggest 30% brightness
}

// Keep screen on only during active game
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

// Remove after game ends
window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

---

## 6. Technical Implementation Details

### Stockfish Integration Methods

**Option A: Native Library (Recommended)**
```kotlin
class StockfishEngine(context: Context) {
    private external fun init(): Long
    private external fun sendCommand(command: String)
    private external fun getOutput(): String
    
    companion object {
        init {
            System.loadLibrary("stockfish")
        }
    }
}
```

**Option B: Process-Based**
- Copy Stockfish binary to app's files directory
- Launch as separate process
- Communicate via stdin/stdout
- More portable but slightly slower

### Move Validation Strategy

Use **chesslib** for move generation and validation:
```kotlin
val board = Board()
board.doMove(Move("e2e4", Side.WHITE))

// Generate all legal moves
val legalMoves = MoveGenerator.generateLegalMoves(board)

// Validate move
fun isMoveLegal(from: Square, to: Square): Boolean {
    return legalMoves.any { 
        it.from == from && it.to == to 
    }
}
```

### Performance Optimizations

1. **UI Thread:**
   - Board rendering only
   - User input handling

2. **Background Thread:**
   - Stockfish calculations
   - Database operations
   - Game analysis

3. **Caching:**
   - Opening book for instant responses
   - Previously analyzed positions
   - Common tactical patterns

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel : ViewModel() {
    private val ioDispatcher = Dispatchers.IO
    
    fun analyzeGame(gameId: Long) = viewModelScope.launch(ioDispatcher) {
        val game = repository.getGame(gameId)
        val analysis = analyzeWithStockfish(game)
        withContext(Dispatchers.Main) {
            _analysisState.value = analysis
        }
    }
}
```

---

## 7. Testing Strategy

### Unit Tests
- Move validation logic
- Game rules (check, checkmate, stalemate)
- Analysis algorithms
- Database operations

### Integration Tests
- Stockfish communication
- Game save/load cycle
- Analysis pipeline
- Practice position generation

### UI Tests
- Board interaction
- Game flow
- Navigation

### Performance Tests
- Stockfish response time at each difficulty
- Database query speed with 1000+ games
- UI rendering at 60fps

---

## 8. Data Flow Examples

### Example 1: Playing a Move

```
User taps piece → UI validates tap
                ↓
UI highlights legal moves
                ↓
User taps destination → GameManager validates move
                ↓
Move is legal → Update board state
                ↓
Save position to memory → Request AI move
                ↓
Stockfish calculates → Returns best move
                ↓
Apply AI move → Update UI
                ↓
Check game status → Continue/End game
```

### Example 2: Analyzing a Completed Game

```
User selects game from history
                ↓
Load PGN from database → Parse into positions
                ↓
For each position:
  ↓
  Send FEN to Stockfish → Get evaluation
  ↓
  Get best move → Compare with played move
  ↓
  Calculate quality → Store analysis
                ↓
Generate report → Display to user
                ↓
Extract practice positions → Save to database
```

---

## 9. File Structure

```
app/
├── src/main/
│   ├── java/com/yourapp/chess/
│   │   ├── data/
│   │   │   ├── database/
│   │   │   │   ├── ChessDatabase.kt
│   │   │   │   ├── dao/
│   │   │   │   │   ├── GameDao.kt
│   │   │   │   │   ├── PositionDao.kt
│   │   │   │   │   └── PracticeDao.kt
│   │   │   │   └── entities/
│   │   │   │       ├── GameEntity.kt
│   │   │   │       ├── PositionEntity.kt
│   │   │   │       └── PracticePositionEntity.kt
│   │   │   ├── repository/
│   │   │   │   ├── GameRepository.kt
│   │   │   │   ├── AnalysisRepository.kt
│   │   │   │   └── PracticeRepository.kt
│   │   │   └── engine/
│   │   │       └── StockfishEngine.kt
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Game.kt
│   │   │   │   ├── Position.kt
│   │   │   │   ├── Move.kt
│   │   │   │   └── Analysis.kt
│   │   │   └── usecase/
│   │   │       ├── PlayMoveUseCase.kt
│   │   │       ├── AnalyzeGameUseCase.kt
│   │   │       ├── GeneratePracticeUseCase.kt
│   │   │       └── GetAIMoveUseCase.kt
│   │   └── ui/
│   │       ├── game/
│   │       │   ├── GameScreen.kt
│   │       │   ├── GameViewModel.kt
│   │       │   └── components/
│   │       │       ├── ChessBoard.kt
│   │       │       ├── MoveList.kt
│   │       │       └── GameControls.kt
│   │       ├── analysis/
│   │       │   ├── AnalysisScreen.kt
│   │       │   ├── AnalysisViewModel.kt
│   │       │   └── components/
│   │       │       ├── EvaluationGraph.kt
│   │       │       └── MoveAnnotations.kt
│   │       ├── practice/
│   │       │   ├── PracticeScreen.kt
│   │       │   ├── PracticeViewModel.kt
│   │       │   └── components/
│   │       │       └── PuzzlePosition.kt
│   │       └── history/
│   │           ├── HistoryScreen.kt
│   │           └── HistoryViewModel.kt
│   ├── cpp/ (for Stockfish native library)
│   │   └── stockfish/
│   └── res/
│       ├── drawable/ (piece images, board themes)
│       └── values/ (strings, colors, themes)
└── build.gradle
```

---

## 10. Development Timeline

### Minimal Viable Product (MVP) - 6 Weeks
- Weeks 1-3: Core chess functionality + Stockfish
- Week 4: Basic database and game saving
- Week 5: Simple post-game analysis
- Week 6: Basic practice mode from mistakes

### Full Version - 12 Weeks
- Complete all phases as outlined above
- Polished UI/UX
- Comprehensive testing
- Performance optimization

---

## 11. Potential Challenges & Solutions

### Challenge 1: Stockfish Performance on Lower-End Devices
**Solution:**
- Adaptive depth based on device capabilities
- Detect CPU cores and adjust threads
- Longer thinking time on weak devices
- Precomputed opening book for instant moves

### Challenge 2: Battery Consumption
**Solution:**
- Limit Stockfish analysis time
- Pause engine when app in background
- Option to analyze on charging only
- Use hardware acceleration if available

### Challenge 3: Database Growth
**Solution:**
- Implement data archiving
- Compress old game PGNs
- Limit position storage to significant moves
- User-controlled retention period

### Challenge 4: Pattern Recognition in Practice
**Solution:**
- Use Stockfish's evaluation features
- Implement simple heuristics (piece on square patterns)
- Community tactical pattern database (offline)
- Machine learning model for pattern detection (future)

---

## 12. Future Enhancements

1. **Cloud Sync** (optional online mode)
   - Sync games across devices
   - Backup to cloud storage

2. **Opening Book**
   - Built-in opening database
   - Personalized repertoire builder
   - Opening statistics

3. **Puzzle Collection**
   - Curated tactical puzzles
   - Daily puzzle challenge
   - Rating system

4. **Social Features** (optional)
   - Share games
   - Challenge friends locally
   - Compare statistics

5. **Advanced Analysis**
   - Multi-line variations
   - Opening preparation
   - Opponent modeling (AI personality)

6. **Accessibility**
   - Screen reader support
   - Alternative input methods
   - Colorblind-friendly themes

---

## 13. Key Success Metrics

- **Performance:** AI response < 1 second on medium difficulty
- **Storage:** < 100KB per game with full analysis
- **Battery:** < 5% per hour during active play
- **Size:** App < 50MB (including Stockfish)
- **Crashes:** < 0.1% crash rate
- **User Engagement:** Practice completion rate > 60%

---

## Conclusion

This architecture provides a robust, offline-first Android chess application with professional-grade AI, comprehensive game analysis, and intelligent practice features. The modular design allows for incremental development and easy maintenance, while Stockfish provides world-class chess engine capabilities completely offline.

The combination of Room database for efficient storage, Jetpack Compose for modern UI, and clean architecture principles ensures the app will be maintainable and scalable for future enhancements.

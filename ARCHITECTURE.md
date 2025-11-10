# RaiChess (来Chess) Architecture

This document describes the architecture and design decisions for RaiChess - The Next Chess App.

**Rai (来)** = "Next" in Japanese | Representing the next evolution in chess training

## Table of Contents

1. [Overview](#overview)
2. [Architecture Layers](#architecture-layers)
3. [Key Components](#key-components)
4. [Data Flow](#data-flow)
5. [Design Patterns](#design-patterns)
6. [Technology Choices](#technology-choices)

---

## Overview

RaiChess uses **Clean Architecture** principles combined with **MVVM** pattern for a maintainable, testable, and scalable codebase.

### Core Principles

- **Separation of Concerns:** Each layer has a distinct responsibility
- **Dependency Inversion:** High-level modules don't depend on low-level modules
- **Testability:** Business logic is independent of Android framework
- **Offline-First:** All features work without internet connectivity

---

## Architecture Layers

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Jetpack Compose + ViewModels)         │
│  - UI Components                        │
│  - State Management                     │
│  - User Input Handling                  │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│           Domain Layer                  │
│    (Business Logic + Use Cases)         │
│  - Game Rules                           │
│  - ELO Calculation                      │
│  - Move Validation                      │
│  - Analysis Algorithms                  │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│            Data Layer                   │
│  (Repositories + Data Sources)          │
│  - Room Database                        │
│  - Stockfish Engine                     │
│  - Local Storage                        │
└─────────────────────────────────────────┘
```

### 1. Presentation Layer

**Responsibility:** Display data and handle user interactions

**Components:**
- Jetpack Compose UI
- ViewModels (state holders)
- UI State classes
- Navigation

**Key Files:**
```
ui/
├── game/
│   ├── GameScreen.kt          # Main game UI
│   ├── GameViewModel.kt       # Game state management
│   └── components/
│       ├── ChessBoard.kt      # Board rendering
│       └── MoveList.kt        # Move history display
├── analysis/
│   ├── AnalysisScreen.kt
│   └── AnalysisViewModel.kt
├── practice/
│   ├── PracticeScreen.kt
│   └── PracticeViewModel.kt
└── theme/
    ├── Theme.kt               # Black & white theme
    └── Type.kt                # Typography
```

**Example:**
```kotlin
@Composable
fun GameScreen(
    viewModel: GameViewModel = hiltViewModel()
) {
    val gameState by viewModel.gameState.collectAsState()
    
    Column {
        ChessBoard(
            position = gameState.position,
            onSquareClick = { square -> viewModel.onSquareClick(square) }
        )
        MoveList(moves = gameState.moves)
    }
}
```

### 2. Domain Layer

**Responsibility:** Business logic and rules

**Components:**
- Use Cases (single-responsibility operations)
- Domain Models (pure data classes)
- Business Rules
- Interfaces for data access

**Key Files:**
```
domain/
├── model/
│   ├── Game.kt               # Game model
│   ├── Position.kt           # Board position
│   ├── Move.kt               # Chess move
│   ├── EloCalculator.kt      # ELO logic
│   └── Analysis.kt           # Analysis results
└── usecase/
    ├── PlayMoveUseCase.kt    # Execute a move
    ├── AnalyzeGameUseCase.kt # Analyze with Stockfish
    ├── GeneratePracticeUseCase.kt
    └── UpdateEloUseCase.kt
```

**Example:**
```kotlin
class PlayMoveUseCase(
    private val gameRepository: GameRepository,
    private val stockfishEngine: StockfishEngine
) {
    suspend operator fun invoke(
        gameId: Long,
        move: Move
    ): Result<Position> {
        // Validate move
        // Update position
        // Get AI response
        // Save to database
        return Result.success(newPosition)
    }
}
```

### 3. Data Layer

**Responsibility:** Data access and storage

**Components:**
- Repositories (abstraction over data sources)
- Room Database (local storage)
- Stockfish Engine (AI)
- Data Access Objects (DAOs)

**Key Files:**
```
data/
├── database/
│   ├── ChessDatabase.kt      # Room database
│   ├── dao/
│   │   ├── GameDao.kt        # Game queries
│   │   ├── PositionDao.kt
│   │   └── PracticeDao.kt
│   └── entities/
│       ├── GameEntity.kt     # Database tables
│       ├── PositionEntity.kt
│       └── PracticePositionEntity.kt
├── repository/
│   ├── GameRepository.kt     # Game data operations
│   ├── AnalysisRepository.kt
│   └── PracticeRepository.kt
└── engine/
    └── StockfishEngine.kt    # Chess AI interface
```

**Example:**
```kotlin
class GameRepository(
    private val gameDao: GameDao,
    private val stockfish: StockfishEngine
) {
    suspend fun saveGame(game: Game) {
        gameDao.insert(game.toEntity())
    }
    
    suspend fun getAIMove(fen: String, eloLevel: Int): Move {
        return stockfish.getBestMove(fen, eloLevel)
    }
}
```

---

## Key Components

### Stockfish Integration

**Purpose:** Provides chess AI and position analysis

**Implementation:**
- Native C++ library via JNI
- UCI protocol communication
- Configurable strength (ELO-based)

```kotlin
class StockfishEngine {
    external fun init(): Long
    external fun sendCommand(command: String)
    external fun getOutput(): String
    
    fun getBestMove(fen: String, targetElo: Int): Move {
        val config = EloConfiguration.forElo(targetElo)
        sendCommand("position fen $fen")
        config.getUciCommands().forEach { sendCommand(it) }
        sendCommand("go depth ${config.depth}")
        return parseBestMove(getOutput())
    }
    
    companion object {
        init {
            System.loadLibrary("stockfish")
        }
    }
}
```

### ELO System

**Purpose:** Track player skill and adjust opponent strength

**Key Features:**
- Standard ELO formula
- Accuracy-based adjustments
- Confidence intervals for new players
- Opponent recommendation

**Formula:**
```
Expected = 1 / (1 + 10^((OpponentELO - PlayerELO) / 400))
Change = K * (Actual - Expected)
NewELO = OldELO + Change
```

### Database Schema

```sql
user_profile
├── id (PK)
├── current_elo
├── peak_elo
├── games_played
├── wins/losses/draws
└── last_updated

games
├── id (PK)
├── pgn
├── player_elo_before/after
├── opponent_elo
├── result
├── accuracy
└── date_played

positions
├── id (PK)
├── game_id (FK)
├── move_number
├── fen
├── evaluation
├── best_move
├── player_move
└── is_mistake/blunder

practice_positions
├── id (PK)
├── source_game_id (FK)
├── fen
├── category
├── difficulty
├── times_practiced
└── success_rate
```

### Analysis Pipeline

**Flow:**
1. Load game PGN from database
2. Parse into positions
3. For each position:
   - Send FEN to Stockfish
   - Get evaluation and best move
   - Compare with played move
   - Calculate centipawn loss
   - Classify move quality
4. Calculate statistics
5. Extract practice positions
6. Save analysis to database

```kotlin
class AnalyzeGameUseCase(
    private val stockfish: StockfishEngine,
    private val positionDao: PositionDao
) {
    suspend operator fun invoke(gameId: Long): Analysis {
        val positions = getGamePositions(gameId)
        val analysis = positions.map { position ->
            val eval = stockfish.evaluate(position.fen)
            val bestMove = stockfish.getBestMove(position.fen)
            analyzePosition(position, eval, bestMove)
        }
        return Analysis(
            accuracy = calculateAccuracy(analysis),
            mistakes = analysis.count { it.isMistake },
            blunders = analysis.count { it.isBlunder },
            practicePositions = extractPractice(analysis)
        )
    }
}
```

---

## Data Flow

### Playing a Move

```
User taps square
       ↓
GameScreen captures event
       ↓
GameViewModel.onSquareClick(square)
       ↓
PlayMoveUseCase.invoke(move)
       ↓
Validate move (chesslib)
       ↓
Update position
       ↓
Save to database
       ↓
Request AI move (StockfishEngine)
       ↓
Apply AI move
       ↓
Update UI State
       ↓
GameScreen recomposes
```

### Post-Game Analysis

```
Game ends
       ↓
AnalysisViewModel.analyzeGame()
       ↓
AnalyzeGameUseCase.invoke()
       ↓
Load positions from database
       ↓
For each position:
  - Stockfish evaluation
  - Best move calculation
  - Move classification
       ↓
Calculate statistics
       ↓
Extract practice positions
       ↓
Save analysis to database
       ↓
Update ELO (UpdateEloUseCase)
       ↓
Display results
```

---

## Design Patterns

### Repository Pattern

Abstracts data sources from business logic.

```kotlin
interface GameRepository {
    suspend fun getGame(id: Long): Game
    suspend fun saveGame(game: Game): Long
    suspend fun getAllGames(): List<Game>
}

class GameRepositoryImpl(
    private val gameDao: GameDao
) : GameRepository {
    override suspend fun getGame(id: Long) = 
        gameDao.getById(id).toDomain()
}
```

### Use Case Pattern

Encapsulates single business operations.

```kotlin
class UpdateEloUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        gameResult: GameResult,
        opponentElo: Int,
        accuracy: Double
    ): Int {
        val currentProfile = userRepository.getProfile()
        val newElo = EloCalculator.calculateNewElo(
            currentProfile.currentElo,
            opponentElo,
            gameResult,
            accuracy
        )
        userRepository.updateElo(newElo)
        return newElo
    }
}
```

### Observer Pattern

ViewModels expose StateFlow for UI observation.

```kotlin
class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
    fun onSquareClick(square: Square) {
        _gameState.value = _gameState.value.copy(
            selectedSquare = square
        )
    }
}
```

### Factory Pattern

Configuration creation based on parameters.

```kotlin
object EloConfiguration {
    fun forElo(elo: Int): EloConfiguration {
        return when {
            elo < 1000 -> EloConfiguration(...)
            elo < 1400 -> EloConfiguration(...)
            // ...
        }
    }
}
```

---

## Technology Choices

### Why Kotlin?

- Modern, concise syntax
- Null safety
- Coroutines for async operations
- Official Android language
- Excellent tooling

### Why Jetpack Compose?

- Declarative UI (easier to reason about)
- Less boilerplate than XML
- Built-in state management
- Better performance
- Material Design 3 support

### Why Room?

- Type-safe SQL queries
- Compile-time verification
- Coroutines support
- Easy migrations
- Excellent documentation

### Why Stockfish?

- Strongest open-source engine (3500+ ELO)
- Completely free (GPL-3.0)
- UCI protocol standard
- Highly configurable
- Well-maintained

### Why Clean Architecture?

- Testable (business logic independent of Android)
- Maintainable (clear separation of concerns)
- Flexible (easy to swap implementations)
- Scalable (add features without breaking existing code)

---

## Performance Considerations

### Memory Management

- Use `viewModelScope` for coroutines
- Cancel jobs when ViewModel cleared
- Use `Flow` for reactive data
- Avoid memory leaks in Stockfish JNI

### Battery Optimization

- Pure black (#000000) for OLED screens
- Limit Stockfish thinking time
- Pause engine when app backgrounded
- Minimal animations
- No unnecessary wake locks

### Database Performance

- Indices on frequently queried columns
- Batch inserts for multiple positions
- Use transactions for multi-step operations
- Lazy loading for large datasets

### UI Performance

- Use `derivedStateOf` for computed values
- Minimize recompositions
- Use `key()` in lazy lists
- Avoid heavy operations on main thread

---

## Testing Strategy

### Unit Tests (Domain Layer)

```kotlin
@Test
fun `EloCalculator increases rating on win with high accuracy`() {
    val result = EloCalculator.calculateNewElo(
        currentElo = 1500,
        opponentElo = 1600,
        result = GameResult.WIN,
        moveAccuracy = 95.0
    )
    assertTrue(result > 1500)
}
```

### Integration Tests (Data Layer)

```kotlin
@Test
fun testGameSaveAndLoad() = runTest {
    val game = Game(...)
    val id = repository.saveGame(game)
    val loaded = repository.getGame(id)
    assertEquals(game, loaded)
}
```

### UI Tests (Presentation Layer)

```kotlin
@Test
fun testChessboardDisplaysCorrectly() {
    composeTestRule.setContent {
        ChessBoard(position = startingPosition)
    }
    composeTestRule.onNodeWithText("♔").assertExists()
}
```

---

## Future Architecture Enhancements

1. **Dependency Injection:** Add Hilt for DI
2. **Multi-Module:** Split into feature modules
3. **Caching Layer:** Add in-memory cache for frequent queries
4. **Analytics:** Track usage patterns (offline)
5. **Export/Import:** Cloud sync support (optional)

---

## References

- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Stockfish UCI Protocol](https://www.shredderchess.com/download/div/uci.zip)

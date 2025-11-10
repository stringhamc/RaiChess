# RaiChess (来Chess) - The Next Chess App

```
██████   █████  ██  ██████ ██   ██ ███████ ███████ ███████ 
██   ██ ██   ██ ██ ██      ██   ██ ██      ██      ██      
██████  ███████ ██ ██      ███████ █████   ███████ ███████ 
██   ██ ██   ██ ██ ██      ██   ██ ██           ██      ██ 
██   ██ ██   ██ ██  ██████ ██   ██ ███████ ███████ ███████ 
```

**Rai** (来) = *"Next"* in Japanese | **RaiChess** = *Righteous!*

> *The next evolution in chess training - stripped down, powered up.*

An offline-first Android chess application with Stockfish AI, ELO-based difficulty, comprehensive game analysis, and intelligent practice features. Pure black & white. Pure focus. Pure chess.

## Features

🎯 **ELO-Based Gameplay**
- Dynamic opponent strength from 800-2800+ ELO
- Personal ELO rating that evolves with your play
- Accurate rating calculation based on results and move accuracy

🤖 **Stockfish AI Engine**
- World-class chess engine (3500+ ELO) at full strength
- Completely offline - no internet required
- Adjustable to match your skill level

📊 **Comprehensive Analysis**
- Post-game analysis with Stockfish
- Move classification (brilliant, good, inaccuracy, mistake, blunder)
- Accuracy percentage calculation
- Position evaluation graphs
- Opening recognition

💡 **Intelligent Practice Mode**
- Automatically extracts tactical positions from your games
- Targets your specific weaknesses
- Categories: tactics, endgames, openings, mistake correction
- Spaced repetition for optimal learning
- Progress tracking and statistics

🎨 **Minimal Black & White UI**
- Pure black background for OLED power savings
- High contrast for maximum readability
- No unnecessary visual effects
- Battery-efficient design

💾 **Full Offline Support**
- All games saved locally
- Complete position history
- Works without internet connection
- Export games in PGN format

## Technical Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM with Clean Architecture
- **Database:** Room (SQLite)
- **Chess Engine:** Stockfish 16
- **Chess Library:** chesslib for move validation and PGN parsing
- **Min SDK:** API 24 (Android 7.0)

## Project Structure

```
app/
├── src/main/
│   ├── java/com/yourapp/chess/
│   │   ├── data/
│   │   │   ├── database/          # Room database, DAOs, entities
│   │   │   ├── repository/        # Data repositories
│   │   │   └── engine/            # Stockfish integration
│   │   ├── domain/
│   │   │   ├── model/             # Domain models
│   │   │   └── usecase/           # Business logic
│   │   └── ui/
│   │       ├── game/              # Game screen
│   │       ├── analysis/          # Analysis screen
│   │       ├── practice/          # Practice mode
│   │       └── history/           # Game history
│   ├── cpp/                       # Stockfish native library
│   └── res/                       # Resources
└── build.gradle
```

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 24+
- NDK (for Stockfish compilation)
- Git

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/raichess.git
cd raichess
```

2. Open in Android Studio

3. Build the Stockfish native library:
```bash
cd app/src/main/cpp/stockfish
./build.sh
```

4. Sync Gradle and build the project

5. Run on device or emulator

## Building Stockfish

The app includes Stockfish as a native library. To build it:

```bash
# Ensure NDK is installed
export ANDROID_NDK=/path/to/ndk

# Build for all architectures
cd app/src/main/cpp/stockfish
./build-android.sh
```

This will create binaries for:
- arm64-v8a
- armeabi-v7a
- x86_64
- x86

## Usage

### Playing a Game

1. Launch app and set your opponent's ELO (or use recommended)
2. Choose color (white, black, or random)
3. Play moves by tapping pieces and destination squares
4. Your ELO updates after each game based on result and accuracy

### Analyzing Games

1. After a game, tap "Analyze"
2. View move-by-move evaluation
3. See mistakes and better alternatives
4. Positions marked for practice are automatically saved

### Practice Mode

1. Navigate to Practice from main menu
2. Solve tactical positions from your games
3. Use hints if stuck (progressive difficulty)
4. Track your success rate and streaks

## ELO System

### How It Works

- **Initial ELO:** 1200 (standard starting rating)
- **Updates After Each Game:** Based on opponent strength and result
- **Accuracy Bonus:** Good play is rewarded even in losses
- **K-Factor:** 32 (standard chess rating change rate)
- **Range:** 400-3000

### ELO Calculation

```
Expected Score = 1 / (1 + 10^((Opponent ELO - Your ELO) / 400))
Actual Score = 1.0 (win), 0.5 (draw), 0.0 (loss)
Accuracy Bonus = (Accuracy% - 50) / 100 * 0.3
ELO Change = K-Factor * (Adjusted Actual Score - Expected Score)
```

## Stockfish Configuration

The app uses UCI commands to configure Stockfish for different ELO levels:

```kotlin
setoption name UCI_LimitStrength value true
setoption name UCI_Elo value [800-2800]
setoption name Skill Level value [0-20]
```

## Database Schema

### Core Tables

- **user_profile:** Current ELO, statistics, preferences
- **elo_history:** ELO progression over time
- **games:** Complete game records with PGN
- **positions:** Individual positions from games with analysis
- **practice_positions:** Extracted tactical positions
- **openings:** Opening repertoire and statistics

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

## Development Roadmap

### Phase 1: MVP (Weeks 1-6)
- [x] Project setup and architecture
- [ ] Chess board UI
- [ ] Stockfish integration
- [ ] Basic game play
- [ ] Database setup
- [ ] Simple analysis

### Phase 2: Analysis (Weeks 7-9)
- [ ] Comprehensive post-game analysis
- [ ] Move classification
- [ ] Evaluation graphs
- [ ] Opening recognition

### Phase 3: Practice (Weeks 10-12)
- [ ] Position extraction
- [ ] Practice mode UI
- [ ] Spaced repetition
- [ ] Progress tracking

### Phase 4: Polish (Weeks 13-14)
- [ ] UI refinements
- [ ] Performance optimization
- [ ] Bug fixes
- [ ] Testing

## Performance Targets

- AI response time: < 1 second (medium difficulty)
- Storage per game: < 100KB (with full analysis)
- Battery usage: < 5% per hour active play
- App size: < 50MB (including Stockfish)
- Crash rate: < 0.1%

## License

This project is licensed under the GPL-3.0 License - see the [LICENSE](LICENSE) file for details.

Note: Stockfish is licensed under the GPL-3.0 License.

## Acknowledgments

- [Stockfish](https://stockfishchess.org/) - The world's strongest open-source chess engine
- [chesslib](https://github.com/bhlangonijr/chesslib) - Java chess library for move generation and PGN parsing
- [DroidFish](https://github.com/peterosterlund2/droidfish) - Reference implementation for Stockfish on Android
- The Japanese language for inspiring our name: 来 (rai) = "next"
- The 80s for teaching us that "Righteous!" is the ultimate expression of awesomeness

## Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Email: your.email@example.com

## Name Origin

**RaiChess (来Chess)** - "Rai" (来) means "next" or "coming" in Japanese, representing the next evolution in chess training apps. It also sounds like "Righteous!" - that awesome 80s exclamation of excellence!

## Disclaimer

This app is for educational and personal use. The ELO ratings are estimates and may not reflect official chess ratings.

# Contributing to RaiChess (来Chess)

Thank you for your interest in contributing to RaiChess! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on what is best for the community
- Show empathy towards other community members

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 24+
- Android NDK (for Stockfish compilation)
- Git

### Setting Up Development Environment

1. **Clone the repository:**
```bash
git clone https://github.com/yourusername/raichess.git
cd raichess
```

2. **Open in Android Studio:**
   - File → Open → Select the project directory
   - Wait for Gradle sync to complete

3. **Build Stockfish (required for full functionality):**
```bash
cd app/src/main/cpp/stockfish
./build-android.sh
```

4. **Run the app:**
   - Select a device/emulator
   - Click Run (Shift+F10)

## Development Guidelines

### Code Style

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

- Use 4 spaces for indentation
- Use camelCase for variables and functions
- Use PascalCase for classes
- Maximum line length: 120 characters
- Add KDoc comments for public APIs

**Example:**
```kotlin
/**
 * Calculate new ELO rating after a game
 * 
 * @param currentElo Player's current ELO rating
 * @param opponentElo Opponent's ELO rating
 * @param result Game result from player's perspective
 * @param moveAccuracy Player's move accuracy percentage
 * @return New ELO rating
 */
fun calculateNewElo(
    currentElo: Int,
    opponentElo: Int,
    result: GameResult,
    moveAccuracy: Double
): Int {
    // Implementation
}
```

### Architecture

We use **Clean Architecture** with MVVM pattern:

```
presentation/ (UI + ViewModels)
    ↓
domain/ (Use Cases + Business Logic)
    ↓
data/ (Repositories + Data Sources)
```

**Key principles:**
- ViewModels should not contain Android framework dependencies
- Use Cases should contain single-responsibility business logic
- Repositories abstract data source details
- Domain models are framework-independent

### Git Workflow

1. **Create a feature branch:**
```bash
git checkout -b feature/your-feature-name
```

2. **Make your changes with clear commits:**
```bash
git add .
git commit -m "feat: add ELO calculation for practice mode"
```

3. **Keep your branch updated:**
```bash
git fetch origin
git rebase origin/main
```

4. **Push your branch:**
```bash
git push origin feature/your-feature-name
```

5. **Create a Pull Request**

### Commit Message Format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(elo): implement accuracy-based ELO adjustment

fix(board): resolve piece rendering on small screens

docs(readme): update installation instructions

test(analysis): add unit tests for move classification
```

## What to Contribute

### Priority Areas

1. **Core Features:**
   - Stockfish integration improvements
   - Game analysis algorithms
   - Practice position generation
   - UI/UX enhancements

2. **Testing:**
   - Unit tests for business logic
   - Integration tests for database
   - UI tests for critical flows

3. **Documentation:**
   - Code documentation
   - User guides
   - Architecture documentation

4. **Bug Fixes:**
   - Check the [Issues](https://github.com/yourusername/chess-trainer-app/issues) page
   - Look for `good first issue` or `help wanted` labels

### Feature Requests

Before starting work on a new feature:

1. Check if an issue already exists
2. If not, create a new issue describing:
   - The problem or need
   - Proposed solution
   - Alternative solutions considered
3. Wait for discussion and approval
4. Once approved, reference the issue in your PR

## Pull Request Process

1. **Before submitting:**
   - Ensure code builds without errors
   - Run all tests: `./gradlew test`
   - Test on at least one physical device
   - Update documentation if needed
   - Add tests for new features

2. **PR Description should include:**
   - Summary of changes
   - Issue reference (e.g., "Fixes #123")
   - Screenshots/videos for UI changes
   - Testing performed
   - Breaking changes (if any)

3. **Review process:**
   - Maintainers will review your PR
   - Address any requested changes
   - Once approved, it will be merged

## Testing Guidelines

### Unit Tests

Located in `app/src/test/`:

```kotlin
@Test
fun `calculateNewElo should increase rating on win`() {
    val currentElo = 1500
    val opponentElo = 1500
    val result = GameResult.WIN
    val accuracy = 85.0
    
    val newElo = EloCalculator.calculateNewElo(
        currentElo, opponentElo, result, accuracy
    )
    
    assert(newElo > currentElo)
}
```

### Integration Tests

Located in `app/src/androidTest/`:

```kotlin
@Test
fun testGameSaveAndLoad() {
    // Test database operations
}
```

### UI Tests

```kotlin
@Test
fun testChessboardRendering() {
    // Test UI components
}
```

**Run tests:**
```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest
```

## Reporting Bugs

When reporting bugs, include:

1. **Environment:**
   - Device model
   - Android version
   - App version

2. **Steps to reproduce:**
   - Clear, numbered steps
   - Expected behavior
   - Actual behavior

3. **Additional info:**
   - Screenshots/videos
   - Logs (if available)
   - Frequency (always/sometimes)

## Code Review Checklist

Before submitting, verify:

- [ ] Code follows Kotlin conventions
- [ ] No hardcoded strings (use resources)
- [ ] No memory leaks (ViewModels, coroutines)
- [ ] Proper error handling
- [ ] Comments for complex logic
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No unnecessary dependencies
- [ ] Backwards compatible (or noted)

## Performance Guidelines

### Battery Efficiency
- Minimize background processing
- Use pure black (#000000) for backgrounds
- Avoid animations unless necessary
- Limit Stockfish analysis time
- Cancel coroutines when not needed

### Memory Management
```kotlin
// Good: Use viewModelScope
viewModelScope.launch {
    // Automatically cancelled when ViewModel cleared
}

// Bad: GlobalScope
GlobalScope.launch {
    // Leaks memory
}
```

### Database Optimization
- Use indices for frequent queries
- Batch operations when possible
- Use transactions for multiple operations

## Questions?

- Check existing [Issues](https://github.com/yourusername/raichess/issues)
- Ask in [Discussions](https://github.com/yourusername/raichess/discussions)
- Email: your.email@example.com

## License

By contributing, you agree that your contributions will be licensed under the GPL-3.0 License (same as Stockfish).

---

Thank you for contributing to RaiChess (来Chess) - The Next Chess App! 🎉♟️

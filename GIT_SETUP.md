# RaiChess (来Chess) - Git Setup Guide

## Quick Git Setup

Follow these exact commands to get your repository on GitHub.

### Step 1: Create GitHub Repository

Go to https://github.com/new and create a new repository:
- **Repository name:** `raichess`
- **Description:** `RaiChess (来Chess) - The Next Chess App. Offline chess training with Stockfish AI and ELO-based difficulty.`
- **Visibility:** Public (or Private)
- **Do NOT** initialize with README, .gitignore, or license (we already have these)

### Step 2: Initialize Local Repository

```bash
# Navigate to the raichess directory
cd raichess

# Initialize git repository
git init

# Add all files
git add .

# Create initial commit
git commit -m "Initial commit: RaiChess (来Chess) - The Next Chess App

- Complete project structure with Clean Architecture
- ELO-based rating system (800-2800+)
- Minimal black & white UI for OLED power savings
- Comprehensive documentation (README, Architecture, Branding)
- Gradle build configuration
- Room database schema
- Stockfish integration ready
- GPL-3.0 license"

# Rename default branch to main
git branch -M main

# Add remote (replace 'yourusername' with your GitHub username)
git remote add origin https://github.com/yourusername/raichess.git

# Push to GitHub
git push -u origin main
```

### Step 3: Verify on GitHub

1. Go to https://github.com/yourusername/raichess
2. You should see all files uploaded
3. README.md will be displayed automatically

## Repository Settings Recommendations

### Topics (for discoverability)

Add these topics to your repository:
```
android
kotlin
chess
stockfish
jetpack-compose
material-design
chess-ai
chess-engine
elo-rating
offline-first
oled-optimization
```

### About Section

```
🎯 RaiChess (来Chess) - The Next Chess App
Offline chess training with Stockfish AI, ELO-based difficulty, and intelligent practice mode.
```

### Repository Options

**Recommended settings:**
- ✅ Include in the code search
- ✅ Discussions (for community)
- ✅ Issues (for bug tracking)
- ✅ Projects (for roadmap tracking)
- ✅ Wiki (for additional documentation)

### Branch Protection (optional but recommended)

For `main` branch:
- ✅ Require pull request reviews before merging
- ✅ Require status checks to pass before merging
- ✅ Require branches to be up to date before merging

## Useful Git Commands

### Daily Development

```bash
# Create feature branch
git checkout -b feature/chess-board-ui

# Stage changes
git add .

# Commit with conventional commit format
git commit -m "feat(ui): implement chess board component"

# Push feature branch
git push origin feature/chess-board-ui

# After PR merge, update main
git checkout main
git pull origin main

# Delete feature branch
git branch -d feature/chess-board-ui
```

### Syncing Fork (for contributors)

```bash
# Add upstream remote (only once)
git remote add upstream https://github.com/originalowner/raichess.git

# Fetch upstream changes
git fetch upstream

# Merge upstream changes
git checkout main
git merge upstream/main

# Push to your fork
git push origin main
```

### Checking Status

```bash
# View changed files
git status

# View commit history
git log --oneline --graph --all

# View differences
git diff

# View remote repositories
git remote -v
```

## GitHub CLI Alternative (Optional)

If you have [GitHub CLI](https://cli.github.com/) installed:

```bash
# Navigate to project
cd raichess

# Initialize git and create GitHub repo in one command
git init
git add .
git commit -m "Initial commit: RaiChess (来Chess) - The Next Chess App"

# Create repo on GitHub and push (interactive)
gh repo create raichess --public --source=. --push

# Or specify everything
gh repo create raichess \
  --public \
  --description "RaiChess (来Chess) - The Next Chess App" \
  --source=. \
  --push
```

## First Issue to Create

After pushing, create your first issue for tracking:

**Title:** "Set up project structure and Stockfish integration"

**Body:**
```markdown
## Description
Complete the initial project setup following the roadmap in README.md

## Tasks
- [ ] Create remaining directory structure
- [ ] Download and integrate Stockfish
- [ ] Build Stockfish for Android (arm64-v8a, armeabi-v7a)
- [ ] Create basic MainActivity
- [ ] Set up navigation
- [ ] Implement RaiChessTheme
- [ ] Create basic home screen UI

## Reference
See QUICKSTART.md for setup instructions
```

**Labels:** `enhancement`, `good first issue`, `documentation`

## Project Board Setup

Create a project board with columns:
1. **📋 Backlog** - Future features
2. **📝 To Do** - Ready to work on
3. **🔨 In Progress** - Currently working
4. **👀 In Review** - PR submitted
5. **✅ Done** - Completed

## Release Strategy

### Version Numbering

Follow Semantic Versioning:
- `MAJOR.MINOR.PATCH`
- Example: `1.0.0` (first stable release)

**Milestones:**
- `v0.1.0` - MVP (basic gameplay)
- `v0.5.0` - Full feature set
- `v1.0.0` - First stable release

### Creating a Release

```bash
# Tag a release
git tag -a v0.1.0 -m "MVP Release: Basic gameplay and ELO system"

# Push tag to GitHub
git push origin v0.1.0
```

Then on GitHub:
1. Go to Releases
2. Click "Draft a new release"
3. Select the tag
4. Add release notes
5. Attach APK file
6. Publish release

## Continuous Integration (Future)

Once you have some code, set up GitHub Actions:

`.github/workflows/android.yml`:
```yaml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    - name: Build with Gradle
      run: ./gradlew build
    - name: Run tests
      run: ./gradlew test
```

## Troubleshooting

### "remote: Permission denied"
- Check SSH key is added to GitHub
- Or use HTTPS: `https://github.com/yourusername/raichess.git`

### "Updates were rejected"
```bash
git pull origin main --rebase
git push origin main
```

### "Large files detected"
- Stockfish binaries should be in .gitignore
- Check .gitignore includes `*.so` and `jniLibs/`

### Accidentally committed large files
```bash
# Remove from git but keep locally
git rm --cached app/src/main/jniLibs/arm64-v8a/libstockfish.so
git commit -m "Remove Stockfish binary from git"
git push
```

## Next Steps After Push

1. ✅ Verify all files are on GitHub
2. 📝 Add repository topics
3. 🎯 Create first issue
4. 📊 Set up project board
5. 👥 Invite collaborators (if any)
6. 📢 Share on Reddit, Twitter, etc.

---

**RaiChess (来Chess)** - Ready to push! 🚀

Remember: The journey of a thousand commits begins with a single push. Righteous!

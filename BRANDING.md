# RaiChess (来Chess) - Branding & Visual Identity

## Name & Concept

**RaiChess (来Chess)**
- **Rai (来)** - Japanese kanji meaning "next" or "coming"
- **Phonetic:** Sounds like "Righteous!" - the ultimate 80s expression of excellence
- **Tagline:** "The Next Chess App"

## Logo Concepts

### Primary Logo (Minimal Black & White)

```
┌─────────────────────────────┐
│                             │
│         来                  │
│      ───────                │
│        ♔ ♟                  │
│                             │
│      RaiChess               │
│                             │
└─────────────────────────────┘
```

**Elements:**
- Japanese kanji 来 (rai) at the top
- Horizontal line separator
- Chess pieces (King and Pawn) representing progression
- "RaiChess" wordmark in clean, modern font

### Alternative Logo (Icon Only)

```
┌─────────┐
│   来    │
│   ♔     │
└─────────┘
```

**Usage:** App icon, small spaces, favicons

### Logo Variations

1. **Full Logo:** Kanji + Pieces + Wordmark
2. **Icon Logo:** Kanji + Single piece
3. **Text Logo:** RaiChess wordmark only
4. **Kanji Logo:** 来 only (square app icon)

## Color Palette

### Primary Colors (OLED Optimized)

```
Pure Black:    #000000  ■  (Background — OLED power saving)
Pure White:    #FFFFFF  □  (Text, UI — Maximum contrast)
```

The app background stays pure black for OLED power savings. The **board
squares** are the one deliberate exception: they use softened grays
(dark #3D3D3D / light #E3E3E3) so the two-tone outlined vector pieces
stay legible on either color. The board is bounded and the surrounding
UI remains pure black, so the power impact is small and the legibility
gain is worth it.

### Accent Colors (Minimal Use)

```
Light Gray:    #888888  ▒  (Selection, hints)
Dark Gray:     #333333  ▓  (Containers)
Mid Gray:      #666666  ▓  (Borders, disabled)
```

### Why Black & White?

1. **Power Efficiency:** Pure black (#000000) on OLED screens uses zero power for those pixels
2. **High Contrast:** Maximum readability in all lighting conditions
3. **Focus:** Minimal distraction, attention on the game
4. **Timeless:** Classic, elegant, never goes out of style
5. **Performance:** Faster rendering, less GPU usage

## Typography

### Primary Font: System Default (Roboto on Android)

**Reasoning:**
- No custom font loading = faster performance
- Better battery life
- Native Android feel
- Excellent readability

### Font Weights
- Regular (400) - Body text
- Medium (500) - Unused (keeping it minimal)
- Bold (700) - Unused (using size hierarchy instead)

### Type Scale

```
Display Large:   48sp  (ELO numbers, scores)
Headline Large:  32sp  (Screen titles)
Title Large:     20sp  (Section headers)
Body Large:      16sp  (Main content)
Label Medium:    12sp  (Buttons, small text)
```

### Chess Pieces Typography

Using Unicode chess symbols for ultimate simplicity:
```
White: ♔ ♕ ♖ ♗ ♘ ♙
Black: ♚ ♛ ♜ ♝ ♞ ♟
Font Size: 48sp for board display
```

## App Icon Design

### Launcher Icon Concept

```
┌─────────────────┐
│                 │
│      来         │
│                 │
│      ♔          │
│                 │
└─────────────────┘

Pure black background
White kanji & chess piece
108x108dp adaptive icon
```

### Adaptive Icon Layers

**Foreground Layer:**
- 来 (kanji) - top center
- ♔ (king) - bottom center
- White on transparent

**Background Layer:**
- Solid black (#000000)

### Icon Sizes Required

- `mipmap-mdpi/` - 48x48px
- `mipmap-hdpi/` - 72x72px
- `mipmap-xhdpi/` - 96x96px
- `mipmap-xxhdpi/` - 144x144px
- `mipmap-xxxhdpi/` - 192x192px
- Adaptive icon - 108x108dp (with safe zone)

## UI Design System

### Spacing Scale

```
4dp   - Micro spacing (piece padding)
8dp   - Small spacing (icon margins)
16dp  - Medium spacing (standard margins)
24dp  - Large spacing (section dividers)
32dp  - XL spacing (screen padding)
```

### Board Design

```
┌────────────────────────┐
│ ▢ ■ ▢ ■ ▢ ■ ▢ ■       │  8
│ ■ ▢ ■ ▢ ■ ▢ ■ ▢       │  7
│ ▢ ■ ▢ ■ ▢ ■ ▢ ■       │  6
│ ■ ▢ ■ ▢ ■ ▢ ■ ▢       │  5
│ ▢ ■ ▢ ■ ▢ ■ ▢ ■       │  4
│ ■ ▢ ■ ▢ ■ ▢ ■ ▢       │  3
│ ▢ ■ ▢ ■ ▢ ■ ▢ ■       │  2
│ ■ ▢ ■ ▢ ■ ▢ ■ ▢       │  1
└────────────────────────┘
  a b c d e f g h
```

**Features:**
- Light squares: #E3E3E3 (softened from pure white for piece legibility)
- Dark squares: #3D3D3D (softened from pure black for piece legibility)
- Gray board frame (#888888)
- Pieces use two-tone vector drawables (white fill / black outline and vice
  versa) with a baked soft drop shadow for depth and to keep white pieces
  legible on the light squares — the shadow is pure vector (no raster
  assets, negligible size). This is a deliberate, contained relaxation of
  the earlier "no shadows" rule, limited to the pieces on the board.

### Button Style

```
┌──────────────────────┐
│                      │
│    BUTTON TEXT       │
│                      │
└──────────────────────┘

Background: Black with white 1px border
Text: White, 14sp, centered
Padding: 16dp vertical, 24dp horizontal
Corner radius: 0dp (sharp corners for minimal aesthetic)
```

### Input Style (ELO Slider)

```
Current: 1547

800 ────•───────────── 2800

White circle on black background
White track line
No colors - all grayscale
```

## Screen Templates

### Home Screen

```
┌──────────────────────────────┐
│                              │
│      Your ELO: 1547          │
│        (±23)                 │
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
│                              │
│      来Chess                 │
│                              │
└──────────────────────────────┘
```

## Marketing Copy

### App Store Description

**Short Description:**
"RaiChess (来Chess) - The Next Chess App. Train with Stockfish AI, track your ELO, and improve through intelligent practice."

**Full Description:**
```
RaiChess (来Chess) - The Next Chess App

Rai (来) means "next" in Japanese. RaiChess is the next evolution in chess training.

🎯 FEATURES

• ELO-Based AI Opponents (800-2800+)
  Play against Stockfish at your exact skill level

• Personal ELO Rating
  Track your improvement with accurate rating calculations

• Comprehensive Analysis
  Every game analyzed by Stockfish with move-by-move breakdown

• Intelligent Practice
  Automatically creates training positions from your mistakes

• Minimal Black & White UI
  Battery-efficient pure black design for OLED screens

• Completely Offline
  No internet required. All games and analysis stored locally.

⚡ POWER EFFICIENT

Pure black UI optimized for OLED screens. Minimal battery drain.
Focus on chess, not on draining your battery.

🤖 POWERED BY STOCKFISH

The world's strongest chess engine (3500+ ELO) adjusts to match 
your skill level perfectly.

来 THE NEXT CHESS APP

Righteous!
```

### Taglines

1. "来Chess - The Next Chess App"
2. "Train Smart. Play Better."
3. "Your Personal Chess Sensei"
4. "Righteous Chess Training"
5. "Next Level. Next Chess."

## Social Media Presence

### Twitter/X Handle Suggestions
- @RaiChessApp
- @PlayRaiChess
- @NextChessApp

### Hashtags
- #RaiChess
- #来Chess
- #NextChess
- #RighteousChess
- #ChessTraining

## Voice & Tone

**Personality:**
- **Direct:** No fluff, straight to the point
- **Encouraging:** Supportive of improvement
- **Knowledgeable:** Chess expertise without being condescending
- **Retro-Cool:** Subtle 80s vibes ("Righteous!")
- **Minimalist:** Less is more

**Example Copy:**
- ❌ "Wow! That was an absolutely amazing brilliant tactical shot!"
- ✅ "Good move. +2.3 advantage."

- ❌ "Oh no! That's not the best move you could have played!"
- ✅ "Mistake. -1.5. Better: Nf6"

## Future Brand Extensions

Potential future products/features maintaining the brand:

1. **RaiChess Pro** - Advanced features
2. **RaiChess Lite** - Simplified version
3. **RaiTactics** - Pure puzzle training
4. **RaiEndgame** - Endgame specialist

## Brand Guidelines Summary

**DO:**
- Use pure black (#000000) and white (#FFFFFF)
- Keep it minimal and focused
- Emphasize the Japanese origin (来)
- Reference 80s culture subtly
- Focus on improvement and learning
- Highlight offline/battery efficiency

**DON'T:**
- Use colors (except grays for UI functionality)
- Add gradients, shadows, or effects
- Over-explain the name
- Use chess clichés ("checkmate your opponents!")
- Claim to make people grandmasters
- Drain battery with fancy graphics

---

**RaiChess (来Chess)** - The Next Chess App | Righteous! ♟️

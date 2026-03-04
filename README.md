# Golf Tracker

Golf Tracker is an advanced Android application designed for golfers who want detailed insights into their game. It goes beyond simple scorekeeping by offering PGA Tour-level statistical analysis, specifically Stakes Gained (SG) metrics, and built-in Handicap Index calculations.

## ⛳ Features

- **Comprehensive Round Tracking:** Record hole-by-hole scores, putts, penalties, and track every shot's lie and outcome.
- **Strokes Gained Analytics:** Leverage PGA Tour baseline data to analyze your performance in four key areas:
  - **SG: Off-the-Tee**
  - **SG: Approach**
  - **SG: Around-the-Green**
  - **SG: Putting**
- **Handicap Calculation:** Automatically calculate and track your Handicap Index based on a simplified World Handicap System (WHS) model.
- **Data Export:** Export your round data to CSV for external analysis.
- **Pre-loaded Data:** Includes the complete course details for Pebble Beach Golf Links and seeds realistic sample data upon first run to demonstrate the app's analytical capabilities.

## 🛠 Tech Stack

- **Platform:** Android (Kotlin)
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** Hilt
- **Local Database:** Room
- **Navigation:** Jetpack Navigation Compose
- **Concurrency:** Kotlin Coroutines & Flow
- **Testing:** JUnit, Mockito

## 🚀 Getting Started

### Prerequisites

- Android Studio (latest stable version recommended)
- JDK 17
- Android SDK (API Level 34 target)

### Installation

1. Clone the repository:
   ```bash
   git clone <repository_url>
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Build and run the `app` module on an emulator or physical device.

*Note: On the very first run, the app will automatically seed the database with Pebble Beach course data and a sample round.*

## 📊 Analytics Deep Dive

### Strokes Gained (SG)

The app uses a baseline CSV (`sg_baseline.csv`) containing expected strokes to hole out from various distances and lies (Tee, Fairway, Rough, Sand, Recovery) based on PGA Tour averages. The `StrokesGainedCalculator` interpolates between distance buckets to provide accurate SG values for every tracked shot. It also includes difficulty adjustments based on Course Rating and Slope.

### Handicap Index

The `HandicapCalculator` implements a standard WHS differential calculation: `(113 / Slope) * (Gross - Rating)`. It determines the number of differentials to use based on the total number of rounds played (minimum 3 required) and applies appropriate adjustments for smaller sample sizes.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the issues page.

## 📄 License

This project is licensed under the MIT License.

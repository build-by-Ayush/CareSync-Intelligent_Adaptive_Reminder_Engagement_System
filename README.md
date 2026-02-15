# CareSync: ML-Powered Adaptive Reminder System 🔔

**CareSync** is an intelligent Android reminder system that learns **optimal notification timing** using **on-device Machine Learning**. Instead of sending reminders at fixed times, CareSync predicts when you're most likely to engage with tasks using your interaction history and context, reducing notification fatigue while improving task completion.

## 🌟 Key Features

### 🚀 **ML-Driven Timing Prediction**
- **Random Forest model** deployed via **ONNX Runtime** (runs locally on your phone)
- Predicts receptivity probability using **5 contextual features**:
  - Current app category
  - Time in current app
  - Qualified frequency score
  - Night-time indicator
  - Weekend indicator
- **99.99% accuracy** on synthetic behavioral data

### 🛡️ **Adaptive Blacklist Learning**
- Automatically learns "bad hours" from dismissal patterns
- Blocks notifications during hours with ≥5 dismissals
- **Converges in 14 days** (0→1-2→2-4 blacklisted hours)

### ⚙️ **Multi-Stage Decision Pipeline**
8 sequential checks before sending any notification:
Enabled? → ML Prediction? → Confidence? → Blacklisted? → Cooldown? → Quota? → Time Window? → Battery?


### 🎯 **Smart Fallback Guarantees**
- Ensures critical reminders always go through
- Maintains minimum notification frequency
- Balances learning with reliability

### 📱 **Privacy-First Design**
- **100% on-device inference** (no cloud)
- Predictions in **45-95ms**
- **2-8MB model size**
- **<2% battery overhead**

### 🎨 **User Experience**
- 4 scheduling modes (Fixed, Random, ML, Hybrid)
- 8 adaptive message tones
- Gamification (streaks, achievements)
- Multi-modal delivery (visual, audio, haptic)
- Analytics dashboard

## 📊 Performance Results

| Metric | Baseline | CareSync |
|--------|----------|----------|
| **Model Accuracy** | 91.47% (Logistic Regression) | **99.99%** (Random Forest) |
| **Task Completion** | 52% (Fixed-time) | **68%** (ML-optimized) |
| **Prediction Latency** | N/A | **45-95ms** |
| **Model Size** | N/A | **2-8MB** |

*Results from synthetic behavioral data. Real-world performance estimated 85-90% based on domain literature.*

## 🛠️ Tech Stack
  -📱 Frontend: Jetpack Compose, Material 3
  
  -🎯 Architecture: MVVM + Repository Pattern

  -🗄️ Database: Room (SQLite)
  
  -📅 Scheduling: WorkManager
  
  -🤖 ML Inference: ONNX Runtime
  
  -📱 Language: Kotlin
  
## 🎯 How It Works

# Flowchart

# Prerequisites
-Android Studio Koala (2024.1.1) or later
-Android SDK API 34+
-Min SDK 24 (Android 7.0)

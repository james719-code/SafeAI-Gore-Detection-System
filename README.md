# SafeAI Buddy

## Description

SafeAI Buddy is an Android screen-safety app with an on-device visual content classifier. The repository also includes a Python training pipeline for preparing a gore/non-gore image dataset, training a TensorFlow model, converting it to TensorFlow Lite, and syncing the model into the Android app.

## Features

- Android app built with Kotlin and Jetpack Compose.
- On-device TensorFlow Lite classification using `gore_classifier.tflite`.
- Local labels file for the classifier: `labels.txt`.
- Screen capture foreground service for live screening.
- Safety overlay service for blocking detected content.
- Setup, dashboard, shield, and about screens.
- Python scripts for preprocessing, splitting, training, evaluating, benchmarking, converting, and syncing model assets.
- Unit and instrumentation tests for Android components.

## Tech Stack

- Android
  - Kotlin
  - Jetpack Compose
  - Material 3
  - Gradle Kotlin DSL
  - TensorFlow Lite

- Python
  - TensorFlow
  - NumPy
  - scikit-learn
  - Pillow

- Build and tooling
  - Gradle Wrapper
  - Android Gradle Plugin
  - JUnit
  - AndroidX test libraries

## Installation

### Requirements

- Android Studio
- JDK compatible with the Android Gradle project
- Python 3
- `pip`
- Optional: Python virtual environment support with `venv`

### Android setup

From the repository root:

```powershell
cd android
.\gradlew.bat assembleDebug
```

To install the debug build on a connected Android device or emulator:

```powershell
cd android
.\gradlew.bat installDebug
```

### Python setup

From the repository root:

```powershell
cd gore-detection-system
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

For the Windows DirectML dependency set:

```powershell
cd gore-detection-system
python -m pip install -r requirements-windows-directml.txt
```

## Usage

### Run the Android app

1. Open the `android/` folder in Android Studio.
2. Select a connected Android device or emulator.
3. Build and run the app.
4. Use the setup screen to grant required permissions.
5. Run the model check before starting live shield mode.

The Android app expects these runtime assets:

- `android/app/src/main/assets/gore_classifier.tflite`
- `android/app/src/main/assets/labels.txt`

### Run the model pipeline

From the `gore-detection-system/` folder:

```powershell
.\.venv\Scripts\Activate.ps1
python model_training\scripts\preprocess.py --project-root .
python model_training\scripts\split_dataset.py --project-root . --clean
python model_training\scripts\train_model.py --project-root .
python model_training\scripts\evaluate_model.py --project-root .
python model_training\scripts\convert_tflite.py --project-root .
python model_training\scripts\benchmark_inference.py --project-root .
python model_training\scripts\sync_android_assets.py --project-root .
```

To run a prediction on one image:

```powershell
cd gore-detection-system
python model_training\scripts\predict_sample.py --project-root . --input path\to\image.jpg
```

## Environment Variables

No required environment variables are documented in this repository.

TODO: Add environment variables here if the project introduces required configuration values.

## Project Structure

```text
Aldrich_Project/
|-- README.md
|-- android/
|   |-- app/
|   |   |-- src/main/java/com/aldrich/safeai/
|   |   |-- src/main/assets/
|   |   |-- src/main/res/
|   |   |-- src/test/
|   |   `-- build.gradle.kts
|   |-- gradle/
|   |-- build.gradle.kts
|   |-- settings.gradle.kts
|   |-- gradlew
|   `-- gradlew.bat
`-- gore-detection-system/
    |-- dataset/
    |   |-- metadata/
    |   `-- processed/
    |-- model_training/
    |   |-- notebooks/
    |   `-- scripts/
    |-- README.md
    |-- requirements.txt
    `-- requirements-windows-directml.txt
```

## Scripts

### Android scripts

Run these from the `android/` folder:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat clean
```

### Python scripts

Run these from the `gore-detection-system/` folder:

- `model_training/scripts/preprocess.py` - preprocess raw image data and generate metadata.
- `model_training/scripts/split_dataset.py` - create train, validation, and test splits.
- `model_training/scripts/train_model.py` - train a classifier model.
- `model_training/scripts/evaluate_model.py` - evaluate a trained model.
- `model_training/scripts/convert_tflite.py` - convert a trained model to TensorFlow Lite.
- `model_training/scripts/benchmark_inference.py` - benchmark model inference.
- `model_training/scripts/predict_sample.py` - run prediction on an image or folder.
- `model_training/scripts/sync_android_assets.py` - copy model assets into the Android app.
- `model_training/scripts/check_bad_images.py` - check for image files that may cause processing issues.
- `model_training/scripts/find_bad_training_image.py` - locate problematic training images.
- `model_training/scripts/normalize_processed_images.py` - normalize processed dataset images.

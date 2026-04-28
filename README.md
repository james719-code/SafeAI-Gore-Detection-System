# SafeAI Buddy

SafeAI Buddy is an Android screen-safety app backed by a local TensorFlow Lite visual classifier. The repository contains both the Android client and the Python model pipeline used to prepare data, train a classifier, export it to TFLite, and sync the exported model into the app.

The app runs detection on-device. During a live shield session it captures screen frames through Android MediaProjection, evaluates visual risk locally, and shows a blocking overlay when configured thresholds are exceeded.

## Repository Layout

```text
.
|-- android/                  Android app project
|   |-- app/
|   |   |-- src/main/java/    Kotlin app, services, UI, and inference code
|   |   `-- src/main/assets/  Runtime model assets
|   `-- gradle/               Gradle wrapper and dependency catalog
`-- gore-detection-system/    Python dataset and model-training pipeline
    |-- dataset/
    |   |-- raw/              Source images, grouped by class
    |   |-- metadata/         Generated labels and dataset info
    |   `-- processed/        Generated train/validation/test splits
    `-- model_training/
        |-- scripts/          Preprocess, train, evaluate, convert, benchmark
        |-- logs/             Generated metrics and training logs
        `-- saved_models/     Generated Keras and TFLite models
```

## Main Components

- `android/`: Kotlin, Jetpack Compose, Material 3, foreground screen-capture service, overlay service, local TFLite classifier, model self-test, and unit tests.
- `gore-detection-system/`: Python scripts for dataset validation, splitting, model training, evaluation, prediction, TFLite conversion, inference benchmarking, and Android asset sync.
- `android/app/src/main/assets/`: expected runtime assets:
  - `gore_classifier.tflite`
  - `labels.txt`

`gore_classifier.tflite` must be a valid non-empty model. Placeholder or missing model files will fail the app's model self-test.

## Prerequisites

- Windows PowerShell, or equivalent shell with adjusted commands
- Android Studio or the included Gradle wrapper
- JDK compatible with the Android Gradle Plugin used by this project
- Python 3 with `venv`
- For native Windows GPU training: Python 3.10, `tensorflow-cpu==2.10`, and `tensorflow-directml-plugin`

Python dependencies are listed in:

- `gore-detection-system/requirements.txt`
- `gore-detection-system/requirements-windows-directml.txt`

## Python Model Pipeline

From the model project directory:

```powershell
cd gore-detection-system
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

For the Windows DirectML GPU setup:

```powershell
cd gore-detection-system
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-windows-directml.txt
python -c "import tensorflow as tf; print(tf.__version__); print(tf.config.list_physical_devices('GPU'))"
```

Place source images here:

```text
gore-detection-system/dataset/raw/gore/
gore-detection-system/dataset/raw/non_gore/
```

Run the end-to-end model workflow:

```powershell
cd gore-detection-system
.\.venv\Scripts\Activate.ps1

python model_training\scripts\preprocess.py --project-root .
python model_training\scripts\split_dataset.py --project-root . --val-ratio 0.15 --test-ratio 0.15 --seed 42 --clean
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001
python model_training\scripts\evaluate_model.py --project-root . --image-size 224 --batch-size 32 --threshold 0.5
python model_training\scripts\convert_tflite.py --project-root . --float16
python model_training\scripts\benchmark_inference.py --project-root . --backend both --batch-size 1 --warmup-runs 10 --benchmark-runs 50
python model_training\scripts\sync_android_assets.py --project-root .
```

Use `--require-gpu` on training or Keras benchmarking commands when you want the script to fail instead of falling back to CPU.

See `gore-detection-system/README.md` for the full training guide and troubleshooting notes.

## Android App

Build and test from the Android project directory:

```powershell
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

Install on a connected device or emulator:

```powershell
cd android
.\gradlew.bat installDebug
```

The app needs these runtime permissions or approvals for live shielding:

- display-over-other-apps permission for the blocking overlay
- notification permission on Android versions that require it
- screen-capture approval when starting Shield

The app includes a model readiness check. Run it before starting live shielding after replacing or regenerating `gore_classifier.tflite`.

## Development Workflow

1. Add or update images under `gore-detection-system/dataset/raw/`.
2. Run preprocessing and dataset splitting.
3. Train and evaluate the model.
4. Convert the Keras model to TFLite.
5. Sync model assets into `android/app/src/main/assets/`.
6. Build and test the Android app.
7. Run the app, complete setup permissions, run the model check, and start Shield.

## Useful Commands

```powershell
# Validate raw images and metadata
cd gore-detection-system
python model_training\scripts\preprocess.py --project-root . --strict

# Train with GPU required
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --require-gpu --gpu-index 0

# Convert and sync Android assets
python model_training\scripts\convert_tflite.py --project-root . --float16
python model_training\scripts\sync_android_assets.py --project-root .

# Android unit tests
cd ..\android
.\gradlew.bat test
```

## Notes

- Generated datasets, logs, model files, and local virtual environments should stay out of source control unless intentionally versioned.
- Keep `labels.txt` aligned with the trained model output order.
- The Android classifier resolves the gore class from labels, so label naming matters.
- Detection is a support tool, not a substitute for human review or safety guidance.

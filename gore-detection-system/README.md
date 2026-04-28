# Gore Detection System

This project contains:

- a Python pipeline for dataset validation, splitting, training, evaluation, and TFLite conversion
- an Android app module that loads `gore_classifier.tflite` and `labels.txt` from `android/app/src/main/assets`

The recommended local Python setup for this repo is a root virtual environment at:

```text
gore-detection-system\.venv
```

This repository was verified on this machine with:

- Python `3.10.20`
- TensorFlow `2.10.0`
- `tensorflow-directml-plugin`
- DirectML GPU detection enabled in `.venv`

All commands below assume Windows PowerShell and that you are running them from the project root:

```powershell
cd C:\Users\James\Documents\Aldrich_Proj\gore-detection-system
```

## Project Structure

```text
gore-detection-system/
|-- android/
|   `-- app/
|-- dataset/
|   |-- raw/
|   |   |-- gore/
|   |   `-- non_gore/
|   |-- metadata/
|   `-- processed/
|       |-- train/
|       |-- val/
|       `-- test/
`-- model_training/
    |-- scripts/
    |-- logs/
    `-- saved_models/
```

## Prerequisites

- Windows PowerShell
- Python 3 with `venv`
- `pip`
- a TensorFlow environment with GPU support if you want GPU training
- Android Studio for the Android module

Important Python notes:

- This project requires Python 3. Do not use Python 2.7.
- The repository is set up to use a local `.venv` in the project root.
- If `python` is not recognized in PowerShell, install Python 3 and enable the option to add it to `PATH`, or use the full path to `python.exe`.
- The training script now prefers GPU automatically, can use multiple GPUs, and can fail fast with `--require-gpu` if TensorFlow does not detect a GPU.

Windows GPU note:

- For the current Windows-native GPU setup in this repo, use Python `3.10`, `tensorflow-cpu==2.10`, and `tensorflow-directml-plugin`.
- A pinned native-Windows GPU dependency file is available at [`requirements-windows-directml.txt`](c:/Users/James/Documents/Aldrich_Proj/gore-detection-system/requirements-windows-directml.txt:1).
- The project `.venv` on this machine was created with Python `3.10.20` for DirectML GPU support.

Python packages used by the training scripts are listed in [`requirements.txt`](c:/Users/James/Documents/Aldrich_Proj/gore-detection-system/requirements.txt:1):

- `tensorflow`
- `numpy`
- `scikit-learn`
- `pillow`

## 1. Create or Reuse the Virtual Environment

If the project `.venv` already exists on this machine, use it directly:

```powershell
.\.venv\Scripts\Activate.ps1
python -c "import tensorflow as tf; print(tf.__version__); print(tf.config.list_physical_devices('GPU'))"
```

For this Windows GPU setup, the expected result is TensorFlow `2.10.0` and one or more detected GPU devices.

If you need to create the environment from scratch, use Python `3.10` for the native Windows DirectML path.

Check that Python 3 is available:

```powershell
python --version
```

Generic example if you are not targeting the Windows DirectML GPU path:

```powershell
python -m venv .venv
```

Activate it:

```powershell
.\.venv\Scripts\Activate.ps1
```

If PowerShell blocks script execution, run this once in the current terminal and then activate again:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

If `python` is not on `PATH`, use the full path to your Python 3 executable instead:

```powershell
"C:\Path\To\Python311\python.exe" -m venv .venv
```

For the Windows GPU setup, create the `.venv` with Python `3.10`:

```powershell
"C:\Path\To\Python310\python.exe" -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-windows-directml.txt
```

If you intentionally want the generic non-DirectML dependency set instead, install from [`requirements.txt`](c:/Users/James/Documents/Aldrich_Proj/gore-detection-system/requirements.txt:1):

```powershell
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

For the native Windows GPU setup used on this machine, install the pinned DirectML stack instead:

```powershell
python -m pip install -r requirements-windows-directml.txt
```

Verify that TensorFlow can see your GPU before training:

```powershell
python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
```

If the result is an empty list, the training script can still run on CPU unless you pass `--require-gpu`.

## 2. Prepare the Dataset

The scripts expect the raw images in these folders:

```text
dataset/raw/gore
dataset/raw/non_gore
```

Create the folders if needed:

```powershell
New-Item -ItemType Directory -Force dataset\raw\gore
New-Item -ItemType Directory -Force dataset\raw\non_gore
```

Supported image extensions:

- `.jpg`
- `.jpeg`
- `.png`
- `.bmp`
- `.webp`

Optional checks to confirm your raw data is present:

```powershell
Get-ChildItem dataset\raw\gore -Recurse -File | Measure-Object
Get-ChildItem dataset\raw\non_gore -Recurse -File | Measure-Object
```

## 3. Validate Raw Images and Generate Metadata

Run preprocessing:

```powershell
python model_training\scripts\preprocess.py --project-root .
```

Strict mode will exit with an error if any invalid image is found:

```powershell
python model_training\scripts\preprocess.py --project-root . --strict
```

Outputs generated by this step:

- `dataset/metadata/labels.csv`
- `dataset/metadata/dataset_info.csv`

## 4. Create Train, Validation, and Test Splits

Create fresh splits and clear any previous processed dataset:

```powershell
python model_training\scripts\split_dataset.py --project-root . --clean
```

Explicit example using the default ratios and seed:

```powershell
python model_training\scripts\split_dataset.py --project-root . --val-ratio 0.15 --test-ratio 0.15 --seed 42 --clean
```

The processed dataset is written to:

```text
dataset/processed/train
dataset/processed/val
dataset/processed/test
```

## 5. Train the Model

The training script now:

- prefers GPU automatically when TensorFlow detects one
- uses `OneDeviceStrategy` on the selected GPU by default
- can use `MirroredStrategy` when `--multi-gpu` is passed
- enables GPU memory growth to avoid grabbing all VRAM at startup
- can stop immediately if no GPU is available when `--require-gpu` is used

Supported model names:

- `custom_cnn`
- `mobilenetv2`
- `efficientnetb0`

Basic training command:

```powershell
python model_training\scripts\train_model.py --project-root .
```

Recommended GPU-enforced transfer-learning example:

```powershell
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --require-gpu --gpu-index 0
```

Training with fine-tuning and GPU required:

```powershell
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --fine-tune --fine-tune-epochs 3 --fine-tune-layers 30 --require-gpu --gpu-index 0
```

Example using EfficientNet with GPU required:

```powershell
python model_training\scripts\train_model.py --project-root . --model efficientnetb0 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --fine-tune --fine-tune-epochs 3 --fine-tune-layers 30 --require-gpu --gpu-index 0
```

Optional mixed precision on GPU:

```powershell
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --fine-tune --fine-tune-epochs 3 --fine-tune-layers 30 --require-gpu --gpu-index 0 --mixed-precision
```

Optional multi-GPU training across all detected GPUs:

```powershell
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --fine-tune --fine-tune-epochs 3 --fine-tune-layers 30 --require-gpu --multi-gpu
```

Training outputs:

- `model_training/saved_models/gore_classifier.keras`
- `model_training/logs/class_names.txt`
- `model_training/logs/metrics.csv`
- `model_training/logs/training_benchmark.csv`
- `model_training/logs/training_logs.txt`

`training_benchmark.csv` includes:

- per-epoch duration
- per-stage summary rows
- average samples-per-second across the run

`metrics.csv` also includes training benchmark summary values such as total training time, average epoch time, and average samples-per-second.

Important: in the current project snapshot, the files below exist as empty placeholders and must be regenerated before use:

- `model_training/saved_models/gore_classifier.keras`
- `model_training/saved_models/gore_classifier.tflite`

## 6. Evaluate the Trained Model

Run evaluation on `dataset/processed/test`:

```powershell
python model_training\scripts\evaluate_model.py --project-root .
```

Evaluation with an explicit threshold:

```powershell
python model_training\scripts\evaluate_model.py --project-root . --image-size 224 --batch-size 32 --threshold 0.5
```

Evaluation outputs:

- console metrics for accuracy, precision, recall, F1, and ROC AUC
- a terminal explanation of what each metric means
- a terminal verdict showing whether the current evaluation is better, worse, or mixed compared with the previous evaluation
- `model_training/logs/confusion_matrix.csv`
- `model_training/logs/evaluation_history.csv`
- additional evaluation rows appended to `model_training/logs/metrics.csv`

## 7. Run Predictions on One Image or a Folder

Predict one image:

```powershell
python model_training\scripts\predict_sample.py --project-root . --input path\to\image.jpg
```

Predict all supported images inside a folder:

```powershell
python model_training\scripts\predict_sample.py --project-root . --input path\to\folder
```

Predict a folder and save the results to CSV:

```powershell
python model_training\scripts\predict_sample.py --project-root . --input path\to\folder --output-csv model_training\logs\sample_predictions.csv
```

Use a custom model file and threshold:

```powershell
python model_training\scripts\predict_sample.py --project-root . --input path\to\folder --model-path model_training\saved_models\gore_classifier.keras --class-names-path model_training\logs\class_names.txt --image-size 224 --threshold 0.5
```

## 8. Convert the Keras Model to TensorFlow Lite

Convert with default output path:

```powershell
python model_training\scripts\convert_tflite.py --project-root .
```

Convert with float16 quantization:

```powershell
python model_training\scripts\convert_tflite.py --project-root . --float16
```

Use custom input and output paths:

```powershell
python model_training\scripts\convert_tflite.py --project-root . --model-path model_training\saved_models\gore_classifier.keras --output-path model_training\saved_models\gore_classifier.tflite --float16
```

Output:

- `model_training/saved_models/gore_classifier.tflite`

## 9. Benchmark Inference

Use the standalone inference benchmark to compare Keras and TFLite latency and throughput.

The Python TFLite benchmark in this repo uses the default TensorFlow Lite interpreter, which is CPU-based unless you add a delegate separately.

The benchmark script measures:

- model load time
- average latency
- p50 latency
- p95 latency
- min and max latency
- throughput in images per second

Benchmark the Keras model on GPU:

```powershell
python model_training\scripts\benchmark_inference.py --project-root . --backend keras --batch-size 1 --warmup-runs 10 --benchmark-runs 50 --require-gpu
```

Benchmark the TFLite model:

```powershell
python model_training\scripts\benchmark_inference.py --project-root . --backend tflite --batch-size 1 --warmup-runs 10 --benchmark-runs 50
```

Benchmark both backends and save the CSV to the default logs folder:

```powershell
python model_training\scripts\benchmark_inference.py --project-root . --backend both --batch-size 1 --warmup-runs 10 --benchmark-runs 50 --require-gpu
```

Benchmark outputs:

- `model_training/logs/inference_benchmark.csv`
- `model_training/logs/inference_benchmark_history.csv`
- entries appended to `model_training/logs/training_logs.txt`

The benchmark now also prints a terminal summary that:

- explains what each benchmark metric means
- compares the current benchmark to the previous run for the same backend
- tells you whether the result is better, worse, or mixed
- compares Keras and TFLite directly when both are benchmarked in the same run

## 10. Sync the Model Into the Android App Assets

Copy the latest `.tflite` model and labels into the Android assets folder:

```powershell
python model_training\scripts\sync_android_assets.py --project-root .
```

Use custom paths if needed:

```powershell
python model_training\scripts\sync_android_assets.py --project-root . --model-path model_training\saved_models\gore_classifier.tflite --labels-path model_training\logs\class_names.txt --assets-dir android\app\src\main\assets
```

Files written by this step:

- `android/app/src/main/assets/gore_classifier.tflite`
- `android/app/src/main/assets/labels.txt`

If `model_training/logs/class_names.txt` is missing or empty, `labels.txt` falls back to:

```text
gore
non_gore
```

## 11. Android Build Notes

The current repository snapshot contains:

- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- `android/gradle/wrapper/gradle-wrapper.properties`
- `android/gradle/wrapper/gradle-wrapper.jar`

But it does not currently contain the usual Android project root files such as:

- `android/settings.gradle.kts`
- `android/build.gradle.kts`
- `android/gradlew.bat`
- `android/gradlew`

Because of that, full command-line Gradle builds cannot be run from this snapshot alone.

If you restore or regenerate the missing Android project root files, these are the standard commands you would run from inside `android/`:

```powershell
cd android
.\gradlew.bat tasks
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
```

Until then, the practical workflow is:

1. Generate the model with the Python scripts.
2. Run `sync_android_assets.py`.
3. Open the Android module in Android Studio.
4. Restore or recreate the missing project-level Gradle files if you need CLI builds.

## 12. Full End-to-End Command Sequence

Use this sequence for a clean run after placing images in `dataset/raw/gore` and `dataset/raw/non_gore`:

```powershell
cd C:\Users\James\Documents\Aldrich_Proj\gore-detection-system
.\.venv\Scripts\Activate.ps1
python -c "import tensorflow as tf; print(tf.__version__); print(tf.config.list_physical_devices('GPU'))"
python model_training\scripts\preprocess.py --project-root .
python model_training\scripts\split_dataset.py --project-root . --val-ratio 0.15 --test-ratio 0.15 --seed 42 --clean
python model_training\scripts\train_model.py --project-root . --model mobilenetv2 --image-size 224 --batch-size 32 --epochs 10 --learning-rate 0.0001 --fine-tune --fine-tune-epochs 3 --fine-tune-layers 30 --require-gpu --gpu-index 0
python model_training\scripts\evaluate_model.py --project-root . --image-size 224 --batch-size 32 --threshold 0.5
python model_training\scripts\convert_tflite.py --project-root . --float16
python model_training\scripts\benchmark_inference.py --project-root . --backend both --batch-size 1 --warmup-runs 10 --benchmark-runs 50 --require-gpu
python model_training\scripts\sync_android_assets.py --project-root .
```

If you need to rebuild the verified Windows GPU environment first:

```powershell
cd C:\Users\James\Documents\Aldrich_Proj\gore-detection-system
"C:\Path\To\Python310\python.exe" -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-windows-directml.txt
python -c "import tensorflow as tf; print(tf.__version__); print(tf.config.list_physical_devices('GPU'))"
```

## 13. Common Problems

`PowerShell says running scripts is disabled`

Run:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

`python is not recognized`

- Install Python 3 first.
- Enable `Add python.exe to PATH` during installation, or use the full path to `python.exe`.
- This project cannot be set up with Python 2.7.

`No GPU detected by TensorFlow`

- Run:

```powershell
python -c "import tensorflow as tf; print(tf.config.list_physical_devices('GPU'))"
```

- If the output is `[]`, TensorFlow cannot currently see a GPU.
- Remove `--require-gpu` only if you intentionally want CPU fallback.
- On this Windows-native setup, make sure `.venv` uses Python `3.10` with `tensorflow-cpu==2.10` and `tensorflow-directml-plugin`.

`TensorFlow import fails with NumPy compatibility errors`

- For the native Windows DirectML setup, keep `numpy<2`.
- Install from [`requirements-windows-directml.txt`](c:/Users/James/Documents/Aldrich_Proj/gore-detection-system/requirements-windows-directml.txt:1) to avoid incompatible versions.

`No valid records found`

- Make sure `dataset/raw/gore` and `dataset/raw/non_gore` contain supported image files.
- Run `preprocess.py` before `split_dataset.py`.

`Missing train/val directories in dataset/processed`

- Run:

```powershell
python model_training\scripts\split_dataset.py --project-root . --clean
```

`Trained model missing or empty`

- Run `train_model.py` before `evaluate_model.py` or `convert_tflite.py`.
- In this snapshot, the saved model files are currently empty placeholders until you generate new ones.

`Model file missing or empty during Android asset sync`

- Run `convert_tflite.py` first.

`TFLite benchmark was skipped or failed because the model is missing`

- Run:

```powershell
python model_training\scripts\convert_tflite.py --project-root . --float16
```

- Then rerun `benchmark_inference.py`.

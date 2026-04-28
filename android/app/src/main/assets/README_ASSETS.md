# Android Assets

This folder is aligned with the training pipeline output.

Expected runtime assets:
- gore_classifier.tflite
- labels.txt

Important:
- gore_classifier.tflite must be a valid, non-empty exported model.
- Placeholder files with 0 bytes will fail model self-test and live detection.

To sync the latest model from the project root:

python model_training/scripts/sync_android_assets.py --project-root .

If class names are unavailable from training logs, labels.txt defaults to:
- gore
- non_gore

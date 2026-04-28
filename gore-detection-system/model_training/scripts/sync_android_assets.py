from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Sync trained model artifacts to the Android app assets folder."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument(
        "--model-path",
        type=Path,
        default=None,
        help="Path to source .tflite model. Defaults to model_training/saved_models/gore_classifier.tflite",
    )
    parser.add_argument(
        "--labels-path",
        type=Path,
        default=None,
        help="Path to labels file. Defaults to model_training/logs/class_names.txt",
    )
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=None,
        help="Android assets destination. Defaults to android/app/src/main/assets",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_root = args.project_root.resolve()

    model_path = args.model_path
    if model_path is None:
        model_path = project_root / "model_training" / "saved_models" / "gore_classifier.tflite"
    model_path = model_path.resolve()

    labels_path = args.labels_path
    if labels_path is None:
        labels_path = project_root / "model_training" / "logs" / "class_names.txt"
    labels_path = labels_path.resolve()

    assets_dir = args.assets_dir
    if assets_dir is None:
        assets_dir = project_root / "android" / "app" / "src" / "main" / "assets"
    assets_dir = assets_dir.resolve()
    assets_dir.mkdir(parents=True, exist_ok=True)

    if not model_path.exists() or model_path.stat().st_size == 0:
        raise FileNotFoundError(
            f"Model file missing or empty: {model_path}. Run convert_tflite.py first."
        )

    destination_model = assets_dir / "gore_classifier.tflite"
    shutil.copy2(model_path, destination_model)

    destination_labels = assets_dir / "labels.txt"
    if labels_path.exists() and labels_path.stat().st_size > 0:
        labels_content = labels_path.read_text(encoding="utf-8").strip()
        if labels_content:
            destination_labels.write_text(labels_content + "\n", encoding="utf-8")
        else:
            destination_labels.write_text("gore\nnon_gore\n", encoding="utf-8")
    else:
        destination_labels.write_text("gore\nnon_gore\n", encoding="utf-8")

    print(f"Copied model to: {destination_model}")
    print(f"Wrote labels to: {destination_labels}")
    print("Android assets sync complete.")


if __name__ == "__main__":
    main()

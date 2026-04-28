from __future__ import annotations

import argparse
import csv
from pathlib import Path

import numpy as np
import tensorflow as tf

SUPPORTED_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Run gore detection inference for one image or a folder of images."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--model-path", type=Path, default=None)
    parser.add_argument("--class-names-path", type=Path, default=None)
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--output-csv", type=Path, default=None)
    return parser.parse_args()


def load_class_names(class_names_path: Path | None) -> list[str]:
    if class_names_path is None or not class_names_path.exists():
        return ["gore", "non_gore"]

    names = [line.strip() for line in class_names_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if len(names) == 2:
        return names
    return ["gore", "non_gore"]


def collect_image_paths(input_path: Path) -> list[Path]:
    if input_path.is_file() and input_path.suffix.lower() in SUPPORTED_SUFFIXES:
        return [input_path]

    if input_path.is_dir():
        return sorted(
            path
            for path in input_path.rglob("*")
            if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
        )

    return []


def predict_image(
    model: tf.keras.Model,
    image_path: Path,
    image_size: int,
    gore_index: int,
    threshold: float,
) -> dict[str, str | float]:
    image = tf.keras.utils.load_img(image_path, target_size=(image_size, image_size))
    image_array = tf.keras.utils.img_to_array(image)
    batch = np.expand_dims(image_array, axis=0)

    non_gore_probability = float(model.predict(batch, verbose=0)[0][0])

    if gore_index == 0:
        gore_probability = 1.0 - non_gore_probability
    else:
        gore_probability = non_gore_probability

    predicted_label = "gore" if gore_probability >= threshold else "non_gore"

    return {
        "image_path": str(image_path),
        "predicted_label": predicted_label,
        "gore_probability": gore_probability,
        "non_gore_probability": non_gore_probability,
    }


def main() -> None:
    args = parse_args()
    project_root = args.project_root.resolve()

    model_path = args.model_path
    if model_path is None:
        model_path = project_root / "model_training" / "saved_models" / "gore_classifier.keras"
    model_path = model_path.resolve()

    if not model_path.exists() or model_path.stat().st_size == 0:
        raise FileNotFoundError(
            f"Trained model missing or empty at {model_path}. Run train_model.py first."
        )

    class_names_path = args.class_names_path
    if class_names_path is None:
        class_names_path = project_root / "model_training" / "logs" / "class_names.txt"

    class_names = load_class_names(class_names_path)
    gore_index = class_names.index("gore") if "gore" in class_names else 0

    image_paths = collect_image_paths(args.input.resolve())
    if not image_paths:
        raise SystemExit("No supported image files found for inference.")

    model = tf.keras.models.load_model(model_path)

    results = [
        predict_image(model, image_path, args.image_size, gore_index, args.threshold)
        for image_path in image_paths
    ]

    for result in results:
        print(
            f"{result['image_path']} -> {result['predicted_label']} "
            f"(gore={result['gore_probability']:.4f}, non_gore={result['non_gore_probability']:.4f})"
        )

    if args.output_csv is not None:
        output_csv = args.output_csv.resolve()
        output_csv.parent.mkdir(parents=True, exist_ok=True)

        with output_csv.open("w", newline="", encoding="utf-8") as csv_file:
            writer = csv.DictWriter(
                csv_file,
                fieldnames=[
                    "image_path",
                    "predicted_label",
                    "gore_probability",
                    "non_gore_probability",
                ],
            )
            writer.writeheader()
            writer.writerows(results)

        print(f"Predictions saved to: {output_csv}")


if __name__ == "__main__":
    main()

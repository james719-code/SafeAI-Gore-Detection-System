from __future__ import annotations

import argparse
from pathlib import Path

import tensorflow as tf


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Convert a trained Keras gore classifier to TFLite format."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument("--model-path", type=Path, default=None)
    parser.add_argument("--output-path", type=Path, default=None)
    parser.add_argument(
        "--float16",
        action="store_true",
        help="Enable float16 post-training quantization for smaller model size.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_root = args.project_root.resolve()

    model_path = args.model_path
    if model_path is None:
        model_path = project_root / "model_training" / "saved_models" / "gore_classifier.keras"
    model_path = model_path.resolve()

    output_path = args.output_path
    if output_path is None:
        output_path = project_root / "model_training" / "saved_models" / "gore_classifier.tflite"
    output_path = output_path.resolve()

    if not model_path.exists() or model_path.stat().st_size == 0:
        raise FileNotFoundError(
            f"Trained model missing or empty at {model_path}. Run train_model.py first."
        )

    keras_model = tf.keras.models.load_model(model_path)

    converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
    if args.float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)

    interpreter = tf.lite.Interpreter(model_path=str(output_path))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"TFLite model saved to: {output_path}")
    print(f"Input tensors: {input_details}")
    print(f"Output tensors: {output_details}")


if __name__ == "__main__":
    main()

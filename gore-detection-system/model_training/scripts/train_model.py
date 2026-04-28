from __future__ import annotations

import argparse
import csv
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

import tensorflow as tf

AUTOTUNE = tf.data.AUTOTUNE


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Train a gore vs non_gore binary image classifier."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument(
        "--model",
        choices=["custom_cnn", "mobilenetv2", "efficientnetb0"],
        default="mobilenetv2",
    )
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--learning-rate", type=float, default=1e-4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--gpu-index",
        type=int,
        default=0,
        help="GPU index to use when one or more GPUs are available. Defaults to 0.",
    )
    parser.add_argument(
        "--multi-gpu",
        action="store_true",
        help="Use MirroredStrategy across all detected GPUs instead of a single selected GPU.",
    )
    parser.add_argument(
        "--require-gpu",
        action="store_true",
        help="Exit if TensorFlow cannot detect at least one GPU.",
    )
    parser.add_argument(
        "--mixed-precision",
        action="store_true",
        help="Enable mixed_float16 policy when training on GPU.",
    )
    parser.add_argument(
        "--fine-tune",
        action="store_true",
        help="Enable a second fine-tuning stage for transfer-learning models.",
    )
    parser.add_argument(
        "--fine-tune-epochs",
        type=int,
        default=3,
        help="Number of epochs for fine-tuning stage.",
    )
    parser.add_argument(
        "--fine-tune-layers",
        type=int,
        default=30,
        help="Number of last base-model layers to keep trainable during fine-tuning.",
    )
    return parser.parse_args()


def build_data_augmentation() -> tf.keras.Sequential:
    return tf.keras.Sequential(
        [
            tf.keras.layers.RandomFlip("horizontal"),
            tf.keras.layers.RandomRotation(0.05),
            tf.keras.layers.RandomZoom(0.1),
        ],
        name="augmentation",
    )


def load_datasets(
    processed_dir: Path,
    image_size: int,
    batch_size: int,
    seed: int,
) -> tuple[tf.data.Dataset, tf.data.Dataset, list[str], int, int]:
    train_dir = processed_dir / "train"
    val_dir = processed_dir / "val"

    if not train_dir.exists() or not val_dir.exists():
        raise FileNotFoundError(
            "Missing train/val directories in dataset/processed. Run split_dataset.py first."
        )

    train_ds = tf.keras.utils.image_dataset_from_directory(
        train_dir,
        labels="inferred",
        label_mode="binary",
        image_size=(image_size, image_size),
        batch_size=batch_size,
        shuffle=True,
        seed=seed,
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        val_dir,
        labels="inferred",
        label_mode="binary",
        image_size=(image_size, image_size),
        batch_size=batch_size,
        shuffle=False,
    )

    class_names = train_ds.class_names
    if len(class_names) != 2:
        raise ValueError(
            f"Expected exactly 2 classes for binary classification, found: {class_names}"
        )

    train_count = len(getattr(train_ds, "file_paths", []))
    val_count = len(getattr(val_ds, "file_paths", []))

    train_ds = train_ds.prefetch(AUTOTUNE)
    val_ds = val_ds.prefetch(AUTOTUNE)
    return train_ds, val_ds, class_names, train_count, val_count


def configure_training_runtime(
    gpu_index: int,
    use_multi_gpu: bool,
    require_gpu: bool,
    use_mixed_precision: bool,
) -> tuple[tf.distribute.Strategy, str, list[str]]:
    gpu_devices = tf.config.list_physical_devices("GPU")

    for gpu in gpu_devices:
        try:
            tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError:
            # TensorFlow raises once the runtime is initialized; training can still continue.
            pass

    if gpu_devices:
        if gpu_index < 0 or gpu_index >= len(gpu_devices):
            raise ValueError(
                f"gpu-index must be between 0 and {len(gpu_devices) - 1} for the detected GPUs."
            )

        if use_multi_gpu and len(gpu_devices) > 1:
            strategy = tf.distribute.MirroredStrategy()
            runtime_name = "multi_gpu"
        else:
            strategy = tf.distribute.OneDeviceStrategy(device=f"/GPU:{gpu_index}")
            runtime_name = "gpu"

        if use_mixed_precision:
            tf.keras.mixed_precision.set_global_policy("mixed_float16")
    else:
        if require_gpu:
            raise SystemExit(
                "No GPU detected by TensorFlow. Install a GPU-enabled TensorFlow stack and verify "
                "that tf.config.list_physical_devices('GPU') returns at least one device."
            )

        strategy = tf.distribute.get_strategy()
        runtime_name = "cpu"

        if tf.keras.mixed_precision.global_policy().name != "float32":
            tf.keras.mixed_precision.set_global_policy("float32")

    device_names = [device.name for device in gpu_devices]
    if not device_names:
        device_names = [device.name for device in tf.config.list_physical_devices("CPU")]

    return strategy, runtime_name, device_names


def build_custom_cnn(image_size: int, augmentation: tf.keras.Sequential) -> tuple[tf.keras.Model, None]:
    inputs = tf.keras.Input(shape=(image_size, image_size, 3))
    x = tf.keras.layers.Rescaling(1.0 / 255.0)(inputs)
    x = augmentation(x)
    x = tf.keras.layers.Conv2D(32, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.MaxPooling2D()(x)
    x = tf.keras.layers.Conv2D(64, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.MaxPooling2D()(x)
    x = tf.keras.layers.Conv2D(128, 3, padding="same", activation="relu")(x)
    x = tf.keras.layers.MaxPooling2D()(x)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    outputs = tf.keras.layers.Dense(1, activation="sigmoid", dtype="float32")(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="custom_cnn")
    return model, None


def build_mobilenetv2(
    image_size: int, augmentation: tf.keras.Sequential
) -> tuple[tf.keras.Model, tf.keras.Model]:
    inputs = tf.keras.Input(shape=(image_size, image_size, 3))

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(image_size, image_size, 3),
        include_top=False,
        weights="imagenet",
    )
    base_model.trainable = False

    x = augmentation(inputs)
    x = tf.keras.applications.mobilenet_v2.preprocess_input(x)
    x = base_model(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(1, activation="sigmoid", dtype="float32")(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="mobilenetv2_transfer")
    return model, base_model


def build_efficientnetb0(
    image_size: int, augmentation: tf.keras.Sequential
) -> tuple[tf.keras.Model, tf.keras.Model]:
    inputs = tf.keras.Input(shape=(image_size, image_size, 3))

    base_model = tf.keras.applications.EfficientNetB0(
        input_shape=(image_size, image_size, 3),
        include_top=False,
        weights="imagenet",
    )
    base_model.trainable = False

    x = augmentation(inputs)
    x = tf.keras.applications.efficientnet.preprocess_input(x)
    x = base_model(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    outputs = tf.keras.layers.Dense(1, activation="sigmoid", dtype="float32")(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="efficientnetb0_transfer")
    return model, base_model


def build_model(
    model_name: str,
    image_size: int,
    augmentation: tf.keras.Sequential,
) -> tuple[tf.keras.Model, Optional[tf.keras.Model]]:
    if model_name == "custom_cnn":
        return build_custom_cnn(image_size, augmentation)
    if model_name == "mobilenetv2":
        return build_mobilenetv2(image_size, augmentation)
    if model_name == "efficientnetb0":
        return build_efficientnetb0(image_size, augmentation)

    raise ValueError(f"Unsupported model type: {model_name}")


def compile_model(model: tf.keras.Model, learning_rate: float) -> None:
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss="binary_crossentropy",
        metrics=[
            tf.keras.metrics.BinaryAccuracy(name="accuracy"),
            tf.keras.metrics.Precision(name="precision"),
            tf.keras.metrics.Recall(name="recall"),
            tf.keras.metrics.AUC(name="auc"),
        ],
    )


def append_training_log(log_path: Path, event: str, details: str) -> None:
    timestamp = datetime.utcnow().isoformat(timespec="seconds")
    with log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(f"{timestamp},{event},{details}\n")


class BenchmarkCallback(tf.keras.callbacks.Callback):
    def __init__(self, stage_name: str, samples_per_epoch: int) -> None:
        super().__init__()
        self.stage_name = stage_name
        self.samples_per_epoch = samples_per_epoch
        self.rows: list[dict[str, float | int | str]] = []
        self._epoch_start_time = 0.0
        self.total_duration_seconds = 0.0

    def on_train_begin(self, logs: Optional[dict[str, float]] = None) -> None:
        self.total_duration_seconds = 0.0

    def on_epoch_begin(self, epoch: int, logs: Optional[dict[str, float]] = None) -> None:
        self._epoch_start_time = time.perf_counter()

    def on_epoch_end(self, epoch: int, logs: Optional[dict[str, float]] = None) -> None:
        duration_seconds = time.perf_counter() - self._epoch_start_time
        self.total_duration_seconds += duration_seconds
        throughput = (
            self.samples_per_epoch / duration_seconds if duration_seconds > 0 else 0.0
        )
        row: dict[str, float | int | str] = {
            "row_type": "epoch",
            "stage": self.stage_name,
            "epoch": epoch + 1,
            "duration_seconds": duration_seconds,
            "samples": self.samples_per_epoch,
            "samples_per_second": throughput,
        }

        if logs:
            for metric_name in ("loss", "accuracy", "auc", "val_loss", "val_accuracy", "val_auc"):
                metric_value = logs.get(metric_name)
                if metric_value is not None:
                    row[metric_name] = float(metric_value)

        self.rows.append(row)

    @property
    def epoch_count(self) -> int:
        return len(self.rows)

    @property
    def total_samples(self) -> int:
        return self.samples_per_epoch * self.epoch_count

    @property
    def average_samples_per_second(self) -> float:
        if self.total_duration_seconds <= 0:
            return 0.0
        return self.total_samples / self.total_duration_seconds


def summarize_benchmarks(
    callbacks: list[BenchmarkCallback],
) -> dict[str, float]:
    total_duration = sum(callback.total_duration_seconds for callback in callbacks)
    total_epochs = sum(callback.epoch_count for callback in callbacks)
    total_samples = sum(callback.total_samples for callback in callbacks)

    summary = {
        "total_train_seconds": total_duration,
        "total_epochs_ran": float(total_epochs),
        "total_train_samples_seen": float(total_samples),
    }

    if total_epochs > 0:
        summary["avg_epoch_seconds"] = total_duration / total_epochs
    if total_duration > 0:
        summary["avg_samples_per_second"] = total_samples / total_duration

    return summary


def write_training_benchmark_csv(
    output_path: Path,
    callbacks: list[BenchmarkCallback],
) -> None:
    fieldnames = [
        "row_type",
        "stage",
        "epoch",
        "duration_seconds",
        "samples",
        "samples_per_second",
        "loss",
        "accuracy",
        "auc",
        "val_loss",
        "val_accuracy",
        "val_auc",
    ]

    with output_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()

        for callback in callbacks:
            for row in callback.rows:
                writer.writerow(row)

            writer.writerow(
                {
                    "row_type": "summary",
                    "stage": callback.stage_name,
                    "epoch": callback.epoch_count,
                    "duration_seconds": callback.total_duration_seconds,
                    "samples": callback.total_samples,
                    "samples_per_second": callback.average_samples_per_second,
                }
            )

        overall_summary = summarize_benchmarks(callbacks)
        writer.writerow(
            {
                "row_type": "summary",
                "stage": "overall",
                "epoch": int(overall_summary.get("total_epochs_ran", 0.0)),
                "duration_seconds": overall_summary.get("total_train_seconds", 0.0),
                "samples": int(overall_summary.get("total_train_samples_seen", 0.0)),
                "samples_per_second": overall_summary.get("avg_samples_per_second", 0.0),
            }
        )


def write_summary_metrics(
    metrics_path: Path,
    history: dict[str, list[float]],
    benchmark_summary: Optional[dict[str, float]] = None,
) -> None:
    metric_rows: list[tuple[str, str, float]] = []

    for metric_name, values in history.items():
        if values:
            metric_rows.append(("training", f"final_{metric_name}", float(values[-1])))

    if history.get("val_auc"):
        metric_rows.append(("training", "best_val_auc", float(max(history["val_auc"]))))

    if benchmark_summary:
        for metric_name, value in benchmark_summary.items():
            metric_rows.append(("training", metric_name, float(value)))

    with metrics_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["stage", "metric", "value"])
        for stage, metric, value in metric_rows:
            writer.writerow([stage, metric, value])


def merge_history(
    primary: tf.keras.callbacks.History,
    secondary: Optional[tf.keras.callbacks.History] = None,
) -> dict[str, list[float]]:
    merged: dict[str, list[float]] = {
        key: list(values) for key, values in primary.history.items()
    }

    if secondary is None:
        return merged

    for key, values in secondary.history.items():
        merged.setdefault(key, []).extend(values)

    return merged


def main() -> None:
    args = parse_args()
    project_root = args.project_root.resolve()

    processed_dir = project_root / "dataset" / "processed"
    saved_models_dir = project_root / "model_training" / "saved_models"
    logs_dir = project_root / "model_training" / "logs"

    saved_models_dir.mkdir(parents=True, exist_ok=True)
    logs_dir.mkdir(parents=True, exist_ok=True)

    metrics_path = logs_dir / "metrics.csv"
    benchmark_path = logs_dir / "training_benchmark.csv"
    training_logs_path = logs_dir / "training_logs.txt"
    class_names_path = logs_dir / "class_names.txt"
    best_model_path = saved_models_dir / "gore_classifier.keras"

    strategy, runtime_name, device_names = configure_training_runtime(
        gpu_index=args.gpu_index,
        use_multi_gpu=args.multi_gpu,
        require_gpu=args.require_gpu,
        use_mixed_precision=args.mixed_precision,
    )

    tf.keras.utils.set_random_seed(args.seed)

    train_ds, val_ds, class_names, train_count, val_count = load_datasets(
        processed_dir=processed_dir,
        image_size=args.image_size,
        batch_size=args.batch_size,
        seed=args.seed,
    )

    class_names_path.write_text("\n".join(class_names), encoding="utf-8")
    append_training_log(training_logs_path, "class_names", "|".join(class_names))
    append_training_log(
        training_logs_path,
        "runtime",
        (
            f"type={runtime_name},devices={'|'.join(device_names)},"
            f"strategy={strategy.__class__.__name__},"
            f"mixed_precision={tf.keras.mixed_precision.global_policy().name}"
        ),
    )

    print(f"Training runtime: {runtime_name}")
    print(f"Visible devices: {', '.join(device_names)}")
    print(f"Distribution strategy: {strategy.__class__.__name__}")
    print(f"Mixed precision policy: {tf.keras.mixed_precision.global_policy().name}")
    print(f"Training samples: {train_count}")
    print(f"Validation samples: {val_count}")

    with strategy.scope():
        augmentation = build_data_augmentation()
        model, base_model = build_model(args.model, args.image_size, augmentation)
        compile_model(model, args.learning_rate)

    callbacks: list[tf.keras.callbacks.Callback] = [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(best_model_path),
            monitor="val_auc",
            mode="max",
            save_best_only=True,
            verbose=1,
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_auc",
            mode="max",
            patience=3,
            restore_best_weights=True,
            verbose=1,
        ),
    ]

    append_training_log(training_logs_path, "train_start", f"model={args.model}")

    initial_benchmark_callback = BenchmarkCallback(
        stage_name="initial_train",
        samples_per_epoch=train_count,
    )
    initial_history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=args.epochs,
        callbacks=[*callbacks, initial_benchmark_callback],
        verbose=1,
    )

    fine_tune_history: Optional[tf.keras.callbacks.History] = None
    benchmark_callbacks = [initial_benchmark_callback]

    if args.fine_tune and base_model is not None:
        base_model.trainable = True

        if args.fine_tune_layers > 0 and args.fine_tune_layers < len(base_model.layers):
            freeze_until = len(base_model.layers) - args.fine_tune_layers
            for layer in base_model.layers[:freeze_until]:
                layer.trainable = False

        with strategy.scope():
            compile_model(model, args.learning_rate * 0.1)
        append_training_log(
            training_logs_path,
            "fine_tune_start",
            f"layers={args.fine_tune_layers},epochs={args.fine_tune_epochs}",
        )

        fine_tune_benchmark_callback = BenchmarkCallback(
            stage_name="fine_tune",
            samples_per_epoch=train_count,
        )
        fine_tune_history = model.fit(
            train_ds,
            validation_data=val_ds,
            epochs=args.epochs + args.fine_tune_epochs,
            initial_epoch=args.epochs,
            callbacks=[*callbacks, fine_tune_benchmark_callback],
            verbose=1,
        )
        benchmark_callbacks.append(fine_tune_benchmark_callback)

    if best_model_path.exists():
        trained_model = tf.keras.models.load_model(best_model_path)
        trained_model.save(best_model_path)
    else:
        model.save(best_model_path)

    merged_history = merge_history(initial_history, fine_tune_history)
    benchmark_summary = summarize_benchmarks(benchmark_callbacks)
    write_summary_metrics(metrics_path, merged_history, benchmark_summary)
    write_training_benchmark_csv(benchmark_path, benchmark_callbacks)

    append_training_log(
        training_logs_path,
        "train_complete",
        f"saved_model={best_model_path}",
    )
    append_training_log(
        training_logs_path,
        "training_benchmark",
        (
            f"total_seconds={benchmark_summary.get('total_train_seconds', 0.0):.4f},"
            f"epochs={int(benchmark_summary.get('total_epochs_ran', 0.0))},"
            f"samples_per_second={benchmark_summary.get('avg_samples_per_second', 0.0):.4f}"
        ),
    )

    print(f"Training complete. Model saved to: {best_model_path}")
    print(f"Metrics summary saved to: {metrics_path}")
    print(f"Training benchmark saved to: {benchmark_path}")
    print(
        "Training benchmark summary: "
        f"total_seconds={benchmark_summary.get('total_train_seconds', 0.0):.2f}, "
        f"avg_epoch_seconds={benchmark_summary.get('avg_epoch_seconds', 0.0):.2f}, "
        f"avg_samples_per_second={benchmark_summary.get('avg_samples_per_second', 0.0):.2f}"
    )


if __name__ == "__main__":
    main()

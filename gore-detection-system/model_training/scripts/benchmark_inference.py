from __future__ import annotations

import argparse
import csv
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Benchmark Keras and TFLite inference latency and throughput."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument(
        "--backend",
        choices=["keras", "tflite", "both"],
        default="both",
        help="Which inference backend to benchmark.",
    )
    parser.add_argument("--keras-model-path", type=Path, default=None)
    parser.add_argument("--tflite-model-path", type=Path, default=None)
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--warmup-runs", type=int, default=10)
    parser.add_argument("--benchmark-runs", type=int, default=50)
    parser.add_argument(
        "--require-gpu",
        action="store_true",
        help="Exit if TensorFlow cannot detect a GPU for the Keras benchmark.",
    )
    parser.add_argument("--output-csv", type=Path, default=None)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def append_training_log(log_path: Path, event: str, details: str) -> None:
    timestamp = datetime.utcnow().isoformat(timespec="seconds")
    with log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(f"{timestamp},{event},{details}\n")


def configure_runtime(require_gpu: bool) -> tuple[str, list[str]]:
    gpu_devices = tf.config.list_physical_devices("GPU")

    for gpu in gpu_devices:
        try:
            tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError:
            pass

    if require_gpu and not gpu_devices:
        raise SystemExit(
            "No GPU detected by TensorFlow. Install a GPU-enabled TensorFlow stack and verify "
            "that tf.config.list_physical_devices('GPU') returns at least one device."
        )

    if gpu_devices:
        runtime_name = "multi_gpu" if len(gpu_devices) > 1 else "gpu"
        device_names = [device.name for device in gpu_devices]
    else:
        runtime_name = "cpu"
        device_names = [device.name for device in tf.config.list_physical_devices("CPU")]

    return runtime_name, device_names


def make_random_input(
    shape: tuple[int, ...],
    dtype: np.dtype,
    seed: int,
) -> np.ndarray:
    rng = np.random.default_rng(seed)
    numpy_dtype = np.dtype(dtype)

    if np.issubdtype(numpy_dtype, np.floating):
        return rng.random(shape, dtype=np.float32).astype(numpy_dtype)

    if np.issubdtype(numpy_dtype, np.integer):
        info = np.iinfo(numpy_dtype)
        return rng.integers(
            low=int(info.min),
            high=int(info.max) + 1,
            size=shape,
            dtype=np.int32,
        ).astype(numpy_dtype)

    raise TypeError(f"Unsupported inference dtype for benchmarking: {numpy_dtype}")


def summarize_latencies(
    latencies_seconds: list[float],
    batch_size: int,
) -> dict[str, float]:
    latency_ms = np.array(latencies_seconds, dtype=np.float64) * 1000.0
    total_seconds = float(np.sum(latencies_seconds))
    total_images = batch_size * len(latencies_seconds)

    summary = {
        "avg_latency_ms": float(np.mean(latency_ms)),
        "p50_latency_ms": float(np.percentile(latency_ms, 50)),
        "p95_latency_ms": float(np.percentile(latency_ms, 95)),
        "min_latency_ms": float(np.min(latency_ms)),
        "max_latency_ms": float(np.max(latency_ms)),
        "throughput_images_per_second": 0.0,
    }

    if total_seconds > 0:
        summary["throughput_images_per_second"] = total_images / total_seconds

    return summary


def benchmark_keras(
    model_path: Path,
    image_size: int,
    batch_size: int,
    warmup_runs: int,
    benchmark_runs: int,
    require_gpu: bool,
    seed: int,
) -> dict[str, str | float | int]:
    runtime_name, device_names = configure_runtime(require_gpu=require_gpu)

    load_start = time.perf_counter()
    model = tf.keras.models.load_model(model_path)
    load_time_seconds = time.perf_counter() - load_start

    input_batch = tf.convert_to_tensor(
        make_random_input(
            shape=(batch_size, image_size, image_size, 3),
            dtype=np.float32,
            seed=seed,
        )
    )

    for _ in range(warmup_runs):
        output = model(input_batch, training=False)
        _ = output.numpy()

    latencies_seconds: list[float] = []
    for _ in range(benchmark_runs):
        start_time = time.perf_counter()
        output = model(input_batch, training=False)
        _ = output.numpy()
        latencies_seconds.append(time.perf_counter() - start_time)

    row: dict[str, str | float | int] = {
        "backend": "keras",
        "runtime": runtime_name,
        "devices": "|".join(device_names),
        "model_path": str(model_path),
        "load_time_ms": load_time_seconds * 1000.0,
        "requested_batch_size": batch_size,
        "actual_batch_size": batch_size,
        "warmup_runs": warmup_runs,
        "benchmark_runs": benchmark_runs,
        "input_dtype": "float32",
    }
    row.update(summarize_latencies(latencies_seconds, batch_size))
    return row


def benchmark_tflite(
    model_path: Path,
    batch_size: int,
    warmup_runs: int,
    benchmark_runs: int,
    seed: int,
) -> dict[str, str | float | int]:
    load_start = time.perf_counter()
    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()

    requested_shape = list(input_details[0]["shape"])
    if requested_shape and requested_shape[0] != batch_size:
        requested_shape[0] = batch_size
        try:
            interpreter.resize_tensor_input(
                input_details[0]["index"],
                requested_shape,
                strict=False,
            )
        except ValueError:
            pass

    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    load_time_seconds = time.perf_counter() - load_start

    input_shape = tuple(int(value) for value in input_details[0]["shape"])
    input_dtype = input_details[0]["dtype"]
    actual_batch_size = int(input_shape[0]) if input_shape else batch_size
    input_batch = make_random_input(input_shape, input_dtype, seed=seed)

    for _ in range(warmup_runs):
        interpreter.set_tensor(input_details[0]["index"], input_batch)
        interpreter.invoke()
        _ = interpreter.get_tensor(output_details[0]["index"])

    latencies_seconds: list[float] = []
    for _ in range(benchmark_runs):
        start_time = time.perf_counter()
        interpreter.set_tensor(input_details[0]["index"], input_batch)
        interpreter.invoke()
        _ = interpreter.get_tensor(output_details[0]["index"])
        latencies_seconds.append(time.perf_counter() - start_time)

    row: dict[str, str | float | int] = {
        "backend": "tflite",
        "runtime": "tflite_cpu",
        "devices": "CPU",
        "model_path": str(model_path),
        "load_time_ms": load_time_seconds * 1000.0,
        "requested_batch_size": batch_size,
        "actual_batch_size": actual_batch_size,
        "warmup_runs": warmup_runs,
        "benchmark_runs": benchmark_runs,
        "input_dtype": np.dtype(input_dtype).name,
    }
    row.update(summarize_latencies(latencies_seconds, actual_batch_size))
    return row


def write_benchmark_csv(
    output_path: Path,
    rows: list[dict[str, str | float | int]],
) -> None:
    fieldnames = [
        "backend",
        "runtime",
        "devices",
        "model_path",
        "load_time_ms",
        "requested_batch_size",
        "actual_batch_size",
        "warmup_runs",
        "benchmark_runs",
        "input_dtype",
        "avg_latency_ms",
        "p50_latency_ms",
        "p95_latency_ms",
        "min_latency_ms",
        "max_latency_ms",
        "throughput_images_per_second",
    ]

    with output_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def read_previous_benchmark_history(
    history_path: Path,
) -> dict[str, dict[str, str]]:
    if not history_path.exists():
        return {}

    with history_path.open("r", newline="", encoding="utf-8") as csv_file:
        rows = list(csv.DictReader(csv_file))

    latest_by_backend: dict[str, dict[str, str]] = {}
    for row in rows:
        backend = row.get("backend")
        if backend:
            latest_by_backend[backend] = row

    return latest_by_backend


def append_benchmark_history(
    history_path: Path,
    rows: list[dict[str, str | float | int]],
) -> None:
    history_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "timestamp",
        "backend",
        "runtime",
        "devices",
        "model_path",
        "load_time_ms",
        "requested_batch_size",
        "actual_batch_size",
        "warmup_runs",
        "benchmark_runs",
        "input_dtype",
        "avg_latency_ms",
        "p50_latency_ms",
        "p95_latency_ms",
        "min_latency_ms",
        "max_latency_ms",
        "throughput_images_per_second",
    ]
    file_exists = history_path.exists()

    with history_path.open("a", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        if not file_exists:
            writer.writeheader()

        timestamp = datetime.utcnow().isoformat(timespec="seconds")
        for row in rows:
            history_row = {"timestamp": timestamp}
            history_row.update(row)
            writer.writerow(history_row)


def format_percent_change(current: float, previous: float, lower_is_better: bool = False) -> str:
    if previous == 0:
        return "n/a"

    raw_change = ((current - previous) / previous) * 100.0
    effective_change = -raw_change if lower_is_better else raw_change
    sign = "+" if effective_change >= 0 else "-"
    return f"{sign}{abs(effective_change):.2f}%"


def print_benchmark_details(row: dict[str, str | float | int]) -> None:
    print(f"Backend: {row['backend']}")
    print(f"- runtime: {row['runtime']}")
    print(f"- devices: {row['devices']}")
    print(f"- load time: {float(row['load_time_ms']):.3f} ms")
    print(
        f"- avg latency: {float(row['avg_latency_ms']):.3f} ms "
        "-> lower is better for average inference speed."
    )
    print(
        f"- p50 latency: {float(row['p50_latency_ms']):.3f} ms "
        "-> median latency; lower is better."
    )
    print(
        f"- p95 latency: {float(row['p95_latency_ms']):.3f} ms "
        "-> tail latency under slower runs; lower is better."
    )
    print(
        f"- throughput: {float(row['throughput_images_per_second']):.3f} images/sec "
        "-> higher is better."
    )


def print_benchmark_comparison(
    row: dict[str, str | float | int],
    previous_row: dict[str, str] | None,
) -> None:
    if previous_row is None:
        print("Verdict: no previous benchmark was found for this backend, so this is the baseline.")
        return

    comparisons = [
        ("avg_latency_ms", True),
        ("p95_latency_ms", True),
        ("throughput_images_per_second", False),
    ]
    better_count = 0
    worse_count = 0

    print("Comparison to previous benchmark:")
    for metric_name, lower_is_better in comparisons:
        current_value = float(row[metric_name])
        previous_value = float(previous_row[metric_name])

        if lower_is_better:
            if current_value < previous_value:
                better_count += 1
                trend = "improved"
            elif current_value > previous_value:
                worse_count += 1
                trend = "worsened"
            else:
                trend = "unchanged"
        else:
            if current_value > previous_value:
                better_count += 1
                trend = "improved"
            elif current_value < previous_value:
                worse_count += 1
                trend = "worsened"
            else:
                trend = "unchanged"

        print(
            f"- {metric_name}: {current_value:.3f} vs {previous_value:.3f} "
            f"({trend}, change={format_percent_change(current_value, previous_value, lower_is_better)})"
        )

    if better_count > worse_count:
        print("Verdict: this benchmark is better than the previous run for this backend.")
    elif worse_count > better_count:
        print("Verdict: this benchmark is worse than the previous run for this backend.")
    else:
        print("Verdict: this benchmark is mixed compared with the previous run for this backend.")


def print_current_backend_comparison(rows: list[dict[str, str | float | int]]) -> None:
    if len(rows) < 2:
        return

    by_backend = {str(row["backend"]): row for row in rows}
    keras_row = by_backend.get("keras")
    tflite_row = by_backend.get("tflite")
    if keras_row is None or tflite_row is None:
        return

    keras_avg = float(keras_row["avg_latency_ms"])
    tflite_avg = float(tflite_row["avg_latency_ms"])
    keras_p95 = float(keras_row["p95_latency_ms"])
    tflite_p95 = float(tflite_row["p95_latency_ms"])
    keras_throughput = float(keras_row["throughput_images_per_second"])
    tflite_throughput = float(tflite_row["throughput_images_per_second"])

    print("Current backend comparison:")
    print(f"- lower avg latency: {'keras' if keras_avg < tflite_avg else 'tflite'}")
    print(f"- lower p95 latency: {'keras' if keras_p95 < tflite_p95 else 'tflite'}")
    print(
        f"- higher throughput: "
        f"{'keras' if keras_throughput > tflite_throughput else 'tflite'}"
    )


def validate_args(args: argparse.Namespace) -> None:
    if args.image_size <= 0:
        raise ValueError("image-size must be greater than 0.")
    if args.batch_size <= 0:
        raise ValueError("batch-size must be greater than 0.")
    if args.warmup_runs < 0:
        raise ValueError("warmup-runs must be 0 or greater.")
    if args.benchmark_runs <= 0:
        raise ValueError("benchmark-runs must be greater than 0.")


def main() -> None:
    args = parse_args()
    validate_args(args)

    project_root = args.project_root.resolve()
    logs_dir = project_root / "model_training" / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    training_logs_path = logs_dir / "training_logs.txt"
    output_csv = args.output_csv
    if output_csv is None:
        output_csv = logs_dir / "inference_benchmark.csv"
    output_csv = output_csv.resolve()
    history_path = output_csv.with_name(f"{output_csv.stem}_history.csv")

    keras_model_path = args.keras_model_path
    if keras_model_path is None:
        keras_model_path = project_root / "model_training" / "saved_models" / "gore_classifier.keras"
    keras_model_path = keras_model_path.resolve()

    tflite_model_path = args.tflite_model_path
    if tflite_model_path is None:
        tflite_model_path = project_root / "model_training" / "saved_models" / "gore_classifier.tflite"
    tflite_model_path = tflite_model_path.resolve()

    rows: list[dict[str, str | float | int]] = []
    previous_rows = read_previous_benchmark_history(history_path)

    if args.backend in {"keras", "both"}:
        if keras_model_path.exists() and keras_model_path.stat().st_size > 0:
            rows.append(
                benchmark_keras(
                    model_path=keras_model_path,
                    image_size=args.image_size,
                    batch_size=args.batch_size,
                    warmup_runs=args.warmup_runs,
                    benchmark_runs=args.benchmark_runs,
                    require_gpu=args.require_gpu,
                    seed=args.seed,
                )
            )
        elif args.backend == "keras":
            raise FileNotFoundError(
                f"Keras model missing or empty at {keras_model_path}. Run train_model.py first."
            )
        else:
            print(f"Skipping Keras benchmark because the model is missing: {keras_model_path}")

    if args.backend in {"tflite", "both"}:
        if tflite_model_path.exists() and tflite_model_path.stat().st_size > 0:
            rows.append(
                benchmark_tflite(
                    model_path=tflite_model_path,
                    batch_size=args.batch_size,
                    warmup_runs=args.warmup_runs,
                    benchmark_runs=args.benchmark_runs,
                    seed=args.seed,
                )
            )
        elif args.backend == "tflite":
            raise FileNotFoundError(
                f"TFLite model missing or empty at {tflite_model_path}. Run convert_tflite.py first."
            )
        else:
            print(f"Skipping TFLite benchmark because the model is missing: {tflite_model_path}")

    if not rows:
        raise SystemExit("No benchmarks were executed. Generate the requested model artifacts first.")

    output_csv.parent.mkdir(parents=True, exist_ok=True)
    write_benchmark_csv(output_csv, rows)
    append_benchmark_history(history_path, rows)

    for row in rows:
        append_training_log(
            training_logs_path,
            "inference_benchmark",
            (
                f"backend={row['backend']},runtime={row['runtime']},"
                f"avg_latency_ms={float(row['avg_latency_ms']):.4f},"
                f"throughput_images_per_second={float(row['throughput_images_per_second']):.4f}"
            ),
        )

    print(f"Inference benchmark saved to: {output_csv}")
    print(f"Inference benchmark history saved to: {history_path}")
    for row in rows:
        print_benchmark_details(row)
        print_benchmark_comparison(row, previous_rows.get(str(row["backend"])))

    print_current_backend_comparison(rows)


if __name__ == "__main__":
    main()

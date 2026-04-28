from __future__ import annotations

import argparse
import csv
import math
from datetime import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_score, recall_score, roc_auc_score


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Evaluate the trained gore classifier on dataset/processed/test."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument("--model-path", type=Path, default=None)
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--threshold", type=float, default=0.5)
    return parser.parse_args()


def append_training_log(log_path: Path, event: str, details: str) -> None:
    timestamp = datetime.utcnow().isoformat(timespec="seconds")
    with log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(f"{timestamp},{event},{details}\n")


def load_class_names(class_names_path: Path, fallback: list[str]) -> list[str]:
    if class_names_path.exists():
        names = [line.strip() for line in class_names_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        if names:
            return names
    return fallback


def write_metrics_csv(metrics_path: Path, metrics: dict[str, float]) -> None:
    with metrics_path.open("a", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file)
        for metric_name, value in metrics.items():
            writer.writerow(["evaluation", metric_name, value])


def write_confusion_matrix(confusion_path: Path, matrix: np.ndarray) -> None:
    with confusion_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["", "pred_non_gore", "pred_gore"])
        writer.writerow(["true_non_gore", int(matrix[0, 0]), int(matrix[0, 1])])
        writer.writerow(["true_gore", int(matrix[1, 0]), int(matrix[1, 1])])


def read_previous_evaluation(
    history_path: Path,
    threshold: float,
) -> dict[str, str] | None:
    if not history_path.exists():
        return None

    with history_path.open("r", newline="", encoding="utf-8") as csv_file:
        rows = list(csv.DictReader(csv_file))

    same_threshold_rows = [
        row
        for row in rows
        if abs(float(row.get("threshold", "nan")) - threshold) < 1e-12
    ]
    if same_threshold_rows:
        return same_threshold_rows[-1]

    return rows[-1] if rows else None


def append_evaluation_history(
    history_path: Path,
    model_path: Path,
    threshold: float,
    metrics: dict[str, float],
) -> None:
    history_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "timestamp",
        "model_path",
        "threshold",
        "accuracy",
        "precision",
        "recall",
        "f1",
        "roc_auc",
    ]
    file_exists = history_path.exists()

    with history_path.open("a", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        if not file_exists:
            writer.writeheader()

        writer.writerow(
            {
                "timestamp": datetime.utcnow().isoformat(timespec="seconds"),
                "model_path": str(model_path),
                "threshold": threshold,
                "accuracy": metrics["accuracy"],
                "precision": metrics["precision"],
                "recall": metrics["recall"],
                "f1": metrics["f1"],
                "roc_auc": metrics["roc_auc"],
            }
        )


def format_delta(current: float, previous: float) -> str:
    difference = current - previous
    sign = "+" if difference >= 0 else "-"
    return f"{sign}{abs(difference):.4f}"


def print_metric_explanations(metrics: dict[str, float]) -> None:
    print("Metric guide:")
    print(f"- accuracy: {metrics['accuracy']:.4f} -> overall correctness; higher is better.")
    print(
        f"- precision: {metrics['precision']:.4f} -> when the model predicts gore, "
        "how often it is correct; higher means fewer false alarms."
    )
    print(
        f"- recall: {metrics['recall']:.4f} -> how much of the actual gore content is caught; "
        "higher means fewer missed gore samples."
    )
    print(
        f"- f1: {metrics['f1']:.4f} -> balance between precision and recall; "
        "higher is better when you want both."
    )
    roc_auc = metrics["roc_auc"]
    if math.isnan(roc_auc):
        print("- roc_auc: nan -> not available because the test labels only contained one class.")
    else:
        print(
            f"- roc_auc: {roc_auc:.4f} -> threshold-independent ranking quality; "
            "higher is better."
        )


def print_evaluation_comparison(
    metrics: dict[str, float],
    previous_row: dict[str, str] | None,
) -> None:
    if previous_row is None:
        print("Verdict: no previous evaluation run was found, so this is the current baseline.")
        return

    compared_metrics = ["accuracy", "precision", "recall", "f1", "roc_auc"]
    better_count = 0
    worse_count = 0

    print("Comparison to previous evaluation:")
    for metric_name in compared_metrics:
        current_value = metrics[metric_name]
        previous_value = float(previous_row.get(metric_name, "nan"))

        if math.isnan(current_value) or math.isnan(previous_value):
            print(f"- {metric_name}: current={current_value} previous={previous_value} -> skipped")
            continue

        if current_value > previous_value:
            better_count += 1
            trend = "improved"
        elif current_value < previous_value:
            worse_count += 1
            trend = "dropped"
        else:
            trend = "unchanged"

        print(
            f"- {metric_name}: {current_value:.4f} vs {previous_value:.4f} "
            f"({trend}, delta={format_delta(current_value, previous_value)})"
        )

    if better_count > worse_count:
        print("Verdict: this model is better than the previous evaluation overall.")
    elif worse_count > better_count:
        print("Verdict: this model is worse than the previous evaluation overall.")
    else:
        print("Verdict: this model is mixed compared with the previous evaluation.")


def main() -> None:
    args = parse_args()
    project_root = args.project_root.resolve()

    logs_dir = project_root / "model_training" / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    metrics_path = logs_dir / "metrics.csv"
    confusion_path = logs_dir / "confusion_matrix.csv"
    history_path = logs_dir / "evaluation_history.csv"
    class_names_path = logs_dir / "class_names.txt"
    training_logs_path = logs_dir / "training_logs.txt"

    model_path = args.model_path
    if model_path is None:
        model_path = project_root / "model_training" / "saved_models" / "gore_classifier.keras"
    model_path = model_path.resolve()

    if not model_path.exists() or model_path.stat().st_size == 0:
        raise FileNotFoundError(
            f"Trained model missing or empty at {model_path}. Run train_model.py first."
        )

    test_dir = project_root / "dataset" / "processed" / "test"
    if not test_dir.exists():
        raise FileNotFoundError(
            "Missing dataset/processed/test directory. Run split_dataset.py first."
        )

    test_ds = tf.keras.utils.image_dataset_from_directory(
        test_dir,
        labels="inferred",
        label_mode="binary",
        image_size=(args.image_size, args.image_size),
        batch_size=args.batch_size,
        shuffle=False,
    )

    fallback_class_names = list(test_ds.class_names)
    class_names = load_class_names(class_names_path, fallback_class_names)

    test_ds = test_ds.prefetch(tf.data.AUTOTUNE)

    if "gore" in class_names:
        gore_index = class_names.index("gore")
    else:
        gore_index = 0

    model = tf.keras.models.load_model(model_path)

    y_true_raw = np.concatenate(
        [labels.numpy().reshape(-1) for _, labels in test_ds],
        axis=0,
    ).astype(int)

    non_gore_probabilities = model.predict(test_ds, verbose=0).reshape(-1)

    if gore_index == 0:
        gore_probabilities = 1.0 - non_gore_probabilities
    else:
        gore_probabilities = non_gore_probabilities

    y_true_gore = (y_true_raw == gore_index).astype(int)
    y_pred_gore = (gore_probabilities >= args.threshold).astype(int)

    metrics: dict[str, float] = {
        "accuracy": float(accuracy_score(y_true_gore, y_pred_gore)),
        "precision": float(precision_score(y_true_gore, y_pred_gore, zero_division=0)),
        "recall": float(recall_score(y_true_gore, y_pred_gore, zero_division=0)),
        "f1": float(f1_score(y_true_gore, y_pred_gore, zero_division=0)),
    }

    if len(np.unique(y_true_gore)) > 1:
        metrics["roc_auc"] = float(roc_auc_score(y_true_gore, gore_probabilities))
    else:
        metrics["roc_auc"] = float("nan")

    confusion = confusion_matrix(y_true_gore, y_pred_gore, labels=[0, 1])

    if not metrics_path.exists():
        metrics_path.write_text("stage,metric,value\n", encoding="utf-8")

    previous_evaluation = read_previous_evaluation(history_path, args.threshold)
    write_metrics_csv(metrics_path, metrics)
    write_confusion_matrix(confusion_path, confusion)
    append_evaluation_history(history_path, model_path, args.threshold, metrics)

    append_training_log(training_logs_path, "evaluation_complete", f"model={model_path}")

    print("Evaluation complete")
    print_metric_explanations(metrics)
    print_evaluation_comparison(metrics, previous_evaluation)
    print(f"Confusion matrix saved to: {confusion_path}")
    print(f"Evaluation history saved to: {history_path}")


if __name__ == "__main__":
    main()
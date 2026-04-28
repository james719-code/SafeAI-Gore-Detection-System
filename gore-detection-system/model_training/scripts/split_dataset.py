from __future__ import annotations

import argparse
import csv
import shutil
from collections import Counter
from pathlib import Path

from sklearn.model_selection import train_test_split


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Create train/val/test splits from labels metadata and copy files into processed folders."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument("--val-ratio", type=float, default=0.15)
    parser.add_argument("--test-ratio", type=float, default=0.15)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--clean",
        action="store_true",
        help="Clear existing files in dataset/processed before copying new splits.",
    )
    return parser.parse_args()


def read_valid_records(labels_path: Path, raw_dir: Path) -> list[dict[str, Path | str]]:
    if not labels_path.exists():
        raise FileNotFoundError(
            f"Missing labels metadata file: {labels_path}. Run preprocess.py first."
        )

    records: list[dict[str, Path | str]] = []

    with labels_path.open("r", encoding="utf-8", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        for row in reader:
            is_valid = row.get("is_valid", "").strip().lower() == "true"
            if not is_valid:
                continue

            relative_path = row.get("image_path", "").strip()
            label = row.get("label", "").strip()
            source_path = raw_dir / relative_path

            if not relative_path or not label or not source_path.exists():
                continue

            records.append(
                {
                    "source_path": source_path,
                    "relative_path": relative_path,
                    "label": label,
                }
            )

    return records


def reset_processed_dir(processed_dir: Path) -> None:
    if not processed_dir.exists():
        return

    for child in processed_dir.iterdir():
        if child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink(missing_ok=True)


def ensure_split_dirs(processed_dir: Path, labels: list[str]) -> None:
    for split_name in ("train", "val", "test"):
        for label in labels:
            (processed_dir / split_name / label).mkdir(parents=True, exist_ok=True)


def stratified_split(
    records: list[dict[str, Path | str]],
    val_ratio: float,
    test_ratio: float,
    seed: int,
) -> tuple[list[dict[str, Path | str]], list[dict[str, Path | str]], list[dict[str, Path | str]]]:
    labels = [str(record["label"]) for record in records]
    holdout_ratio = val_ratio + test_ratio

    if holdout_ratio <= 0:
        return records, [], []

    train_records, holdout_records = train_test_split(
        records,
        test_size=holdout_ratio,
        random_state=seed,
        stratify=labels,
    )

    if not holdout_records:
        return train_records, [], []

    val_share_within_holdout = val_ratio / holdout_ratio
    holdout_labels = [str(record["label"]) for record in holdout_records]

    val_records, test_records = train_test_split(
        holdout_records,
        test_size=(1.0 - val_share_within_holdout),
        random_state=seed,
        stratify=holdout_labels,
    )

    return train_records, val_records, test_records


def fallback_random_split(
    records: list[dict[str, Path | str]],
    val_ratio: float,
    test_ratio: float,
    seed: int,
) -> tuple[list[dict[str, Path | str]], list[dict[str, Path | str]], list[dict[str, Path | str]]]:
    holdout_ratio = val_ratio + test_ratio
    if holdout_ratio <= 0:
        return records, [], []

    train_records, holdout_records = train_test_split(
        records,
        test_size=holdout_ratio,
        random_state=seed,
        shuffle=True,
    )

    if not holdout_records:
        return train_records, [], []

    val_share_within_holdout = val_ratio / holdout_ratio

    val_records, test_records = train_test_split(
        holdout_records,
        test_size=(1.0 - val_share_within_holdout),
        random_state=seed,
        shuffle=True,
    )

    return train_records, val_records, test_records


def copy_records_to_split(
    records: list[dict[str, Path | str]],
    processed_dir: Path,
    split_name: str,
) -> None:
    name_counts: Counter[str] = Counter()

    for record in records:
        source_path = Path(record["source_path"])
        label = str(record["label"])
        destination_dir = processed_dir / split_name / label

        file_name = source_path.name
        base_name = source_path.stem
        suffix = source_path.suffix

        name_counts[file_name] += 1
        if name_counts[file_name] == 1:
            destination_path = destination_dir / file_name
        else:
            destination_path = destination_dir / f"{base_name}_{name_counts[file_name]}{suffix}"

        shutil.copy2(source_path, destination_path)


def write_split_summary(
    info_path: Path,
    train_records: list[dict[str, Path | str]],
    val_records: list[dict[str, Path | str]],
    test_records: list[dict[str, Path | str]],
) -> None:
    split_map = {
        "train": train_records,
        "val": val_records,
        "test": test_records,
    }

    with info_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["scope", "category", "label", "count", "notes"])

        for split_name, rows in split_map.items():
            counts = Counter(str(row["label"]) for row in rows)
            for label in sorted(counts):
                writer.writerow(
                    [
                        "processed",
                        split_name,
                        label,
                        counts[label],
                        "copied from dataset/raw",
                    ]
                )

        writer.writerow(
            [
                "processed",
                "overall",
                "all",
                len(train_records) + len(val_records) + len(test_records),
                "all copied images",
            ]
        )


def main() -> None:
    args = parse_args()

    if args.val_ratio < 0 or args.test_ratio < 0:
        raise ValueError("val-ratio and test-ratio must be non-negative.")
    if args.val_ratio + args.test_ratio >= 1:
        raise ValueError("val-ratio + test-ratio must be less than 1.")

    project_root = args.project_root.resolve()
    raw_dir = project_root / "dataset" / "raw"
    metadata_dir = project_root / "dataset" / "metadata"
    labels_path = metadata_dir / "labels.csv"
    info_path = metadata_dir / "dataset_info.csv"
    processed_dir = project_root / "dataset" / "processed"

    records = read_valid_records(labels_path, raw_dir)
    if not records:
        raise SystemExit("No valid records found. Run preprocess.py and add raw images first.")

    labels = sorted({str(record["label"]) for record in records})

    if args.clean:
        reset_processed_dir(processed_dir)

    ensure_split_dirs(processed_dir, labels)

    try:
        train_records, val_records, test_records = stratified_split(
            records,
            val_ratio=args.val_ratio,
            test_ratio=args.test_ratio,
            seed=args.seed,
        )
    except ValueError as exc:
        print(f"Warning: stratified split failed ({exc}). Using random split fallback.")
        train_records, val_records, test_records = fallback_random_split(
            records,
            val_ratio=args.val_ratio,
            test_ratio=args.test_ratio,
            seed=args.seed,
        )

    copy_records_to_split(train_records, processed_dir, "train")
    copy_records_to_split(val_records, processed_dir, "val")
    copy_records_to_split(test_records, processed_dir, "test")

    write_split_summary(info_path, train_records, val_records, test_records)

    print(f"Train images: {len(train_records)}")
    print(f"Val images: {len(val_records)}")
    print(f"Test images: {len(test_records)}")
    print(f"Updated: {info_path}")


if __name__ == "__main__":
    main()

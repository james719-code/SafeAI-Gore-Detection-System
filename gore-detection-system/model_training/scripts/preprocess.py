from __future__ import annotations

import argparse
import csv
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional

from PIL import Image, UnidentifiedImageError

SUPPORTED_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


@dataclass
class ImageRecord:
    image_path: str
    label: str
    is_valid: bool
    width: Optional[int]
    height: Optional[int]
    image_format: Optional[str]
    reason: str


def iter_candidate_images(raw_dir: Path, label: str) -> Iterable[Path]:
    label_dir = raw_dir / label
    if not label_dir.exists():
        return []
    return sorted(
        path
        for path in label_dir.rglob("*")
        if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
    )


def inspect_image(path: Path) -> tuple[bool, Optional[int], Optional[int], Optional[str], str]:
    try:
        with Image.open(path) as image:
            image.verify()

        with Image.open(path) as image:
            width, height = image.size
            image_format = image.format or "unknown"

        return True, width, height, image_format, ""
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        return False, None, None, None, str(exc)


def collect_records(raw_dir: Path, labels: list[str]) -> list[ImageRecord]:
    records: list[ImageRecord] = []

    for label in labels:
        for image_path in iter_candidate_images(raw_dir, label):
            is_valid, width, height, image_format, reason = inspect_image(image_path)
            relative = str(image_path.relative_to(raw_dir)).replace("\\", "/")
            records.append(
                ImageRecord(
                    image_path=relative,
                    label=label,
                    is_valid=is_valid,
                    width=width,
                    height=height,
                    image_format=image_format,
                    reason=reason,
                )
            )

    return records


def write_labels_csv(output_path: Path, records: list[ImageRecord]) -> None:
    with output_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(
            ["image_path", "label", "is_valid", "width", "height", "image_format", "reason"]
        )
        for record in records:
            writer.writerow(
                [
                    record.image_path,
                    record.label,
                    str(record.is_valid).lower(),
                    record.width if record.width is not None else "",
                    record.height if record.height is not None else "",
                    record.image_format or "",
                    record.reason,
                ]
            )


def write_dataset_info(output_path: Path, records: list[ImageRecord]) -> None:
    class_totals = Counter(record.label for record in records)
    valid_totals = Counter(record.label for record in records if record.is_valid)
    invalid_totals = Counter(record.label for record in records if not record.is_valid)

    with output_path.open("w", newline="", encoding="utf-8") as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(["scope", "category", "label", "count", "notes"])

        for label in sorted(class_totals):
            writer.writerow(["raw", "total", label, class_totals[label], "discovered image files"])

        for label in sorted(valid_totals):
            writer.writerow(["raw", "valid", label, valid_totals[label], "usable image files"])

        for label in sorted(invalid_totals):
            writer.writerow(["raw", "invalid", label, invalid_totals[label], "corrupt or unreadable files"])

        writer.writerow(
            [
                "raw",
                "overall",
                "all",
                len(records),
                "all discovered images across labels",
            ]
        )


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parents[1]

    parser = argparse.ArgumentParser(
        description="Validate raw dataset images and generate metadata CSV files."
    )
    parser.add_argument("--project-root", type=Path, default=project_root)
    parser.add_argument("--labels", nargs="+", default=["gore", "non_gore"])
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit with non-zero status if any invalid image is found.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    project_root = args.project_root.resolve()

    raw_dir = project_root / "dataset" / "raw"
    metadata_dir = project_root / "dataset" / "metadata"
    metadata_dir.mkdir(parents=True, exist_ok=True)

    labels_path = metadata_dir / "labels.csv"
    info_path = metadata_dir / "dataset_info.csv"

    records = collect_records(raw_dir, args.labels)
    write_labels_csv(labels_path, records)
    write_dataset_info(info_path, records)

    invalid_count = sum(1 for record in records if not record.is_valid)
    valid_count = len(records) - invalid_count

    print(f"Scanned images: {len(records)}")
    print(f"Valid images: {valid_count}")
    print(f"Invalid images: {invalid_count}")
    print(f"Generated: {labels_path}")
    print(f"Generated: {info_path}")

    if args.strict and invalid_count > 0:
        raise SystemExit("Invalid images found. Run without --strict to inspect metadata output.")


if __name__ == "__main__":
    main()

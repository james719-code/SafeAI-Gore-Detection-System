from pathlib import Path
from PIL import Image
import os

DATASET_DIR = Path("dataset/processed")
SUPPORTED_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".gif"}

converted = 0
skipped = 0
failed = 0

def normalize_image(path: Path):
    global converted, skipped, failed

    ext = path.suffix.lower()
    if ext not in SUPPORTED_EXTS:
        skipped += 1
        return

    try:
        with Image.open(path) as img:
            # Convert everything to RGB
            img = img.convert("RGB")

            # Save as clean JPG beside the old file
            new_path = path.with_suffix(".jpg")

            # Avoid deleting if it is already a .jpg and usable
            img.save(new_path, format="JPEG", quality=95)

        # Delete old file if the extension changed
        if new_path != path:
            path.unlink()

        converted += 1
        print(f"Converted: {path} -> {new_path}")

    except Exception as e:
        failed += 1
        print(f"FAILED: {path} -> {e}")

def main():
    if not DATASET_DIR.exists():
        print(f"Dataset folder not found: {DATASET_DIR}")
        return

    files = [p for p in DATASET_DIR.rglob("*") if p.is_file()]
    print(f"Found {len(files)} files.")

    for path in files:
        normalize_image(path)

    print("\n===== DONE =====")
    print(f"Converted: {converted}")
    print(f"Skipped: {skipped}")
    print(f"Failed: {failed}")

if __name__ == "__main__":
    main()
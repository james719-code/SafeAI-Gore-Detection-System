import os
import shutil
from pathlib import Path
from PIL import Image
import tensorflow as tf

# Change this if you want to scan raw instead of processed
DATASET_DIR = Path("dataset/processed")

# Where bad files will be moved
QUARANTINE_DIR = Path("dataset/bad_files")

# Allowed extensions for this project
ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".gif"}


def check_with_pillow(file_path: Path):
    try:
        with Image.open(file_path) as img:
            img.verify()
        return True, None
    except Exception as e:
        return False, f"Pillow verify failed: {e}"


def check_with_tensorflow(file_path: Path):
    try:
        img_bytes = tf.io.read_file(str(file_path))
        tf.io.decode_image(img_bytes, channels=3, expand_animations=False)
        return True, None
    except Exception as e:
        return False, f"TensorFlow decode failed: {e}"


def move_to_quarantine(file_path: Path):
    relative_path = file_path.relative_to(DATASET_DIR)
    target_path = QUARANTINE_DIR / relative_path
    target_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(file_path), str(target_path))
    return target_path


def main():
    if not DATASET_DIR.exists():
        print(f"Dataset folder not found: {DATASET_DIR}")
        return

    bad_files = []
    suspicious_files = []
    total_files = 0

    for root, _, files in os.walk(DATASET_DIR):
        for file_name in files:
            file_path = Path(root) / file_name
            total_files += 1

            ext = file_path.suffix.lower()
            if ext not in ALLOWED_EXTENSIONS:
                suspicious_files.append((file_path, f"Unsupported extension: {ext}"))
                continue

            ok_pillow, err_pillow = check_with_pillow(file_path)
            ok_tf, err_tf = check_with_tensorflow(file_path)

            if not ok_pillow or not ok_tf:
                errors = []
                if err_pillow:
                    errors.append(err_pillow)
                if err_tf:
                    errors.append(err_tf)
                bad_files.append((file_path, " | ".join(errors)))

    print(f"\nScanned files: {total_files}")
    print(f"Suspicious files: {len(suspicious_files)}")
    print(f"Bad files: {len(bad_files)}\n")

    if suspicious_files:
        print("=== SUSPICIOUS FILES ===")
        for path, reason in suspicious_files:
            print(f"{path} -> {reason}")

    if bad_files:
        print("\n=== BAD FILES ===")
        for path, reason in bad_files:
            print(f"{path} -> {reason}")

        print("\nMoving bad files to quarantine...")
        for path, _ in bad_files:
            new_path = move_to_quarantine(path)
            print(f"Moved: {path} -> {new_path}")
    else:
        print("No bad files found.")

    print("\nDone.")


if __name__ == "__main__":
    main()
from pathlib import Path
import tensorflow as tf

DATASET_ROOT = Path("dataset/processed")
ALLOWED_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".gif"}

def check_file(path: Path):
    try:
        raw = tf.io.read_file(str(path))
        img = tf.io.decode_image(raw, channels=3, expand_animations=False)
        _ = tf.image.resize(img, [224, 224])
        return True, None
    except Exception as e:
        return False, str(e)

def main():
    if not DATASET_ROOT.exists():
        print(f"Dataset folder not found: {DATASET_ROOT}")
        return

    all_files = []
    for split in ["train", "val", "test"]:
        split_dir = DATASET_ROOT / split
        if not split_dir.exists():
            print(f"Missing split folder: {split_dir}")
            continue

        for p in split_dir.rglob("*"):
            if p.is_file():
                all_files.append(p)

    print(f"Total files found: {len(all_files)}")

    bad = []
    suspicious = []

    for i, path in enumerate(all_files, start=1):
        ext = path.suffix.lower()

        if ext not in ALLOWED_EXTS:
            suspicious.append((path, f"Unsupported extension: {ext}"))
            print(f"[{i}/{len(all_files)}] SUSPICIOUS -> {path}")
            continue

        ok, err = check_file(path)
        if not ok:
            bad.append((path, err))
            print(f"[{i}/{len(all_files)}] BAD -> {path}")
            print(f"    Reason: {err}")
        else:
            if i % 500 == 0:
                print(f"[{i}/{len(all_files)}] checked...")

    print("\n===== RESULT =====")
    print(f"Suspicious files: {len(suspicious)}")
    print(f"Bad files: {len(bad)}")

    if suspicious:
        print("\n--- Suspicious files ---")
        for path, reason in suspicious:
            print(f"{path} -> {reason}")

    if bad:
        print("\n--- Bad files ---")
        for path, reason in bad:
            print(f"{path} -> {reason}")
    else:
        print("\nNo TensorFlow-decode bad files found.")

if __name__ == "__main__":
    main()
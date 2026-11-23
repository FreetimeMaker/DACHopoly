"""Rectify the supplied board photograph for offline Android rendering.
Run from the repository root: py media-resources/prepare_board.py
Requires Pillow only for asset generation, not at runtime.
"""

from pathlib import Path

from PIL import Image, ImageOps

root = Path(__file__).resolve().parent.parent
source = root / "media-resources/pics/IMG_20250124_114319.jpg"
image = Image.open(source).convert("RGB")
# Measured outer corners, in Pillow's top-left, bottom-left, bottom-right,
# top-right order. Keep the handmade artwork instead of inventing missing art.
board = image.transform(
    (1200, 1200),
    Image.Transform.QUAD,
    tuple(
        value * image.width / 1568
        for value in (300, 26, 120, 1044, 1295, 1125, 1275, 44)
    ),
    resample=Image.Resampling.BICUBIC,
).transpose(Image.Transpose.ROTATE_270)
board = ImageOps.autocontrast(board, cutoff=1)
output = root / "app/src/main/res/drawable/board_reference.webp"
board.save(output, "WEBP", quality=88, method=6)
print(f"Created {output.relative_to(root)} ({output.stat().st_size} bytes)")

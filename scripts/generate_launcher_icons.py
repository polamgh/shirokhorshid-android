#!/usr/bin/env python3
"""Generate Android launcher, dashboard, and notification icons from a source image."""
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    import subprocess
    subprocess.run(["pip3", "install", "pillow", "-q"], check=True)
    from PIL import Image

SRC = Path("assets/azaditunnel-logo.png")
RES = Path("app/src/main/res")

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}


def remove_near_black_background(img: Image.Image, threshold: int = 40) -> Image.Image:
    rgba = img.convert("RGBA")
    px = rgba.load()
    w, h = rgba.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if r <= threshold and g <= threshold and b <= threshold:
                px[x, y] = (0, 0, 0, 0)
    return rgba


def trim_transparent(img: Image.Image) -> Image.Image:
    bbox = img.getbbox()
    return img.crop(bbox) if bbox else img


def fit_square(img: Image.Image, size: int, pad_ratio: float = 0.08) -> Image.Image:
    pad = int(size * pad_ratio)
    inner = size - 2 * pad
    logo = img.resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(logo, (pad, pad), logo)
    return canvas


def prepare_logo(img: Image.Image) -> Image.Image:
    return trim_transparent(remove_near_black_background(img))


def white_silhouette(img: Image.Image, size: int, alpha_scale: float = 1.0) -> Image.Image:
    logo = prepare_logo(img)
    square = fit_square(logo, size, pad_ratio=0.06)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = square.load()
    opx = out.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            if a > 24:
                luminance = int(0.299 * r + 0.587 * g + 0.114 * b)
                alpha = min(255, int(a * alpha_scale * (0.35 + 0.65 * luminance / 255)))
                opx[x, y] = (255, 255, 255, alpha)
    return out


def main() -> None:
    img = Image.open(SRC).convert("RGBA")
    logo = prepare_logo(img)

    legacy = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in legacy.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        fit_square(logo, size).save(out_dir / "ic_launcher.png", "PNG")
        fit_square(logo, size).save(out_dir / "ic_launcher_round.png", "PNG")

    fg_sizes = {
        "drawable-mdpi": 108,
        "drawable-hdpi": 162,
        "drawable-xhdpi": 216,
        "drawable-xxhdpi": 324,
        "drawable-xxxhdpi": 432,
        "drawable-v24": 432,
    }
    for folder, size in fg_sizes.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        fit_square(logo, size).save(d / "ic_launcher_foreground.png", "PNG")

    dashboard_sizes = {f"drawable-{d}": int(72 * s) for d, s in DENSITIES.items()}
    for folder, size in dashboard_sizes.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        fit_square(logo, size).save(d / "ic_app_logo.png", "PNG")

    notif_base = 24
    pulse = [0.45, 0.55, 0.65, 0.75, 0.65, 0.55]
    notif_names = {
        "notification_icon_connected": 1.0,
        "notification_icon_waiting": 0.55,
        "ic_psiphon_alert_notification": 1.0,
        "notification_icon_upgrade_available": 1.0,
    }
    for density, scale in DENSITIES.items():
        folder = RES / f"drawable-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        size = int(notif_base * scale)
        for name, alpha in notif_names.items():
            white_silhouette(img, size, alpha).save(folder / f"{name}.png", "PNG")
        for idx, alpha in enumerate(pulse, start=1):
            white_silhouette(img, size, alpha).save(
                folder / f"notification_icon_connecting_{idx:02d}.png", "PNG"
            )

    print(f"Generated launcher, dashboard, and notification icons from {SRC} ({img.size[0]}x{img.size[1]})")


if __name__ == "__main__":
    main()

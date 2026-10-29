from PIL import Image

# Change this to your new logo image path
input_image = r"E:\Working Projects\Java Projects\BCIPL-Launcher\src\resources\bci-icon_1.png"

# Output icon path
output_icon = r"src\resources\bci-launcher.ico"

img = Image.open(input_image).convert("RGBA")

# Resize while preserving aspect ratio and transparency
img.thumbnail((256, 256), Image.Resampling.LANCZOS)

# Center the logo on a transparent square canvas
canvas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
x = (256 - img.width) // 2
y = (256 - img.height) // 2
canvas.alpha_composite(img, (x, y))

# Save a multi-resolution Windows icon
canvas.save(
    output_icon,
    format="ICO",
    sizes=[
        (256, 256),
        (128, 128),
        (64, 64),
        (48, 48),
        (32, 32),
        (16, 16),
    ],
)

print("Icon created:", output_icon)
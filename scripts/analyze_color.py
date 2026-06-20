from PIL import Image
img = Image.open('/tmp/immersive_aircraft_src/common/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')
# Get colors at specific positions
positions = [
    (0, 0), (32, 0), (64, 0), (96, 0), (0, 32), (32, 32), (64, 32), (96, 32),
    (0, 64), (32, 64), (64, 64), (96, 64),
    (0, 96), (32, 96), (64, 96), (96, 96),
    (0, 124), (32, 124), (64, 124), (96, 124),
]
for x, y in positions:
    c = img.getpixel((x, y))
    print(f'  ({x:3d},{y:3d}): RGBA{tuple(c)}')

# Also check the propeller area
print("\nPropeller area (x=0-32, y=24-40):")
for y in range(24, 40, 2):
    line = f'y={y:3d}: '
    for x in range(0, 48, 2):
        c = img.getpixel((x, y))
        if c[3] == 0:
            line += '. '
        elif c[0] < 100 and c[1] < 100 and c[2] < 100:
            line += 'K '  # dark/black
        elif abs(c[0] - c[1]) < 30 and abs(c[1] - c[2]) < 30:
            line += 'G '  # gray
        else:
            line += 'w '  # wood
    print(line)

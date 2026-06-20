from PIL import Image
img = Image.open('/tmp/immersive_aircraft_src/common/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')
print(f'Image size: {img.size}')
# Resample to 16x16 grid for overview
small = img.resize((16, 16), Image.NEAREST)
for y in range(16):
    line = ''
    for x in range(16):
        c = small.getpixel((x, y))
        if c[3] == 0:
            line += '. '
        else:
            r, g, b = c[0], c[1], c[2]
            # Categorize color
            if r > 150 and g < 100 and b < 100:
                line += 'R '  # red
            elif r > 200 and g > 200 and b > 200:
                line += 'W '  # white
            elif r < 80 and g < 80 and b < 80:
                line += 'B '  # black
            elif abs(r - g) < 20 and abs(g - b) < 20:
                if r > 150:
                    line += 'L '  # light gray
                else:
                    line += 'G '  # dark gray
            else:
                line += 'w '  # wood
    print(line)

from PIL import Image
img = Image.open('/tmp/immersive_aircraft_src/common/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')
# Map the texture more carefully - show where opaque pixels are
print("Texture layout (R=red, W=wood, K=black, G=gray, .=transparent):")
print("y\\x  ", end='')
for x in range(0, 128, 4):
    print(f'{x:3d}', end='')
print()
for y in range(0, 128, 4):
    line = f'{y:3d}: '
    for x in range(0, 128, 4):
        c = img.getpixel((x, y))
        if c[3] == 0:
            line += '...'
        elif c[0] > 150 and c[1] < 100 and c[2] < 100:
            line += 'RRR'
        elif c[0] > 200 and c[1] > 200 and c[2] > 200:
            line += 'WWW'
        elif c[0] < 80 and c[1] < 80 and c[2] < 80:
            line += 'KKK'
        elif abs(c[0] - c[1]) < 30 and abs(c[1] - c[2]) < 30:
            line += 'GGG'
        else:
            line += 'www'
    print(line)

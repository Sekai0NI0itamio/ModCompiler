from PIL import Image
img = Image.open('/tmp/immersive_aircraft_src/common/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')
# Look at the alpha channel as a 32x32 grid
alpha = img.split()[3]
# Resize to 32x32
small = alpha.resize((32, 32), Image.NEAREST)
for y in range(32):
    line = ''
    for x in range(32):
        v = small.getpixel((x, y))
        if v == 0:
            line += '. '
        elif v < 50:
            line += ': '
        elif v < 128:
            line += 'o '
        elif v < 200:
            line += 'O '
        else:
            line += '# '
    print(line)

from PIL import Image
img = Image.open('/tmp/immersive_aircraft_src/common/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')
# Look at the alpha channel as a 64x64 grid
alpha = img.split()[3]
small = alpha.resize((64, 64), Image.NEAREST)
for y in range(64):
    line = ''
    for x in range(64):
        v = small.getpixel((x, y))
        if v == 0:
            line += '.'
        elif v < 128:
            line += 'o'
        else:
            line += '#'
    print(line)

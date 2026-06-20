from PIL import Image, ImageDraw
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)
wood = (139, 90, 43, 255)
wood_dark = (101, 67, 33, 255)
wood_light = (205, 133, 63, 255)
metal = (80, 80, 80, 255)
white = (240, 240, 240, 255)
red = (180, 30, 30, 255)
black = (20, 20, 20, 255)

# Layout: 64x64 texture, model uses up to 64x64x64 units (=4 blocks)
# Texture regions (UVs in pixels):
#   0-64 x 0-8: TOP WING
#   16-48 x 8-24: BODY (main fuselage)
#   0-64 x 24-32: BOTTOM WING
#   48-64 x 8-24: TAIL FIN
#   0-16 x 8-24: PROPELLER
#   0-64 x 32-64: SPARE (tail wing, details)

# TOP WING strip (y=0-8, x=0-64)
draw.rectangle([0, 0, 63, 7], fill=wood)
draw.rectangle([0, 0, 5, 7], fill=wood_dark)
draw.rectangle([58, 0, 63, 7], fill=wood_dark)
# Wing ribs
for x in [12, 24, 36, 48]:
    draw.line([(x, 0), (x, 7)], fill=wood_dark, width=1)
# Roundels
draw.ellipse([8, 1, 14, 6], fill=red)
draw.ellipse([26, 1, 32, 6], fill=red)
draw.ellipse([50, 1, 56, 6], fill=red)

# BOTTOM WING strip (y=24-32, x=0-64)
draw.rectangle([0, 24, 63, 31], fill=wood)
draw.rectangle([0, 24, 5, 31], fill=wood_dark)
draw.rectangle([58, 24, 63, 31], fill=wood_dark)
for x in [12, 24, 36, 48]:
    draw.line([(x, 24), (x, 31)], fill=wood_dark, width=1)
draw.ellipse([8, 25, 14, 30], fill=red)
draw.ellipse([26, 25, 32, 30], fill=red)
draw.ellipse([50, 25, 56, 30], fill=red)

# BODY (y=8-24, x=16-48) - main fuselage side view
draw.rectangle([16, 8, 47, 23], fill=wood)
# Cockpit (lighter section on top)
draw.rectangle([18, 6, 46, 11], fill=wood_light)
# Windows
draw.rectangle([20, 7, 23, 10], fill=metal)
draw.rectangle([24, 7, 27, 10], fill=metal)
draw.rectangle([40, 7, 43, 10], fill=metal)
# Body wood panel lines
draw.line([(16, 14), (47, 14)], fill=wood_dark, width=1)
draw.line([(16, 20), (47, 20)], fill=wood_dark, width=1)
# Body roundel (center)
draw.ellipse([26, 12, 36, 22], fill=white)
draw.ellipse([28, 14, 34, 20], fill=red)
# Body dark stripe
draw.rectangle([16, 16, 47, 17], fill=wood_dark)
# Vertical panel lines
for x in [22, 32, 42]:
    draw.line([(x, 8), (x, 23)], fill=wood_dark, width=1)

# PROPELLER (y=8-24, x=0-16) - spinning blade
# Spinner (front of plane)
draw.ellipse([2, 12, 10, 20], fill=metal)
# Propeller blades (cross)
draw.rectangle([0, 14, 15, 16], fill=metal)
draw.rectangle([0, 18, 15, 20], fill=metal)
# Hub center
draw.ellipse([4, 14, 10, 20], fill=black)
draw.ellipse([5, 15, 9, 19], fill=metal)

# TAIL FIN (y=8-24, x=48-64) - vertical stabilizer
draw.polygon([(48, 23), (48, 8), (58, 8), (62, 23)], fill=wood)
draw.polygon([(48, 8), (58, 8), (58, 12), (52, 12)], fill=wood_light)
draw.line([(54, 8), (54, 23)], fill=wood_dark, width=1)
# Tail fin roundel
draw.ellipse([52, 14, 58, 20], fill=red)

# TAIL WING (y=32-40, x=40-64) - horizontal stabilizer
draw.rectangle([40, 32, 63, 39], fill=wood)
draw.rectangle([40, 32, 43, 39], fill=wood_dark)
draw.rectangle([60, 32, 63, 39], fill=wood_dark)
for x in [48, 54]:
    draw.line([(x, 32), (x, 39)], fill=wood_dark, width=1)
draw.ellipse([45, 33, 51, 38], fill=red)

# WHEELS (y=32-40, x=16-32)
draw.ellipse([18, 32, 24, 40], fill=metal)
draw.ellipse([19, 33, 23, 39], fill=black)
draw.ellipse([28, 32, 34, 40], fill=metal)
draw.ellipse([29, 33, 33, 39], fill=black)
# Wheel struts
draw.rectangle([20, 24, 22, 32], fill=metal)
draw.rectangle([30, 24, 32, 32], fill=metal)

# Cross struts connecting top and bottom wings
draw.rectangle([14, 7, 17, 24], fill=wood_dark)
draw.rectangle([46, 7, 49, 24], fill=wood_dark)

img.save('/Users/stevennovak/Desktop/Code Projects/GithubBasedEngineering/ModCompiler/Mod Development/1.12.2-forge/mods/immersive-aircraft/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')
print('Created biplane texture (64x64)')

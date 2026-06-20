from PIL import Image, ImageDraw

# Create 64x64 texture for biplane
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Body (brown wood color)
draw.rectangle([16, 44, 32, 60], fill=(139, 90, 43, 255))
draw.rectangle([17, 45, 31, 59], fill=(160, 100, 50, 255))

# Top wing (light wood)
draw.rectangle([0, 0, 48, 16], fill=(180, 120, 60, 255))
draw.rectangle([2, 2, 46, 14], fill=(200, 140, 70, 255))

# Bottom wing (light wood)
draw.rectangle([0, 72, 48, 88], fill=(180, 120, 60, 255))
draw.rectangle([2, 74, 46, 86], fill=(200, 140, 70, 255))

# Tail fin (brown)
draw.rectangle([84, 0, 92, 14], fill=(139, 90, 43, 255))
draw.rectangle([85, 1, 91, 13], fill=(160, 100, 50, 255))

# Tail wing (brown)
draw.rectangle([40, 32, 64, 35], fill=(139, 90, 43, 255))

# Propeller (gray)
draw.rectangle([0, 8, 24, 10], fill=(80, 80, 80, 255))

# Wheels (dark gray)
draw.rectangle([0, 32, 4, 36], fill=(60, 60, 60, 255))

# Save entity texture
img.save('/Users/stevennovak/Desktop/Code Projects/GithubBasedEngineering/ModCompiler/Mod Development/1.12.2-forge/mods/immersive-aircraft/src/main/resources/assets/immersive_aircraft/textures/entity/biplane.png')

# Create item texture (16x16)
item_img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
item_draw = ImageDraw.Draw(item_img)

# Simple biplane icon
# Body
item_draw.rectangle([6, 7, 10, 9], fill=(139, 90, 43, 255))
# Top wing
item_draw.rectangle([2, 5, 14, 7], fill=(180, 120, 60, 255))
# Bottom wing
item_draw.rectangle([2, 9, 14, 11], fill=(180, 120, 60, 255))
# Propeller
item_draw.rectangle([5, 6, 7, 10], fill=(80, 80, 80, 255))

item_img.save('/Users/stevennovak/Desktop/Code Projects/GithubBasedEngineering/ModCompiler/Mod Development/1.12.2-forge/mods/immersive-aircraft/src/main/resources/assets/immersive_aircraft/textures/items/biplane.png')

print("Textures created successfully!")

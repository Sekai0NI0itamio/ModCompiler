import json
with open('/tmp/immersive_aircraft_src/common/src/main/resources/assets/immersive_aircraft/objects/biplane.bbmodel') as f:
    data = json.load(f)
print("All elements:")
for el in data['elements']:
    name = el.get('name', '')
    origin = el.get('origin')
    print(f'  {name}: origin={origin}')

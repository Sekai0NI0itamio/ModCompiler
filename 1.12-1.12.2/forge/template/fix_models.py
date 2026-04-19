import json
import glob
import os

model_dir = "src/main/resources/assets/eureka_valkerian_ships/models/block"
files = glob.glob(os.path.join(model_dir, "*.json"))

for f in files:
    with open(f, 'r') as file:
        data = json.load(file)
    
    name = os.path.basename(f).replace('.json', '')
    
    if name == 'ship_helm':
        tex = 'minecraft:blocks/log_oak' # Give it a different vanilla texture for now if we don't have helm texture block
    elif name == 'balloon':
        tex = 'eureka_valkerian_ships:blocks/balloon'
    else:
        tex = f'eureka_valkerian_ships:blocks/{name}'
        
    if 'textures' in data and 'all' in data['textures']:
        data['textures']['all'] = tex
        
    with open(f, 'w') as file:
        json.dump(data, file, indent=4)

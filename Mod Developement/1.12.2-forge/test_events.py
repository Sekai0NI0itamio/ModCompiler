import os
for root, dirs, files in os.walk(os.path.expanduser('~/.gradle/caches/minecraft/')):
    for f in files:
        if "BlockEvent" in f:
            print(os.path.join(root, f))

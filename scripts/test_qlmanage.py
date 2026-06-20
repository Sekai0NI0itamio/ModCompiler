#!/usr/bin/env python3
import tempfile, subprocess
from pathlib import Path
from PIL import Image
import numpy as np

stripes_html = ""
colors = [
    (255,0,0), (0,255,0), (0,0,255), (255,255,0), (255,0,255), (0,255,255),
    (255,128,0), (128,0,255), (0,255,128), (255,128,128), (128,255,0), (0,128,255),
    (200,200,0), (200,0,200), (0,200,200), (128,128,255), (255,128,0), (0,200,100),
    (100,0,200), (200,100,0),
]
for i, (r, g, b) in enumerate(colors):
    x = i * 100
    stripes_html += '<div style="position:absolute;left:%dpx;top:0;width:100px;height:100%%;background:rgb(%d,%d,%d)"></div>\n' % (x, r, g, b)

html = """<!doctype html>
<html><head><style>
html, body { margin: 0; width: 2000px; height: 1200px; overflow: hidden; background: #111; }
</style></head><body>
%s
</body></html>""" % stripes_html

with tempfile.TemporaryDirectory(prefix='qltest2-') as td:
    html_path = Path(td) / 'test.html'
    html_path.write_text(html)
    out_dir = Path(td) / 'out'
    out_dir.mkdir()
    cmd = ['/usr/bin/qlmanage', '-t', '-s', '3840', '-o', str(out_dir), str(html_path)]
    subprocess.run(cmd, capture_output=True, timeout=30)
    out_file = out_dir / 'test.html.png'
    if out_file.exists():
        img = Image.open(out_file)
        print('Output size:', img.size)
        arr = np.array(img.convert('RGB'))
        h, w = arr.shape[:2]
        top_row = arr[10, :, :]
        non_black = np.where(np.any(top_row > 30, axis=1))[0]
        if len(non_black) > 0:
            last_colored_x = non_black[-1]
            print('Last colored pixel at x=%d (out of %d)' % (last_colored_x, w))
            print('Estimated logical viewport width: ~%dpx' % (last_colored_x // 2))
        left_col = arr[:, 10, :]
        non_black_y = np.where(np.any(left_col > 30, axis=1))[0]
        if len(non_black_y) > 0:
            last_colored_y = non_black_y[-1]
            print('Last colored pixel at y=%d (out of %d)' % (last_colored_y, h))
            print('Estimated logical viewport height: ~%dpx' % (last_colored_y // 2))

        for x_logical in [0, 200, 400, 600, 800, 900, 950, 960, 1000, 1200, 1400, 1600, 1800]:
            x_out = x_logical * 2
            if x_out < w:
                r, g, b = int(arr[10, x_out, 0]), int(arr[10, x_out, 1]), int(arr[10, x_out, 2])
                print('  x=%dpx (out=%d): RGB=(%d,%d,%d)' % (x_logical, x_out, r, g, b))
    else:
        print('No output file')

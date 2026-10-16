import sys, os, glob
from lottie.importers.core import import_lottie
from lottie.exporters.cairo import export_png
from PIL import Image
src, out = sys.argv[1], sys.argv[2]
rows = []
for f in sorted(glob.glob(src + "/*.json")):
    an = import_lottie(f)
    n = int(an.out_point)
    cells = []
    for k in range(6):
        fr = int(k * (n - 1) / 5)
        p = f"{out}/_tmp.png"
        export_png(an, p, fr)
        im = Image.open(p).convert("RGBA")
        bg = Image.new("RGBA", im.size, (40, 40, 48, 255))
        bg.alpha_composite(im)
        cells.append(bg.resize((160, 160)))
    row = Image.new("RGB", (160 * 6, 160), (40, 40, 48))
    for i, c in enumerate(cells):
        row.paste(c.convert("RGB"), (i * 160, 0))
    rows.append(row)
    print(os.path.basename(f), n)
sheet = Image.new("RGB", (160 * 6, 160 * len(rows)))
for i, r in enumerate(rows):
    sheet.paste(r, (0, i * 160))
sheet.save(out + "/lottie_sheet.png")

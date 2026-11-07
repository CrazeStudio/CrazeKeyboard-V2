#!/usr/bin/env python3
"""Generate tokens.css and Android resources from tokens.json + tokens.dark.json.
Run after any palette change (including the rebrand), then re-run contrast.py on both files."""
import json
light = json.load(open("tokens.json")); dark = json.load(open("tokens.dark.json"))

def css_vars(colors):
    return "\n".join(f"  --color-{k}: {v};" for k, v in colors.items())

sz, rad, sp = light["size"], light["radius"], light["space"]
static = [f"  --space-{i}: {v}px;" for i, v in enumerate(sp)]
static += [f"  --size-{k}: {v}px;" for k, v in sz.items() if "scale" not in k]
static += [f"  --radius-{k}: {v}px;" for k, v in rad.items()]
static += [f"  --shadow-{k}: {v};" for k, v in light["shadow"].items()]
static += [f"  --motion-{k}: {v};" for k, v in light["motion"].items() if k != "reduced-motion"]
static += [f"  --font-sans: {light['font']['sans']};"]
for k, t in light["type"].items():
    static += [f"  --type-{k}-size: {t['size']}px;", f"  --type-{k}-line: {t['line']}px;", f"  --type-{k}-weight: {t['weight']};"]

css = f""":root {{
{css_vars(light['color'])}
{chr(10).join(static)}
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
{css_vars(dark['color']).replace('  --', '    --')}
  }}
}}
:root[data-theme="dark"] {{
{css_vars(dark['color'])}
}}
@media (prefers-reduced-motion: reduce) {{ * {{ transition-duration: 0ms !important; animation-duration: 0ms !important; }} }}
"""
open("tokens.css", "w").write(css)

def xml_colors(colors):
    rows = "\n".join(f'    <color name="craze_{k.replace("-", "_")}">{v}</color>' for k, v in colors.items())
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{rows}\n</resources>\n'
open("android/values/colors.xml", "w").write(xml_colors(light["color"]))
open("android/values-night/colors.xml", "w").write(xml_colors(dark["color"]))

d = []
for i, v in enumerate(sp): d.append(f'    <dimen name="craze_space_{i}">{v}dp</dimen>')
for k, v in sz.items():
    if "scale" not in k: d.append(f'    <dimen name="craze_size_{k.replace("-", "_")}">{v}dp</dimen>')
for k, v in rad.items():
    if k != "pill": d.append(f'    <dimen name="craze_radius_{k}">{v}dp</dimen>')
for k, v in light["elevation"].items(): d.append(f'    <dimen name="craze_elevation_{k}">{v}dp</dimen>')
for k, t in light["type"].items():
    d.append(f'    <dimen name="craze_text_{k.replace("-", "_")}">{t["size"]}sp</dimen>')
d.append(f'    <item name="craze_height_scale_min" format="float" type="dimen">{sz["keyboard-height-scale-min"]}</item>')
d.append(f'    <item name="craze_height_scale_max" format="float" type="dimen">{sz["keyboard-height-scale-max"]}</item>')
open("android/values/dimens.xml", "w").write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(d) + "\n</resources>\n")
print("wrote tokens.css, android/values/colors.xml, android/values-night/colors.xml, android/values/dimens.xml")

#!/usr/bin/env python3
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
svg = ET.parse(root / 'release-1.14.svg').getroot()
fonts = {
    'Shippori Mincho': 'shippori_mincho_bold.ttf',
    'IBM Plex Sans JP': 'ibm_plex_sans_jp_regular.ttf',
    'IBM Plex Mono': 'ibm_plex_mono_regular.ttf',
}
command = ['magick', '-size', f"{svg.attrib['width']}x{svg.attrib['height']}", 'xc:none']
for element in svg:
    tag = element.tag.rsplit('}', 1)[-1]
    a = element.attrib
    command += ['-fill', a.get('fill', 'none'), '-stroke', a.get('stroke', 'none'), '-strokewidth', '1']
    x, y = float(a.get('x', 0)), float(a.get('y', 0))
    if tag == 'rect':
        right, bottom = x + float(a['width']), y + float(a['height'])
        radius = a.get('rx', '0')
        shape = f'roundrectangle {x},{y} {right},{bottom} {radius},{radius}' if float(radius) else f'rectangle {x},{y} {right},{bottom}'
        command += ['-draw', shape]
    elif tag == 'text':
        font = root.parent / 'assets/fonts' / fonts[a['font-family']]
        command += ['-font', str(font), '-pointsize', a['font-size'], '-draw', f"text {x},{y} '{element.text}'"]
    elif tag == 'image':
        command += ['(', str(root / a['href']), '-resize', f"{a['width']}x{a['height']}", ')', '-geometry', f'+{int(x)}+{int(y)}', '-compose', 'Over', '-composite']
    else:
        raise ValueError(f'Unsupported SVG element: {tag}')
command += ['-depth', '8', '-alpha', 'off', str(root / 'release-1.14.png')]
subprocess.run(command, check=True)

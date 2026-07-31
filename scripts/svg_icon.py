#!/usr/bin/env python3
"""Generate Android launcher drawable resources from an SVG icon.

Reads an SVG (e.g. Affinity Designer export) and writes into the given res dir:
  ic_launcher_background.xml  adaptive icon background (linear gradient)
  ic_launcher_foreground.xml  adaptive icon foreground (VectorDrawable, 108dp)

Usage: svg_icon.py <icon.svg> <output-res-dir>
"""

import math
import os
import re
import sys
import xml.etree.ElementTree as ET

NUM = r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"
TOKEN_RE = re.compile(r"[MmLlCcSsQqTtZzHhVvAa]|" + NUM)
COLOR_RE = re.compile(r"rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)")
IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def local_tag(tag):
    return tag.rsplit("}", 1)[-1]


def is_number(tok):
    return tok[:1] in "-+.0123456789"


def compose(m1, m2):
    a, b, c, d, e, f = m1
    a2, b2, c2, d2, e2, f2 = m2
    return (
        a * a2 + c * b2,
        b * a2 + d * b2,
        a * c2 + c * d2,
        b * c2 + d * d2,
        a * e2 + c * f2 + e,
        b * e2 + d * f2 + f,
    )


def parse_transform(t):
    t = t.strip()
    m = re.search(r"matrix\s*\(\s*(" + NUM + r")\s*,\s*(" + NUM + r")\s*,\s*(" + NUM +
                  r")\s*,\s*(" + NUM + r")\s*,\s*(" + NUM + r")\s*,\s*(" + NUM + r")\s*\)", t)
    if m:
        return tuple(float(g) for g in m.groups())
    m = re.match(r"translate\(\s*(" + NUM + r")(?:\s*,\s*(" + NUM + r"))?\s*\)", t)
    if m:
        g = m.groups()
        return (1.0, 0.0, 0.0, 1.0, float(g[0]), float(g[1] or 0))
    m = re.match(r"scale\(\s*(" + NUM + r")(?:\s*,\s*(" + NUM + r"))?\s*\)", t)
    if m:
        g = m.groups()
        sx = float(g[0])
        sy = float(g[1]) if g[1] is not None else sx
        return (sx, 0.0, 0.0, sy, 0.0, 0.0)
    m = re.match(r"rotate\(\s*(" + NUM + r")(?:\s*,\s*(" + NUM + r")\s*,\s*(" + NUM + r"))?\s*\)", t)
    if m:
        g = m.groups()
        ang = math.radians(float(g[0]))
        cos, sin = math.cos(ang), math.sin(ang)
        rot = (cos, sin, -sin, cos, 0.0, 0.0)
        if g[1] is not None and g[2] is not None:
            cx, cy = float(g[1]), float(g[2])
            return compose(compose((1.0, 0.0, 0.0, 1.0, cx, cy), rot), (1.0, 0.0, 0.0, 1.0, -cx, -cy))
        return rot
    raise ValueError("unsupported SVG transform: %r" % t)


def transform_path(d, m):
    a, b, c, dd, e, f = m
    tokens = TOKEN_RE.findall(d)
    i = 0
    cx = cy = 0.0
    sx = sy = 0.0
    out = []
    bbox = [None] * 4
    last_cubic = None
    last_quad = None

    def emit(cmd, x, y):
        nonlocal bbox
        tx = a * x + c * y + e
        ty = b * x + dd * y + f
        if cmd:
            out.append(cmd)
        out.extend(["%.1f" % tx, "%.1f" % ty])
        if bbox[0] is None:
            bbox[0] = bbox[2] = tx
            bbox[1] = bbox[3] = ty
        else:
            bbox[0] = min(bbox[0], tx)
            bbox[1] = min(bbox[1], ty)
            bbox[2] = max(bbox[2], tx)
            bbox[3] = max(bbox[3], ty)

    while i < len(tokens):
        cmd = tokens[i]
        i += 1
        if cmd in "Mm":
            first = True
            while i < len(tokens) and is_number(tokens[i]):
                nx, ny = float(tokens[i]), float(tokens[i + 1])
                i += 2
                if cmd == "m":
                    nx += cx
                    ny += cy
                emit("M" if first else "L", nx, ny)
                if first:
                    first = False
                    sx, sy = nx, ny
                cx, cy = nx, ny
                last_cubic = last_quad = None
        elif cmd in "Ll":
            while i < len(tokens) and is_number(tokens[i]):
                nx, ny = float(tokens[i]), float(tokens[i + 1])
                i += 2
                if cmd == "l":
                    nx += cx
                    ny += cy
                emit("L", nx, ny)
                cx, cy = nx, ny
                last_cubic = last_quad = None
        elif cmd in "Hh":
            while i < len(tokens) and is_number(tokens[i]):
                nx = float(tokens[i])
                i += 1
                if cmd == "h":
                    nx += cx
                emit("L", nx, cy)
                cx = nx
                last_cubic = last_quad = None
        elif cmd in "Vv":
            while i < len(tokens) and is_number(tokens[i]):
                ny = float(tokens[i])
                i += 1
                if cmd == "v":
                    ny += cy
                emit("L", cx, ny)
                cy = ny
                last_cubic = last_quad = None
        elif cmd in "Cc":
            while i < len(tokens) and is_number(tokens[i]):
                x1, y1, x2, y2, xn, yn = (float(tokens[i + k]) for k in range(6))
                i += 6
                if cmd == "c":
                    x1 += cx
                    y1 += cy
                    x2 += cx
                    y2 += cy
                    xn += cx
                    yn += cy
                emit("C", x1, y1)
                emit("", x2, y2)
                emit("", xn, yn)
                last_cubic = (x2, y2)
                cx, cy = xn, yn
        elif cmd in "Ss":
            while i < len(tokens) and is_number(tokens[i]):
                x2, y2, xn, yn = (float(tokens[i + k]) for k in range(4))
                i += 4
                if cmd == "s":
                    x2 += cx
                    y2 += cy
                    xn += cx
                    yn += cy
                if last_cubic is not None:
                    x1, y1 = 2 * cx - last_cubic[0], 2 * cy - last_cubic[1]
                else:
                    x1, y1 = cx, cy
                emit("C", x1, y1)
                emit("", x2, y2)
                emit("", xn, yn)
                last_cubic = (x2, y2)
                cx, cy = xn, yn
        elif cmd in "Qq":
            while i < len(tokens) and is_number(tokens[i]):
                x1, y1, xn, yn = (float(tokens[i + k]) for k in range(4))
                i += 4
                if cmd == "q":
                    x1 += cx
                    y1 += cy
                    xn += cx
                    yn += cy
                emit("C", (cx + 2 * x1) / 3, (cy + 2 * y1) / 3)
                emit("", (2 * x1 + xn) / 3, (2 * y1 + yn) / 3)
                emit("", xn, yn)
                last_quad = (x1, y1)
                cx, cy = xn, yn
        elif cmd in "Tt":
            while i < len(tokens) and is_number(tokens[i]):
                xn, yn = float(tokens[i]), float(tokens[i + 1])
                i += 2
                if cmd == "t":
                    xn += cx
                    yn += cy
                if last_quad is not None:
                    x1, y1 = 2 * cx - last_quad[0], 2 * cy - last_quad[1]
                else:
                    x1, y1 = cx, cy
                emit("C", (cx + 2 * x1) / 3, (cy + 2 * y1) / 3)
                emit("", (2 * x1 + xn) / 3, (2 * y1 + yn) / 3)
                emit("", xn, yn)
                last_quad = (x1, y1)
                cx, cy = xn, yn
        elif cmd in "Zz":
            out.append("Z")
            cx, cy = sx, sy
            last_cubic = last_quad = None
        elif cmd in "Aa":
            raise ValueError("arc commands ('A') are not supported by VectorDrawable")
        else:
            raise ValueError("unknown path command %r" % cmd)

    return " ".join(out), tuple(bbox)


def parse_decl(style, name):
    for part in style.split(";"):
        part = part.strip()
        if ":" in part:
            k, v = part.split(":", 1)
            if k.strip() == name:
                return v.strip()
    return None


def color_to_hex(value):
    value = value.strip()
    if value.lower() in ("none", "transparent"):
        return None
    if value.startswith("#"):
        v = value[1:]
        if len(v) == 3:
            return "#" + "".join(ch * 2 for ch in v).upper()
        return "#" + v.upper()
    m = COLOR_RE.match(value)
    if m:
        return "#%02X%02X%02X" % tuple(int(g) for g in m.groups())
    raise ValueError("unsupported color: %r" % value)


def path_paint(el):
    style = el.get("style") or ""
    fill = parse_decl(style, "fill") or el.get("fill")
    stroke = parse_decl(style, "stroke") or el.get("stroke")
    sw_raw = parse_decl(style, "stroke-width") or el.get("stroke-width")
    fill_hex = color_to_hex(fill) if fill else None
    stroke_hex = color_to_hex(stroke) if stroke else None
    stroke_width = float(sw_raw.replace("px", "")) if sw_raw else None
    fill_alpha = parse_decl(style, "fill-opacity") or el.get("fill-opacity")
    fill_alpha = float(fill_alpha) if fill_alpha is not None else None
    return fill_hex, fill_alpha, stroke_hex, stroke_width


def find_gradient_stops(root):
    for el in root.iter():
        if local_tag(el.tag) != "linearGradient":
            continue
        stops = []
        for s in el.iter():
            if local_tag(s.tag) != "stop":
                continue
            style = s.get("style") or ""
            c = parse_decl(style, "stop-color") or s.get("stop-color")
            off = float(s.get("offset") or "0")
            if c:
                stops.append((off, color_to_hex(c)))
        if stops:
            stops.sort()
            return stops
    return None


def find_rect_fill(root):
    for el in root.iter():
        if local_tag(el.tag) != "rect":
            continue
        style = el.get("style") or ""
        fill = parse_decl(style, "fill") or el.get("fill")
        if not fill or fill.lstrip().startswith("url"):
            continue
        h = color_to_hex(fill)
        if h:
            return h
    return None


def fmt_number(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def write_background(out_dir, stops, solid):
    if stops:
        content = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <gradient\n'
            '        android:type="linear"\n'
            '        android:angle="45"\n'
            '        android:startColor="%s"\n'
            '        android:endColor="%s" />\n'
            '</shape>\n' % (stops[0][1], stops[-1][1])
        )
    elif solid:
        content = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <solid android:color="%s" />\n'
            '</shape>\n' % solid
        )
    else:
        print("warning: no gradient or rect fill found; keeping existing background", file=sys.stderr)
        return
    with open(os.path.join(out_dir, "ic_launcher_background.xml"), "w") as f:
        f.write(content)


def write_foreground(out_dir, paths):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ]
    for d, fill_hex, fill_alpha, stroke_hex, stroke_width in paths:
        lines.append("    <path")
        lines.append('        android:pathData="%s"' % d)
        if fill_hex:
            lines.append('        android:fillColor="%s"' % fill_hex)
        if fill_alpha is not None and fill_alpha < 1.0:
            lines.append('        android:fillAlpha="%s"' % fmt_number(fill_alpha))
        if stroke_hex:
            lines.append('        android:strokeWidth="%s"' % fmt_number(stroke_width))
            lines.append('        android:strokeColor="%s"' % stroke_hex)
        lines.append("    />")
    lines.append("</vector>")
    lines.append("")
    with open(os.path.join(out_dir, "ic_launcher_foreground.xml"), "w") as f:
        f.write("\n".join(lines))


def main():
    if len(sys.argv) != 3:
        print("usage: svg_icon.py <icon.svg> <output-res-dir>", file=sys.stderr)
        return 2
    svg_path, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)

    tree = ET.parse(svg_path)
    root = tree.getroot()
    parent_map = {c: p for p in root.iter() for c in p}

    raw_paths = []
    bbox = [None] * 4
    for el in root.iter():
        if local_tag(el.tag) != "path":
            continue
        d = el.get("d")
        if not d:
            continue
        m = IDENTITY
        node = el
        while node is not None:
            t = node.get("transform")
            if t:
                m = compose(m, parse_transform(t))
            node = parent_map.get(node)
        transformed, bb = transform_path(d, m)
        for k in range(4):
            if bb[k] is None:
                continue
            if bbox[k] is None:
                bbox[k] = bb[k]
            elif k < 2:
                bbox[k] = min(bbox[k], bb[k])
            else:
                bbox[k] = max(bbox[k], bb[k])
        raw_paths.append((transformed,) + path_paint(el))

    if not raw_paths:
        print("error: no <path> elements found in SVG", file=sys.stderr)
        return 1

    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    target = 68.0
    s = min(target / w, target / h) if w > 0 and h > 0 else 1.0
    offx = 54.0 - (bbox[0] + bbox[2]) / 2.0 * s
    offy = 54.0 - (bbox[1] + bbox[3]) / 2.0 * s
    final_m = (s, 0.0, 0.0, s, offx, offy)

    foreground = []
    for transformed, fill_hex, fill_alpha, stroke_hex, stroke_width in raw_paths:
        d, _ = transform_path(transformed, final_m)
        sw = stroke_width * s if stroke_width else None
        foreground.append((d, fill_hex, fill_alpha, stroke_hex, sw))

    write_background(out_dir, find_gradient_stops(root), find_rect_fill(root))
    write_foreground(out_dir, foreground)
    return 0


if __name__ == "__main__":
    sys.exit(main())

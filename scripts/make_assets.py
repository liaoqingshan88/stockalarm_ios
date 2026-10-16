#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 StockAlarm iOS 工程图标（纯标准库，不依赖 Pillow）：

  StockAlarm/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
  App 图标：红色渐变背景 + 白色上涨折线箭头（股价预警），1024x1024

用法：python3 scripts/make_assets.py
"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON_PATH = os.path.join(ROOT, "StockAlarm/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png")


def write_png(path, width, height, rows):
    """rows: list of bytearray，每行 width*3 个 RGB 字节"""
    raw = b"".join(b"\x00" + bytes(r) for r in rows)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def lerp(a, b, t):
    return a + (b - a) * t


def dist_to_segment(px, py, x1, y1, x2, y2):
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return ((px - x1) ** 2 + (py - y1) ** 2) ** 0.5
    t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
    cx, cy = x1 + t * dx, y1 + t * dy
    return ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5


def point_in_triangle(px, py, a, b, c):
    def sign(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])

    d1 = sign((px, py), a, b)
    d2 = sign((px, py), b, c)
    d3 = sign((px, py), c, a)
    has_neg = d1 < 0 or d2 < 0 or d3 < 0
    has_pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (has_neg and has_pos)


def make_icon(path, size=1024, ss=2):
    """ss = 超采样倍数（抗锯齿）"""
    top = (208, 56, 56)      # 亮红
    bottom = (130, 12, 22)   # 深红

    # 上涨折线（1024 设计坐标）与箭头头部
    segs = [
        (150, 700, 380, 470),
        (380, 470, 530, 580),
        (530, 580, 800, 300),
    ]
    line_half = 34
    arrow = ((800, 190), (800, 470), (935, 330))  # 箭头三角（向右上方）

    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            r = g = b = 0
            for sy in range(ss):
                for sx in range(ss):
                    px = x * ss + sx + 0.5
                    py = y * ss + sy + 0.5
                    t = py / (size * ss)
                    br = lerp(top[0], bottom[0], t)
                    bg = lerp(top[1], bottom[1], t)
                    bb = lerp(top[2], bottom[2], t)

                    on_line = any(dist_to_segment(px, py,
                                                  x1 * ss, y1 * ss, x2 * ss, y2 * ss) <= line_half * ss
                                  for x1, y1, x2, y2 in segs)
                    in_arrow = point_in_triangle(px, py, arrow[0], arrow[1], arrow[2])

                    if on_line or in_arrow:
                        br, bg, bb = 255, 255, 255
                    r += br
                    g += bg
                    b += bb
            n = ss * ss
            row += bytes((int(r / n), int(g / n), int(b / n)))
        rows.append(row)

    write_png(path, size, size, rows)
    print("icon  ->", path)


if __name__ == "__main__":
    make_icon(ICON_PATH)
#!/usr/bin/env python3
"""Generate honest cover/end cards for a cut of the existing demo video."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path("video-output")
OUT.mkdir(exist_ok=True)
W, H = 1280, 720
reg = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
bold = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

def card(path, label, title, subtitle, lines):
    im = Image.new("RGB", (W, H), (15, 27, 49))
    d = ImageDraw.Draw(im)
    for y in range(0, H, 4):
        t = y / H
        d.line((0, y, W, y), fill=(int(15 + 9*t), int(27 + 11*t), int(49 + 14*t)), width=4)
    d.rounded_rectangle((86, 92, 1194, 628), radius=24,
                        fill=(26,43,70), outline=(57,82,116), width=2)
    d.rounded_rectangle((132,141,144,188), radius=5, fill=(87,157,242))
    d.text((171,145), label, font=ImageFont.truetype(reg,28), fill=(169,193,224))
    d.text((130,229), title, font=ImageFont.truetype(bold,57), fill=(245,248,253))
    d.text((133,323), subtitle, font=ImageFont.truetype(reg,32), fill=(120,185,247))
    y = 426
    for line in lines:
        d.text((133,y), line, font=ImageFont.truetype(reg,25), fill=(198,211,230))
        y += 48
    d.text((133,588), "第10组 · 在线购物系统",
           font=ImageFont.truetype(reg,18), fill=(120,146,178))
    im.save(OUT/path)

card("intro.png", "项目视频 / 剪辑版", "在线购物系统", "现有功能演示",
     ["涵盖买家购买意向、卖家后台及商品管理操作",
      "本片不替代第二次挑战需求 A 的新增功能实测"])
card("outro.png", "第二次成果验收 / 待补充", "新增功能演示待补录", "挑战需求 A",
     ["手动下架二次确认 · 待处理意向及口令失效",
      "交易中的商品禁止直接下架",
      "需补录实际操作画面后才能构成完整本轮视频"])

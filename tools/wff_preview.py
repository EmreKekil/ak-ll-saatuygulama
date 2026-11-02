#!/usr/bin/env python3
"""Watch Face Format kadranlarının yaklaşık önizlemesi.

Kadranın gerçek watchface.xml dosyasını okur ve kullandığımız WFF alt kümesini (Group, PartDraw
şekilleri, PartText, DigitalClock/TimeText, AnalogClock ibreleri, ComplicationSlot, Condition,
Transform, Variant) örnek verilerle çizer. Amaç saatte denemeden önce yerleşimi görmek ve kadran
seçicideki preview.png'yi XML'le tutarlı üretmektir; saatin çizimiyle piksel piksel aynı değildir.

Kullanım:
  python3 tools/wff_preview.py <watchface.xml> <çıktı.png> [--ambient] [--size 450]
      [--slot 0=LONG_TEXT|İkindi 15:26 · 1sa 23dk] [--slot 1=SHORT_TEXT|6.2k|Adım]
      [--res <res klasörü>]
"""
import argparse
import math
import os
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

FONT_DIR = "/usr/share/fonts/opentype/inter"
WEIGHTS = {
    "THIN": "Thin", "ULTRA_LIGHT": "ExtraLight", "EXTRA_LIGHT": "ExtraLight", "LIGHT": "Light",
    "NORMAL": "Regular", "MEDIUM": "Medium", "SEMI_BOLD": "SemiBold", "BOLD": "Bold",
    "ULTRA_BOLD": "ExtraBold", "EXTRA_BOLD": "ExtraBold", "BLACK": "Black",
}

# Örnek veri: 10:08:32, Pazartesi 2 Kasım.
SAMPLE = {
    "HOUR_0_23": 10, "HOUR_1_12": 10, "HOUR_0_11": 10, "HOUR_1_24": 10, "MINUTE": 8, "SECOND": 32,
    "BATTERY_PERCENT": 78, "STEP_COUNT": 6240, "STEP_GOAL": 8000, "STEP_PERCENT": 78, "HEART_RATE": 72,
    "DAY": 2, "MONTH": 11, "YEAR": 2026, "DAY_OF_WEEK": 2,
    "DAY_OF_WEEK_S": "Pzt", "DAY_OF_WEEK_F": "Pazartesi", "MONTH_S": "Kas", "MONTH_F": "Kasım",
}
SAMPLE["SECOND_MILLISECOND"] = SAMPLE["SECOND"]
SAMPLE["MINUTE_SECOND"] = SAMPLE["MINUTE"] + SAMPLE["SECOND"] / 60
SAMPLE["HOUR_0_11_MINUTE"] = SAMPLE["HOUR_0_11"] + SAMPLE["MINUTE"] / 60


def font(size, weight="NORMAL"):
    name = WEIGHTS.get(weight, "Regular")
    for candidate in (f"Inter-{name}.otf", "Inter-Regular.otf"):
        path = os.path.join(FONT_DIR, candidate)
        if os.path.exists(path):
            return ImageFont.truetype(path, int(round(size)))
    return ImageFont.load_default()


def parse_color(value, config):
    value = substitute_config(value, config)
    v = value.lstrip("#")
    if len(v) == 6:
        v = "FF" + v
    a, r, g, b = (int(v[i:i + 2], 16) for i in (0, 2, 4, 6))
    return (r, g, b, a)


def substitute_config(text, config):
    def repl(m):
        option = config[m.group(1)]
        return option[int(m.group(2))]
    return re.sub(r"\[CONFIGURATION\.(\w+)\.(\d+)\]", repl, text)


class Ctx:
    def __init__(self, config, slots, ambient, res_dir):
        self.config = config
        self.slots = slots
        self.ambient = ambient
        self.res_dir = res_dir
        self.complication = None


def value_of(token, ctx):
    if token.startswith("COMPLICATION."):
        key = token.split(".", 1)[1]
        return (ctx.complication or {}).get(key)
    return SAMPLE.get(token)


def evaluate(expr, ctx):
    """Sayısal ifade; [ETİKET]'leri örnek değerlerle değiştirip hesaplar."""
    expr = substitute_config(expr, ctx.config)
    text = re.sub(r"\[([A-Z0-9_.]+)\]", lambda m: repr(value_of(m.group(1), ctx)), expr)
    text = text.replace("&&", " and ").replace("||", " or ").replace("!=", " != ")
    try:
        return eval(text, {"__builtins__": {}}, {"null": None, "None": None})
    except Exception:
        return None


def number(el, name, default=0.0):
    v = el.get(name)
    return float(v) if v is not None else default


def alpha_of(el, ctx):
    alpha = number(el, "alpha", 255)
    for v in el.findall("Variant"):
        if v.get("mode") == "AMBIENT" and v.get("target") == "alpha" and ctx.ambient:
            alpha = float(v.get("value"))
    return alpha


def apply_transforms(el, attrs, ctx):
    for t in el.findall("Transform"):
        v = evaluate(t.get("value"), ctx)
        if v is not None:
            attrs[t.get("target")] = float(v)
    return attrs


def new_layer(size):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def composite(base, layer, alpha):
    if alpha <= 0:
        return
    if alpha < 255:
        a = layer.getchannel("A").point(lambda p: int(p * alpha / 255))
        layer.putalpha(a)
    base.alpha_composite(layer)


def draw_shapes(part, ox, oy, canvas, ctx):
    layer = new_layer(canvas.size)
    d = ImageDraw.Draw(layer)
    for shape in part:
        tag = shape.tag
        if tag in ("Variant", "Transform"):
            continue
        a = apply_transforms(shape, {k: float(v) for k, v in shape.attrib.items() if re.match(r"^-?[\d.]+$", v)}, ctx)
        stroke = shape.find("Stroke")
        fill = shape.find("Fill")
        sc = parse_color(stroke.get("color"), ctx.config) if stroke is not None else None
        sw = int(round(number(stroke, "thickness", 1))) if stroke is not None else 0
        fc = parse_color(fill.get("color"), ctx.config) if fill is not None else None
        if tag == "Arc":
            cx, cy, w, h = ox + a["centerX"], oy + a["centerY"], a["width"], a["height"]
            box = [cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2]
            start, end = a.get("startAngle", 0), a.get("endAngle", 360)
            if end - start >= 360:
                d.ellipse(box, outline=sc, width=sw)
            elif end != start:
                d.arc(box, start - 90, end - 90, fill=sc, width=sw)
                if stroke is not None and stroke.get("cap") == "ROUND":
                    for ang in (start, end):
                        r = math.radians(ang - 90)
                        px, py = cx + w / 2 * math.cos(r), cy + h / 2 * math.sin(r)
                        d.ellipse([px - sw / 2, py - sw / 2, px + sw / 2, py + sw / 2], fill=sc)
        elif tag in ("Rectangle", "RoundRectangle", "Ellipse"):
            x, y, w, h = ox + a.get("x", 0), oy + a.get("y", 0), a.get("width", 0), a.get("height", 0)
            if w <= 0 or h <= 0:
                continue
            box = [x, y, x + w, y + h]
            if tag == "Ellipse":
                d.ellipse(box, fill=fc, outline=sc, width=sw)
            elif tag == "RoundRectangle":
                d.rounded_rectangle(box, radius=min(a.get("cornerRadiusX", 0), w / 2, h / 2), fill=fc, outline=sc, width=sw)
            else:
                d.rectangle(box, fill=fc, outline=sc, width=sw)
        elif tag == "Line":
            d.line([ox + a["startX"], oy + a["startY"], ox + a["endX"], oy + a["endY"]], fill=sc, width=sw)
    return layer


def text_of(template, ctx):
    params = []
    for p in template.findall("Parameter"):
        expr = p.get("expression")
        m = re.fullmatch(r"\[([A-Z0-9_.]+)\]", expr.strip())
        v = value_of(m.group(1), ctx) if m else evaluate(expr, ctx)
        if isinstance(v, float) and v.is_integer():
            v = int(v)
        params.append("" if v is None else str(v))
    fmt = (template.text or "").strip()
    try:
        return fmt % tuple(params)
    except TypeError:
        return fmt


def draw_text_box(canvas, text, box, f, color, align):
    x, y, w, h = box
    layer = new_layer(canvas.size)
    d = ImageDraw.Draw(layer)
    while text and d.textlength(text, font=f) > w and len(text) > 1:
        text = text[:-2] + "…"
    tw = d.textlength(text, font=f)
    ascent, descent = f.getmetrics()
    tx = {"START": x, "END": x + w - tw}.get(align, x + (w - tw) / 2)
    ty = y + (h - (ascent + descent)) / 2
    d.text((tx, ty), text, font=f, fill=color)
    return layer


def render_children(el, ox, oy, canvas, ctx):
    for child in el:
        render(child, ox, oy, canvas, ctx)


def render(el, ox, oy, canvas, ctx):
    tag = el.tag
    if tag in ("Variant", "Transform", "Metadata", "UserConfigurations", "Expressions", "BoundingRoundBox",
               "BoundingOval", "BoundingBox", "BoundingArc", "DefaultProviderPolicy"):
        return
    alpha = alpha_of(el, ctx)
    x, y = ox + number(el, "x"), oy + number(el, "y")
    if tag in ("Scene", "Group", "DigitalClock", "AnalogClock", "Complication", "Default", "Compare"):
        layer = new_layer(canvas.size)
        if tag == "AnalogClock":
            render_hands(el, x, y, layer, ctx)
        else:
            render_children(el, x, y, layer, ctx)
        composite(canvas, layer, alpha)
    elif tag == "Condition":
        names = {e.get("name"): e.text for e in el.find("Expressions")}
        for cmp in el.findall("Compare"):
            if evaluate(names[cmp.get("expression")], ctx):
                render(cmp, ox, oy, canvas, ctx)
                return
        default = el.find("Default")
        if default is not None:
            render(default, ox, oy, canvas, ctx)
    elif tag == "PartDraw":
        composite(canvas, draw_shapes(el, x, y, canvas, ctx), alpha)
    elif tag == "PartText":
        text_el = el.find("Text")
        f_el = text_el.find("Font")
        text = text_of(f_el.find("Template"), ctx)
        f = font(number(f_el, "size", 20), f_el.get("weight", "NORMAL"))
        color = parse_color(f_el.get("color", "#FFFFFFFF"), ctx.config)
        layer = draw_text_box(canvas, text, (x, y, number(el, "width"), number(el, "height")), f, color, text_el.get("align", "CENTER"))
        composite(canvas, layer, alpha)
    elif tag == "TimeText":
        f_el = el.find("Font")
        fmt = el.get("format")
        h = SAMPLE["HOUR_0_23"]
        values = {"hh": f"{h:02d}", "HH": f"{h:02d}", "h": str(h), "mm": f"{SAMPLE['MINUTE']:02d}",
                  "ss": f"{SAMPLE['SECOND']:02d}", "hh:mm": f"{h:02d}:{SAMPLE['MINUTE']:02d}"}
        f = font(number(f_el, "size", 40), f_el.get("weight", "NORMAL"))
        color = parse_color(f_el.get("color", "#FFFFFFFF"), ctx.config)
        layer = draw_text_box(canvas, values.get(fmt, fmt), (x, y, number(el, "width"), number(el, "height")), f, color, el.get("align", "CENTER"))
        composite(canvas, layer, alpha)
    elif tag == "ComplicationSlot":
        data = ctx.slots.get(el.get("slotId"))
        if not data:
            return
        for comp in el.findall("Complication"):
            if comp.get("type") == data["TYPE"]:
                ctx.complication = data
                layer = new_layer(canvas.size)
                render_children(comp, x, y, layer, ctx)
                composite(canvas, layer, alpha)
                ctx.complication = None
                return
    else:
        render_children(el, x, y, canvas, ctx)


def render_hands(clock, x, y, canvas, ctx):
    cw, ch = number(clock, "width"), number(clock, "height")
    angles = {
        "HourHand": (SAMPLE["HOUR_0_11"] + SAMPLE["MINUTE"] / 60) * 30,
        "MinuteHand": (SAMPLE["MINUTE"] + SAMPLE["SECOND"] / 60) * 6,
        "SecondHand": SAMPLE["SECOND"] * 6,
    }
    for hand in clock:
        if hand.tag not in angles or alpha_of(hand, ctx) <= 0:
            continue
        path = os.path.join(ctx.res_dir, "drawable", hand.get("resource") + ".png")
        img = Image.open(path).convert("RGBA")
        hw, hh = number(hand, "width"), number(hand, "height")
        img = img.resize((int(hw), int(hh)))
        tint = hand.get("tintColor")
        if tint:
            c = parse_color(tint, ctx.config)
            solid = Image.new("RGBA", img.size, c[:3] + (255,))
            solid.putalpha(img.getchannel("A"))
            img = solid
        px, py = number(hand, "pivotX", 0.5) * hw, number(hand, "pivotY", 0.5) * hh
        hx, hy = x + number(hand, "x"), y + number(hand, "y")
        big = new_layer(canvas.size)
        big.alpha_composite(img, (int(hx), int(hy)))
        cx, cy = hx + px, hy + py
        big = big.rotate(-angles[hand.tag], center=(cx, cy), resample=Image.BICUBIC)
        canvas.alpha_composite(big)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("out")
    ap.add_argument("--ambient", action="store_true")
    ap.add_argument("--size", type=int, default=450)
    ap.add_argument("--slot", action="append", default=[], help="id=TYPE|TEXT|TITLE|VALUE")
    ap.add_argument("--res", default=None)
    ap.add_argument("--round", action="store_true", help="yuvarlak maske uygula")
    args = ap.parse_args()

    root = ET.parse(args.xml).getroot()
    config = {}
    for cc in root.iter("ColorConfiguration"):
        opt = next(o for o in cc.findall("ColorOption") if o.get("id") == cc.get("defaultValue"))
        config[cc.get("id")] = opt.get("colors").split()
    slots = {}
    for s in args.slot:
        sid, rest = s.split("=", 1)
        parts = rest.split("|")
        data = {"TYPE": parts[0], "TEXT": parts[1] if len(parts) > 1 else None,
                "TITLE": parts[2] if len(parts) > 2 and parts[2] else None}
        if len(parts) > 3:
            data.update(RANGED_VALUE_VALUE=float(parts[3]), RANGED_VALUE_MIN=0.0, RANGED_VALUE_MAX=1.0)
        slots[sid] = data
    res_dir = args.res or os.path.join(os.path.dirname(os.path.dirname(args.xml)))

    w, h = int(root.get("width")), int(root.get("height"))
    scene = root.find("Scene")
    canvas = Image.new("RGBA", (w, h), parse_color(scene.get("backgroundColor", "#FF000000"), config))
    render(scene, 0, 0, canvas, Ctx(config, slots, args.ambient, res_dir))
    if args.round:
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, w - 1, h - 1], fill=255)
        bg = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        bg.paste(canvas, (0, 0), mask)
        canvas = bg
    canvas = canvas.resize((args.size, args.size), Image.LANCZOS)
    canvas.save(args.out)


if __name__ == "__main__":
    main()

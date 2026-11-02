#!/usr/bin/env python3
"""tr_districts.csv'ye Diyanet ilçe kimliği (diyanet_id) sütununu ekler.

Diyanet'in il/ilçe listeleri ezanvakti.emushaf.net'ten çekilir ve adlar Türkçe harf/aksan
normalizasyonuyla eşleştirilir; Diyanet'in aynı adlı ilçeleri ayırmak için eklediği "(K)" gibi ekler
yok sayılır. Diyanet'te ayrı kaydı olmayan ilçeler (çoğunlukla büyükşehir merkez ilçeleri) aynı ildeki
en yakın eşleşmiş ilçenin kaydına atanır.

Kullanım: python3 qibla/tools/build_diyanet_ids.py qibla/src/main/assets/tr_districts.csv
"""
import csv
import io
import json
import math
import sys
import time
import unicodedata
import urllib.request

API = "https://ezanvakti.emushaf.net"

# Diyanet listesinde farklı yazılan adlar: (plaka, tablodaki ad) -> Diyanet adı (normalize).
ALIASES = {
    (4, "Doğubayazıt"): "DOGUBEYAZIT",
    (59, "Marmaraereğlisi"): "MEREGLISI",
}


def fetch(path):
    for attempt in range(4):
        try:
            # Sunucu Python'un varsayılan istemci kimliğini reddediyor (403).
            request = urllib.request.Request(API + path, headers={"User-Agent": "EzanVaktiKible-tools/1.0"})
            with urllib.request.urlopen(request, timeout=30) as r:
                return json.load(r)
        except Exception:
            time.sleep(2 ** attempt)
    raise RuntimeError(path)


def norm(name):
    name = name.split("(")[0]
    s = name.replace("İ", "I").replace("ı", "i").upper()
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return "".join(c for c in s if c.isalnum())


def distance_km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (float(a["lat"]), float(a["lon"]), float(b["lat"]), float(b["lon"])))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def main(path):
    with open(path, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    provinces = {}
    for r in rows:
        provinces.setdefault(int(r["plate"]), r["province"])

    cities = {norm(c["SehirAdi"]): c["SehirID"] for c in fetch("/sehirler/2")}
    unmatched, fallback = [], []
    by_plate = {}
    for plate, name in sorted(provinces.items()):
        city_id = cities.get(norm(name))
        if city_id is None:
            sys.exit(f"İl eşleşmedi: {plate} {name}")
        by_plate[plate] = {norm(i["IlceAdi"]): int(i["IlceID"]) for i in fetch(f"/ilceler/{city_id}")}
        time.sleep(0.3)

    matched = []
    for r in rows:
        ids = by_plate[int(r["plate"])]
        diyanet = ids.get(ALIASES.get((int(r["plate"]), r["district"]), norm(r["district"])))
        if diyanet is None and r["district"] == "Merkez":
            diyanet = ids.get(norm(r["province"]))
        if diyanet is None and r["is_province_center"] == "1":
            diyanet = ids.get(norm(r["province"]))
        r["diyanet_id"] = str(diyanet) if diyanet else ""
        if diyanet:
            matched.append(r)

    for r in rows:
        if r["diyanet_id"]:
            continue
        # Diyanet'te ayrı kayıt yok: aynı ildeki en yakın eşleşmiş ilçenin vakitleri kullanılır.
        same = [m for m in matched if m["plate"] == r["plate"]]
        if not same:
            unmatched.append(f'{r["province"]}/{r["district"]}')
            continue
        nearest = min(same, key=lambda m: distance_km(r, m))
        r["diyanet_id"] = nearest["diyanet_id"]
        fallback.append(f'{r["province"]}/{r["district"]} -> {nearest["district"]} ({distance_km(r, nearest):.0f} km)')

    if unmatched:
        sys.exit("Eşleşmeyen: " + ", ".join(unmatched))
    fields = ["plate", "province", "district", "lat", "lon", "is_province_center", "diyanet_id", "source"]
    out = io.StringIO()
    w = csv.DictWriter(out, fieldnames=fields, lineterminator="\n")
    w.writeheader()
    for r in rows:
        w.writerow({k: r[k] for k in fields})
    with open(path, "w", encoding="utf-8") as f:
        f.write(out.getvalue())
    print(f"{len(rows)} ilçe; en yakın Diyanet kaydına atanan {len(fallback)}:")
    print("\n".join(fallback))


if __name__ == "__main__":
    main(sys.argv[1])

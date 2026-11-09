"""把原站 data.js / data-anime.js / data-portrait.js + index.html 的 EN2ZH 词典
转成 App 内置 assets/*.json（只含 App 需要的字段）。原站为 MIT 开源。"""
import json, re

BASE = "https://paipiange.github.io/nvzhuang/assets/"

def load_js(path, var):
    s = open(path, encoding="utf-8", errors="ignore").read()
    m = re.search(re.escape(var) + r"\s*=\s*(\[.*?\]);", s, re.S)
    return json.loads(m.group(1))

def load_meta(path, var):
    s = open(path, encoding="utf-8", errors="ignore").read()
    m = re.search(re.escape(var) + r"\s*=\s*(\{.*?\});", s, re.S)
    return json.loads(m.group(1))

html = open("index.html", encoding="utf-8").read()
m = re.search(r"const EN2ZH = \{(.*?)\n\};", html, re.S)
en2zh = dict(re.findall(r"'([^']+)':'([^']+)'", m.group(1)))
print("EN2ZH entries:", len(en2zh))

def zh_tags(kw):
    return [en2zh[t] for t in kw.split() if t in en2zh]

# --- 写实 ---
meta = load_meta("data.js", "META")
d = load_js("data.js", "D")
costume = [{
    "id": x["id"], "cat": x["カテゴリ"], "kw": x["キーワード"],
    "zh": zh_tags(x["キーワード"]),
    "thumb": BASE + x["サムネ"], "img": BASE + x["画像"],
    # 注意：原站 assets/png/*.png 目前全部 404（实测），下载用 img webp
    "full": BASE + x["画像"],
} for x in d]
print("costume:", len(costume), "meta total:", meta["total"], "counts:", meta["counts"])

# --- 动漫 ---
meta_a = load_meta("data-anime.js", "META_ANIME")
d_a = load_js("data-anime.js", "D_ANIME")
anime = [{
    "id": x["id"], "cat": x["カテゴリ"], "kw": x["キーワード"],
    "zh": zh_tags(x["キーワード"]),
    "thumb": BASE + x["サムネ"], "img": BASE + x["画像"],
    "full": x["原寸"],  # 外站 pages.dev PNG，实测 200
} for x in d_a]
print("anime:", len(anime), "meta total:", meta_a["total"])

# --- AI 模特 ---
meta_p = load_meta("data-portrait.js", "META_PORTRAIT")
d_p = load_js("data-portrait.js", "D_PORTRAIT")
portrait = [{
    "id": x["id"], "cat": x["カテゴリ"], "kw": x["キーワード"],
    "zh": [x.get("hairZh",""), x.get("secZh","")] + ([x["styleZh"]] if x.get("styleZh") else []),
    "thumb": BASE + x["サムネ"], "img": BASE + x["画像"],
    "full": x["原寸"],  # 外站 pages.dev PNG，实测 200
    "sec": x.get("sec",""), "secZh": x.get("secZh",""),
    "hairZh": x.get("hairZh",""), "styleZh": x.get("styleZh",""),
} for x in d_p]
print("portrait:", len(portrait), "meta total:", meta_p["total"])

import os
os.makedirs("assets_out", exist_ok=True)
for name, arr in [("costume", costume), ("anime", anime), ("portrait", portrait)]:
    with open(f"assets_out/{name}.json", "w", encoding="utf-8") as f:
        json.dump(arr, f, ensure_ascii=False, separators=(",", ":"))
    print(name, os.path.getsize(f"assets_out/{name}.json"), "bytes")
with open("assets_out/en2zh.json", "w", encoding="utf-8") as f:
    json.dump(en2zh, f, ensure_ascii=False, separators=(",", ":"))
# sanity: ids unique per file, thumb/img present
for name, arr in [("costume", costume), ("anime", anime), ("portrait", portrait)]:
    ids = [x["id"] for x in arr]
    assert len(set(ids)) == len(ids), name
    assert all(x["thumb"] and x["img"] for x in arr)
print("OK: ids unique, urls present")

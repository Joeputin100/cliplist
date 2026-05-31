#!/usr/bin/env python3
"""Single source of truth -> Android string resources.
Edit localization/strings.json, then run: python3 localization/generate_strings.py"""
import json, os, html
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES=os.path.join(ROOT,"app/src/main/res")
QUAL={"en":"values","es":"values-es","fr":"values-fr","de":"values-de","it":"values-it",
      "ru":"values-ru","ja":"values-ja","ko":"values-ko","pt-BR":"values-pt-rBR","zh-CN":"values-zh-rCN"}
def esc(s):
    s=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    s=s.replace("'","\\'").replace('"','\\"')
    return s
data=json.load(open(os.path.join(ROOT,"localization/strings.json")))
for lang,strings in data.items():
    d=os.path.join(RES,QUAL[lang]); os.makedirs(d,exist_ok=True)
    lines=['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for k in sorted(strings):
        lines.append(f'    <string name="{k}">{esc(strings[k])}</string>')
    lines+=['</resources>','']
    open(os.path.join(d,"strings.xml"),"w",encoding="utf-8").write("\n".join(lines))
    print("wrote", QUAL[lang], f"({len(strings)} strings)")
# locale config for per-app language (used in 3c-2)
xmld=os.path.join(RES,"xml"); os.makedirs(xmld,exist_ok=True)
lc=['<?xml version="1.0" encoding="utf-8"?>',
    '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
for lang in data: lc.append(f'    <locale android:name="{lang}"/>')
lc+=['</locale-config>','']
open(os.path.join(xmld,"locales_config.xml"),"w").write("\n".join(lc))
print("wrote xml/locales_config.xml")

import os, numpy as np
from PIL import Image, ImageDraw
RES="/home/projects/mpc/app/src/main/res"
A=np.array([14,124,123]); B=np.array([168,207,69])  # teal #0E7C7B -> lime #A8CF45
TEAL="#0E7C7B"; LIME="#A8CF45"
def mk(*p): 
    d=os.path.join(RES,*p); os.makedirs(os.path.dirname(d),exist_ok=True); return d
# ---- vector geometry: 5 rounded-bar "play" mark in a 108 viewport ----
def rrect_path(x,y,w,h,r):
    f=lambda v:f"{v:.2f}"
    return (f"M{f(x+r)},{f(y)} h{f(w-2*r)} a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)} "
            f"v{f(h-2*r)} a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(r)} h{f(-(w-2*r))} "
            f"a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(-r)} v{f(-(h-2*r))} a{f(r)},{f(r)} 0 0 1 {f(r)},{f(-r)} z")
def bar_paths(V=108.0,scale=0.46):
    bw=V*scale; cx=cy=V/2; x0=cx-bw/2; bhmax=V*0.52; step=bw/5; barw=step*0.66; r=barw/2
    out=[]
    for i in range(5):
        h=bhmax*(1-i/4*0.82); bx=x0+i*step+(step-barw)/2
        out.append(rrect_path(bx,cy-h/2,barw,h,r))
    return out
PATHS=bar_paths()
def vector_bars(color):
    paths="\n".join(f'    <path android:fillColor="{color}" android:pathData="{p}"/>' for p in PATHS)
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'+paths+'\n</vector>\n')
BG=('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    xmlns:aapt="http://schemas.android.com/aapt"\n'
    '    android:width="108dp" android:height="108dp"\n'
    '    android:viewportWidth="108" android:viewportHeight="108">\n'
    '    <path android:pathData="M0,0h108v108h-108z">\n'
    '        <aapt:attr name="android:fillColor">\n'
    '            <gradient android:type="linear" android:startX="0" android:startY="0"\n'
    '                android:endX="108" android:endY="108">\n'
    f'                <item android:offset="0" android:color="{TEAL}"/>\n'
    f'                <item android:offset="1" android:color="{LIME}"/>\n'
    '            </gradient>\n'
    '        </aapt:attr>\n'
    '    </path>\n</vector>\n')
ADAPTIVE=('<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
    '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
    '</adaptive-icon>\n')
open(mk("drawable","ic_launcher_background.xml"),"w").write(BG)
open(mk("drawable","ic_launcher_foreground.xml"),"w").write(vector_bars("#FFFFFFFF"))
open(mk("drawable","ic_launcher_monochrome.xml"),"w").write(vector_bars("#FFFFFFFF"))
open(mk("mipmap-anydpi-v26","ic_launcher.xml"),"w").write(ADAPTIVE)
open(mk("mipmap-anydpi-v26","ic_launcher_round.xml"),"w").write(ADAPTIVE)
# ---- legacy raster icons (API 24-25) ----
def gradient(sz):
    yy,xx=np.mgrid[0:sz,0:sz]; t=((xx+yy)/(2*(sz-1)))[...,None]
    return Image.fromarray((A*(1-t)+B*t).astype("uint8"),"RGB")
def bars_png(sz):
    im=Image.new("RGBA",(sz,sz),(0,0,0,0)); d=ImageDraw.Draw(im)
    bw=sz*0.46; cx=cy=sz/2; x0=cx-bw/2; bhmax=sz*0.52; step=bw/5; barw=step*0.66
    for i in range(5):
        h=bhmax*(1-i/4*0.82); bx=x0+i*step+(step-barw)/2
        d.rounded_rectangle([bx,cy-h/2,bx+barw,cy+h/2],radius=barw/2,fill=(255,255,255,255))
    return im
def full(sz):
    bg=gradient(sz).convert("RGBA"); bg.alpha_composite(bars_png(sz)); return bg
def rsq(sz,r=0.22):
    m=Image.new("L",(sz,sz),0); ImageDraw.Draw(m).rounded_rectangle([0,0,sz-1,sz-1],radius=int(sz*r),fill=255); return m
def circ(sz):
    m=Image.new("L",(sz,sz),0); ImageDraw.Draw(m).ellipse([0,0,sz-1,sz-1],fill=255); return m
for dens,sz in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
    sq=full(sz).copy(); sq.putalpha(rsq(sz)); sq.save(mk(f"mipmap-{dens}","ic_launcher.png"))
    rd=full(sz).copy(); rd.putalpha(circ(sz)); rd.save(mk(f"mipmap-{dens}","ic_launcher_round.png"))
# Play Store icon (kept outside res, for listing)
full(512).convert("RGB").save("/home/projects/mpc/docs/mockups/play_store_icon_512.png")
print("wrote icon resources under app/src/main/res + play_store_icon_512.png")

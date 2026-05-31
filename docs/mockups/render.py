#!/usr/bin/env python3
"""My Playlist Creator 2026 — Material 3 Expressive mockups, light + dark.
Edge-to-edge, brand blue/orange, Pillow 2x supersampled."""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

S = 2
PW, PH = 360, 740
GAP, MARGIN, LABEL_H = 44, 46, 44
TOPSTRIP, ROWHEAD, GAPROWS = 40, 30, 26
N = 4
W = MARGIN*2 + N*PW + (N-1)*GAP
H = TOPSTRIP + (ROWHEAD+PH+LABEL_H) + GAPROWS + (ROWHEAD+PH+LABEL_H) + MARGIN

def px(v): return int(round(v*S))
CW, CH = px(W), px(H)

LIGHT = dict(
    surface=(251,250,254), container=(241,240,247), container_h=(233,231,240),
    primary=(0,83,164), on_primary=(255,255,255), pri_cont=(214,227,255),
    on_pri_cont=(0,29,57), pri_label=(11,57,110), pri_sub=(40,74,120),
    orange=(193,87,0), sec_cont=(255,220,196), on_sec_cont=(80,38,0),
    on_surf=(28,27,31), on_surf_var=(92,90,99),
    outline=(200,198,207), outline_v=(226,224,233),
    green=(24,118,71), green_cont=(188,233,201), on_green_c=(0,33,17),
    btn_fill=(0,83,164), btn_text=(255,255,255), sysbar=(28,27,31),
    gesture=(168,166,176), page_bg=(255,255,255), strip=(120,120,130),
    scrim=(0,0,0,46))
DARK = dict(
    surface=(17,19,24), container=(30,32,40), container_h=(41,43,52),
    primary=(162,201,255), on_primary=(0,49,95), pri_cont=(8,68,128),
    on_pri_cont=(212,227,255), pri_label=(186,210,248), pri_sub=(168,196,236),
    orange=(255,184,124), sec_cont=(92,46,0), on_sec_cont=(255,220,196),
    on_surf=(228,226,234), on_surf_var=(170,168,178),
    outline=(92,94,104), outline_v=(48,50,59),
    green=(132,226,172), green_cont=(0,72,42), on_green_c=(192,241,206),
    btn_fill=(162,201,255), btn_text=(0,49,95), sysbar=(232,230,238),
    gesture=(96,98,108), page_bg=(20,21,27), strip=(150,152,165),
    scrim=(0,0,0,60))

FB="/usr/share/fonts/truetype/dejavu/DejaVuSans"
PATHS={"r":FB+".ttf","b":FB+"-Bold.ttf","l":FB+"-ExtraLight.ttf"}
_fc={}
def f(size,w="r"):
    k=(size,w)
    if k not in _fc: _fc[k]=ImageFont.truetype(PATHS[w],px(size))
    return _fc[k]

img=Image.new("RGB",(CW,CH),LIGHT["page_bg"])
T=LIGHT  # current theme (set per row)

def rrect(box,r,fill=None,outline=None,width=1):
    img_d.rounded_rectangle([px(box[0]),px(box[1]),px(box[2]),px(box[3])],
        radius=px(r),fill=fill,outline=outline,width=px(width) if outline else 1)
def circle(cx,cy,r,fill=None,outline=None,width=1):
    img_d.ellipse([px(cx-r),px(cy-r),px(cx+r),px(cy+r)],fill=fill,
        outline=outline,width=px(width) if outline else 1)
def text(x,y,s,size,fill,w="r",anchor="la"):
    img_d.text((px(x),px(y)),s,font=f(size,w),fill=fill,anchor=anchor)
def tw(s,size,w="r"): return img_d.textlength(s,font=f(size,w))/S
def fit(s,maxw,start,w="b"):
    sz=start
    while sz>11 and tw(s,sz,w)>maxw: sz-=1
    return sz

def gear(cx,cy,r,col):
    for k in range(8):
        a=k*math.pi/4
        circle(cx+math.cos(a)*r,cy+math.sin(a)*r,r*0.30,fill=col)
    circle(cx,cy,r*0.95,fill=col); circle(cx,cy,r*0.42,fill=T["surface"])
def folder(x,y,wd,col):
    h=wd*0.78
    rrect([x,y+h*0.18,x+wd*0.46,y+h*0.40],3,fill=col)
    rrect([x,y+h*0.30,x+wd,y+h],6,fill=col)
def back(x,y,col):
    img_d.line([(px(x+12),px(y)),(px(x),px(y+10)),(px(x+12),px(y+20))],
        fill=col,width=px(2.4),joint="curve")
    img_d.line([(px(x),px(y+10)),(px(x+22),px(y+10))],fill=col,width=px(2.4))
def chevron(x,y,col,sc=1.0):
    img_d.line([(px(x),px(y)),(px(x+6*sc),px(y+6*sc)),(px(x),px(y+12*sc))],
        fill=col,width=px(2.2),joint="curve")
def switch(x,y,on):
    w_,h_=52,32
    if on:
        rrect([x,y,x+w_,y+h_],16,fill=T["primary"]); circle(x+w_-16,y+16,11,fill=T["on_primary"])
    else:
        rrect([x,y,x+w_,y+h_],16,fill=T["container_h"],outline=T["outline"],width=2)
        circle(x+16,y+16,8,fill=T["outline"])
def statusbar(ox,oy):
    c=T["sysbar"]
    text(ox+22,oy+16,"9:30",12,c,"b","lm")
    for i,bh in enumerate([4,7,10,13]):
        img_d.rectangle([px(ox+PW-72+i*7),px(oy+20-bh*0.7),px(ox+PW-67+i*7),px(oy+20)],fill=c)
    rrect([ox+PW-34,oy+11,ox+PW-16,oy+21],3,outline=c,width=1.5)
    rrect([ox+PW-32.5,oy+12.5,ox+PW-22,oy+19.5],1.5,fill=c)
def gesturebar(ox,oy):
    rrect([ox+PW/2-55,oy+PH-15,ox+PW/2+55,oy+PH-11],3,fill=T["gesture"])
def pill(lo,hi,y,h,label,fill,fg,chev=False,outline=None):
    rrect([lo,y,hi,y+h],h/2,fill=fill,outline=outline,width=1.6 if outline else 1)
    cx=(lo+hi)/2; lw=tw(label,15,"b")
    text(cx-lw/2,y+h/2,label,15,fg,"b","lm")
    if chev: chevron(cx+lw/2+10,y+h/2-6,fg)
def phone_bg(ox,oy):
    rrect([ox,oy,ox+PW,oy+PH],46,fill=T["surface"])
def edge_scrim(ox,oy):
    # subtle top scrim => content visibly runs under the status bar (edge-to-edge)
    hh=34
    grad=Image.new("RGBA",(px(PW),px(hh)),(0,0,0,0)); gd=ImageDraw.Draw(grad)
    r,g,b,amax=T["scrim"]
    for i in range(px(hh)):
        a=int(amax*(1-i/px(hh)))
        gd.line([(0,i),(px(PW),i)],fill=(r,g,b,a))
    mask=Image.new("L",(px(PW),px(hh)),0); md=ImageDraw.Draw(mask)
    md.rounded_rectangle([0,0,px(PW),px(hh)+px(46)],radius=px(46),fill=255)
    img.paste(grad,(px(ox),px(oy)),Image.composite(grad.split()[3],Image.new("L",grad.size,0),mask))

# ----------------------------- HOME -----------------------------------------
def home(ox,oy):
    phone_bg(ox,oy)
    pad=22; x=ox+pad; y=oy+46
    sz=fit("My Playlist Creator",PW-2*pad-2,24)
    text(x,y+4,"My Playlist Creator",sz,T["on_surf"],"b")
    gear(ox+PW-pad-11,y+15,11,T["on_surf_var"])
    text(x,y+38,"2026  ·  for SanDisk Clip Sport",12,T["on_surf_var"])
    y+=68
    rrect([x,y,ox+PW-pad,y+92],26,fill=T["pri_cont"])
    circle(x+34,y+46,22,fill=T["surface"]); folder(x+22,y+34,24,T["primary"])
    text(x+70,y+24,"MUSIC FOLDER",10.5,T["pri_label"],"b")
    text(x+70,y+43,"SD card",17,T["on_pri_cont"],"b")
    text(x+70,y+64,"/Music",13,T["pri_sub"])
    text(ox+PW-pad-16,y+46,"Change",12.5,T["primary"],"b","rm")
    y+=112
    rows=[("Search subfolders",None,True),("Alphabetize tracks",None,True),
          ("Clean file names","Plain ASCII for SanDisk Clip Sport",False),
          ("Rename hidden files","For SanDisk Clip Sport compatibility",False)]
    ch=len(rows)*64+8; rrect([x,y,ox+PW-pad,y+ch],24,fill=T["container"]); ry=y+12
    for i,(t,s,on) in enumerate(rows):
        text(x+18,ry+(22 if s else 26),t,14.5,T["on_surf"],"b")
        if s: text(x+18,ry+44,s,11.5,T["on_surf_var"])
        switch(ox+PW-pad-18-52,ry+18,on)
        if i<len(rows)-1:
            img_d.line([(px(x+18),px(ry+58)),(px(ox+PW-pad-18),px(ry+58))],fill=T["outline_v"],width=px(1))
        ry+=64
    text(ox+PW/2,oy+PH-106,"Creates or replaces a .m3u in every music folder",11,T["on_surf_var"],"r","mm")
    pill(x,ox+PW-pad,oy+PH-86,54,"Scan folder",T["btn_fill"],T["btn_text"],chev=True)
    edge_scrim(ox,oy); statusbar(ox,oy); gesturebar(ox,oy)

# ---------------------------- PREVIEW ---------------------------------------
def preview(ox,oy):
    phone_bg(ox,oy)
    pad=20; x=ox+pad; y=oy+42
    back(x+2,y+8,T["on_surf"]); text(x+40,y+18,"Preview",21,T["on_surf"],"b","lm")
    y+=52
    rrect([x,y,ox+PW-pad,y+74],24,fill=T["pri_cont"])
    text(x+20,y+24,"12 playlists",22,T["on_pri_cont"],"b")
    text(x+20,y+52,"348 tracks total",13,T["pri_sub"])
    bw=tw("Within limits",10,"b")+38
    rrect([ox+PW-pad-bw-12,y+26,ox+PW-pad-12,y+48],11,fill=T["green_cont"])
    text(ox+PW-pad-bw-12+14,y+37,"✓",11,T["green"],"b","lm")
    text(ox+PW-pad-bw-12+28,y+37,"Within limits",10,T["on_green_c"],"b","lm")
    y+=90; text(x+2,y,"FOLDERS",11,T["on_surf_var"],"b"); y+=22
    items=[("Rock","24 tracks",None,"NEW","pri"),
           ("Jazz Classics","18 tracks",None,"REPLACE","sec"),
           ("Cafe Music","31 tracks  •  renamed","from “Café Music”","NEW","pri"),
           ("Podcasts","47 tracks",None,"NEW","pri")]
    for t,s,s2,bl,kind in items:
        rh=58; rrect([x,y,ox+PW-pad,y+rh],18,fill=T["container"])
        circle(x+30,y+rh/2,17,fill=T["pri_cont"]); folder(x+20,y+rh/2-9,20,T["primary"])
        text(x+58,y+(15 if not s2 else 12),t,15,T["on_surf"],"b")
        text(x+58,y+(35 if not s2 else 30),s,11.5,T["on_surf_var"])
        if s2: text(x+58,y+44,s2,10.5,T["orange"],"b")
        bfg=T["primary"] if kind=="pri" else T["orange"]
        bbg=T["pri_cont"] if kind=="pri" else T["sec_cont"]
        bw=tw(bl,10,"b")+20
        rrect([ox+PW-pad-bw-12,y+rh/2-11,ox+PW-pad-12,y+rh/2+11],11,outline=bfg,width=1.4,fill=bbg)
        text(ox+PW-pad-12-bw/2,y+rh/2,bl,10,bfg,"b","mm")
        y+=rh+10
    y=oy+PH-78
    pill(x,ox+PW-pad,y,54,"Generate playlists",T["btn_fill"],T["btn_text"])
    edge_scrim(ox,oy); statusbar(ox,oy); gesturebar(ox,oy)

# ---------------------------- PROGRESS --------------------------------------
def progress(ox,oy):
    phone_bg(ox,oy)
    pad=22; x=ox+pad
    text(ox+PW/2,oy+58,"Generating…",21,T["on_surf"],"b","mm")
    cx,cy,r=ox+PW/2,oy+300,92
    img_d.ellipse([px(cx-r),px(cy-r),px(cx+r),px(cy+r)],outline=T["outline_v"],width=px(14))
    img_d.arc([px(cx-r),px(cy-r),px(cx+r),px(cy+r)],-90,-90+int(360*0.58),fill=T["primary"],width=px(14))
    text(cx,cy-8,"58%",40,T["on_surf"],"l","mm")
    text(cx,cy+30,"7 of 12 folders",13,T["on_surf_var"],"r","mm")
    y=oy+440; rrect([x,y,ox+PW-pad,y+70],22,fill=T["container"])
    text(x+20,y+22,"WRITING",10.5,T["primary"],"b")
    text(x+20,y+42,"Jazz Classics.m3u",16,T["on_surf"],"b")
    circle(ox+PW-pad-34,y+35,16,fill=T["pri_cont"]); text(ox+PW-pad-34,y+35,"♪",16,T["primary"],"r","mm")
    y=oy+540; rrect([x,y,ox+PW-pad,y+8],4,fill=T["outline_v"]); rrect([x,y,x+(PW-2*pad)*0.58,y+8],4,fill=T["primary"])
    text(x,y+24,"Writing .m3u files in CRLF format",12,T["on_surf_var"])
    y=oy+PH-78; pill(x,ox+PW-pad,y,54,"Cancel",T["surface"],T["on_surf_var"],outline=T["outline"])
    edge_scrim(ox,oy); statusbar(ox,oy); gesturebar(ox,oy)

# ---------------------------- RESULTS ---------------------------------------
def results(ox,oy):
    phone_bg(ox,oy)
    pad=22; x=ox+pad; cx=ox+PW/2
    cy=oy+148; circle(cx,cy,46,fill=T["green_cont"])
    img_d.line([(px(cx-20),px(cy+2)),(px(cx-6),px(cy+16)),(px(cx+22),px(cy-16))],fill=T["green"],width=px(5),joint="curve")
    text(cx,oy+228,"All playlists created",22,T["on_surf"],"b","mm")
    text(cx,oy+258,"12 playlists  •  348 tracks",13.5,T["on_surf_var"],"r","mm")
    y=oy+292; cwd=(PW-2*pad-14)/2
    rrect([x,y,x+cwd,y+72],22,fill=T["green_cont"])
    text(x+20,y+16,"12",28,T["on_green_c"],"b"); text(x+20,y+52,"written",12,T["on_green_c"])
    rrect([x+cwd+14,y,ox+PW-pad,y+72],22,fill=T["container"])
    text(x+cwd+34,y+16,"0",28,T["on_surf"],"b"); text(x+cwd+34,y+52,"failed",12,T["on_surf_var"])
    # next-step tip (text only)
    y=oy+382; rrect([x,y,ox+PW-pad,y+72],22,fill=T["sec_cont"])
    circle(x+32,y+36,18,fill=T["surface"]); text(x+32,y+36,"♪",17,T["orange"],"r","mm")
    text(x+62,y+18,"NEXT STEP",10.5,T["on_sec_cont"],"b")
    text(x+62,y+37,"Eject, then plug into your",12.5,T["on_sec_cont"])
    text(x+62,y+54,"Clip Sport. Enjoy!",12.5,T["on_sec_cont"])
    # eject hand-off button (opens system storage screen)
    y=oy+470; pill(x,ox+PW-pad,y,50,"Eject SD card",T["sec_cont"],T["on_sec_cont"],chev=True)
    text(x,y+62,"Opens Android's storage screen — apps can't eject directly",10,T["on_surf_var"])
    # done / make another
    y=oy+556; pill(x,ox+PW-pad,y,52,"Done",T["btn_fill"],T["btn_text"])
    text(cx,oy+636,"Make another playlist",14,T["primary"],"b","mm")
    edge_scrim(ox,oy); statusbar(ox,oy); gesturebar(ox,oy)

# ------------------------------- render -------------------------------------
screens=[("Home",home),("Preview",preview),("Progress",progress),("Results",results)]

def render_row(top_y, theme, row_title):
    global T, img_d
    T=theme
    img_d.rectangle([px(MARGIN-10),px(top_y),px(W-MARGIN+10),px(top_y+ROWHEAD+PH+LABEL_H)],fill=None)
    text(MARGIN, top_y+4, row_title, 16, T["strip"], "b")
    py=top_y+ROWHEAD
    for i,(label,fn) in enumerate(screens):
        ox=MARGIN+i*(PW+GAP)
        fn(ox,py)
        text(ox+PW/2, py+PH+LABEL_H/2, label, 14, T["strip"], "b", "mm")

# shadows for all 8 phones first
img_d=ImageDraw.Draw(img)
shadow=Image.new("RGBA",(CW,CH),(0,0,0,0)); sdw=ImageDraw.Draw(shadow)
rows_y=[TOPSTRIP+ROWHEAD, TOPSTRIP+ROWHEAD+PH+LABEL_H+GAPROWS+ROWHEAD]
for ry in rows_y:
    for i in range(N):
        ox=MARGIN+i*(PW+GAP)
        sdw.rounded_rectangle([px(ox),px(ry+10),px(ox+PW),px(ry+PH+10)],radius=px(46),fill=(15,18,34,70))
shadow=shadow.filter(ImageFilter.GaussianBlur(px(11)))
img.paste(shadow,(0,0),shadow)
img_d=ImageDraw.Draw(img)

text(W/2, 16, "My Playlist Creator 2026  —  Material 3 Expressive  ·  light + dark  ·  edge-to-edge",
     14, (120,120,132), "r", "mm")
render_row(TOPSTRIP, LIGHT, "LIGHT THEME")
render_row(TOPSTRIP+ROWHEAD+PH+LABEL_H+GAPROWS, DARK, "DARK THEME")

out=img.resize((W,H),Image.LANCZOS)
os.makedirs("/tmp/cliplist-mockups",exist_ok=True)
out.save("/tmp/cliplist-mockups/flow_lightdark.png")
print("saved /tmp/cliplist-mockups/flow_lightdark.png", out.size)

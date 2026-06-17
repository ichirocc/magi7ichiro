#!/usr/bin/env python3
# Web版(墨と朱・暖色ダーク)のトークン忠実モック × ネイティブ版(既存PNG)を横並び比較し、HID/スマホ特化スコア付きでPDF化。
from PIL import Image, ImageDraw, ImageFont
import os
JP = "/usr/share/fonts/truetype/fonts-japanese-gothic.ttf"
S = 2.75
def f(sp): return ImageFont.truetype(JP, int(sp*S))
def dp(x): return int(x*S)
W = dp(360)
# Web 墨と朱 palette
WB = dict(bg="#1e1a15", panel="#2a251d", panel2="#34302a", gold="#d7a13b", teal="#4fa89c",
          green="#6fa56b", text="#ece3d2", sub="#a99f8c", danger="#d8584e", line="#403a31", onGold="#1e1a15")
def rr(d,x,y,w,h,r,fill,outline=None,ow=0): d.rounded_rectangle([x,y,x+w,y+h],radius=int(r*S),fill=fill,outline=outline,width=ow)
def tx(d,x,y,s,sp,col,anchor="la",maxw=None):
    ft=f(sp)
    if maxw and d.textlength(s,font=ft)>maxw:
        while s and d.textlength(s+"…",font=ft)>maxw: s=s[:-1]
        s+="…"
    d.text((x,y),s,font=ft,fill=col,anchor=anchor)
def ctr(d,cx,y,s,sp,col): d.text((cx,y),s,font=f(sp),fill=col,anchor="ma")
def canv(h): im=Image.new("RGB",(W,dp(h)),WB["bg"]); return im,ImageDraw.Draw(im)
def webtop(d,title):
    d.rectangle([0,0,W,dp(50)],fill=WB["panel"]); tx(d,dp(16),dp(15),"墨 MAGI",16,WB["gold"]); tx(d,dp(110),dp(16),title,14,WB["sub"])
    # undo/redo + save badge
    tx(d,W-dp(150),dp(16),"↶ ↷",15,WB["sub"]); rr(d,W-dp(96),dp(13),dp(80),dp(24),12,WB["panel2"]); tx(d,W-dp(86),dp(17),"✓ 保存済",11,WB["teal"])
    d.line([0,dp(50),W,dp(50)],fill=WB["line"],width=1)
def webnav(d,H,sel):
    ny=H-dp(58); d.rectangle([0,ny,W,H],fill=WB["panel"]); d.line([0,ny,W,ny],fill=WB["line"],width=1)
    for i,l in enumerate(["🏠ホーム","📝入力","🗓勤務表","✅チェック","⚙設定"]):
        cx=int(W/5*(i+0.5)); ctr(d,cx,ny+dp(20),l,11,WB["gold"] if i==sel else WB["sub"])

def web_home():
    H=720; im,d=canv(720); webtop(d,"ホーム")
    x,y,w=dp(14),dp(60),W-dp(28)
    tx(d,x,y,"今月の勤務表づくり",18,WB["gold"]); y+=dp(34)
    steps=[("① 対象の月を選ぶ","翌月にする","done"),("② 希望シフトを入力","希望を入力する(84件)","done"),
           ("③ たたき台を作る","たたき台を作る","cur"),("④ 自動で組む","自動で組む","next"),
           ("⑤ 気になる所を手で直す","手で直す","next"),("⑥ もう一度 自動で組む","もう一度 組む","next"),
           ("⑦ 守れないルールを確認","ルールを確認","next")]
    for t,b,stt in steps:
        col=WB["panel2"] if stt=="cur" else WB["panel"]; rr(d,x,y,w,dp(58),12,col,outline=WB["gold"] if stt=="cur" else None,ow=2 if stt=="cur" else 0)
        badge="✓" if stt=="done" else ("●" if stt=="cur" else "○"); tx(d,x+dp(12),y+dp(8),badge,14,WB["teal"] if stt=="done" else WB["gold"])
        tx(d,x+dp(36),y+dp(8),t,12,WB["text"],maxw=w-dp(160))
        rr(d,x+w-dp(132),y+dp(14),dp(120),dp(30),16,WB["gold"] if stt=="cur" else WB["panel2"]); ctr(d,x+w-dp(72),y+dp(20),b,11,WB["onGold"] if stt=="cur" else WB["sub"])
        y+=dp(66)
    webnav(d,H,0); return im

def web_schedule():
    H=720; im,d=canv(720); webtop(d,"勤務表 ⑤編集中")
    x,y,w=dp(14),dp(60),W-dp(28)
    rr(d,x,y,w,dp(34),10,WB["panel"]); tx(d,x+dp(10),y+dp(8),"タップで: ●シフト編集 / 希望編集",12,WB["gold"]); y+=dp(42)
    # per-staff calendar header
    rr(d,x,y,w,dp(30),8,WB["panel2"]); tx(d,x+dp(10),y+dp(6),"‹  古泉 健一  ▾  ›",13,WB["text"]); y+=dp(38)
    # dense calendar grid (5x7 weeks)
    SHc=["#3a3a3a","#4fa89c","#6fa56b","#a96bff","#5fb3d4","#d7a13b"]
    cw=int((w-dp(8))/7); ch=dp(54)
    days=["日","月","火","水","木","金","土"]
    for di,dn in enumerate(days): ctr(d,x+di*cw+cw//2,y,dn,10,WB["sub"])
    y+=dp(16)
    import random; random.seed(3); vio={(1,3),(2,5),(3,1)}
    for wk in range(4):
        for di in range(7):
            cx=x+di*cw; cyy=y+wk*(ch+dp(4)); col=SHc[(wk*3+di)%len(SHc)]
            rr(d,cx,cyy,cw-dp(4),ch,8,col)
            ctr(d,cx+(cw-dp(4))//2,cyy+dp(4),str(wk*7+di+1),9,WB["text"])
            ctr(d,cx+(cw-dp(4))//2,cyy+dp(20),["休","日","遅","夜","早","Aｱ"][(wk*3+di)%6],13,"#101010")
            if (wk,di) in vio: d.rounded_rectangle([cx,cyy,cx+cw-dp(4),cyy+ch],radius=dp(8),outline=WB["danger"],width=dp(3))
    # bottom commit buttons
    by=H-dp(58)-dp(56); rr(d,x,by,int(w/2)-dp(6),dp(48),12,WB["gold"]); ctr(d,x+int(w/2)//2,by+dp(13),"結果に反映",13,WB["onGold"])
    rr(d,x+int(w/2)+dp(6),by,int(w/2)-dp(6),dp(48),12,WB["panel2"]); ctr(d,x+int(w/2)+dp(6)+int(w/2)//2,by+dp(13),"結果から複製",13,WB["text"])
    webnav(d,H,2); return im

def web_running():
    H=720; im,d=canv(720); webtop(d,"自動で組む")
    # busy overlay
    d.rectangle([0,dp(50),W,H-dp(58)],fill="#15120e")
    x,y,w=dp(24),dp(120),W-dp(48)
    rr(d,x,y,w,dp(360),18,WB["panel"])
    ctr(d,x+w//2,y+dp(20),"⊙",30,WB["gold"]); ctr(d,x+w//2,y+dp(70),"RSI++ 学習＋研磨",14,WB["text"])
    ctr(d,x+w//2,y+dp(96),"残り時間 約2分30秒",12,WB["sub"])
    # progressbar
    rr(d,x+dp(24),y+dp(126),w-dp(48),dp(10),6,WB["panel2"]); rr(d,x+dp(24),y+dp(126),int((w-dp(48))*0.55),dp(10),6,WB["teal"])
    ctr(d,x+w//2,y+dp(146),"絶対違反 2件 ・ 品質違反 31",12,WB["danger"])
    rr(d,x+dp(24),y+dp(178),w-dp(48),dp(40),12,WB["panel2"]); ctr(d,x+w//2,y+dp(190),"🔬 途中経過を見る（盤面アニメ）",12,WB["gold"])
    rr(d,x+dp(24),y+dp(226),w-dp(48),dp(40),12,WB["panel2"]); ctr(d,x+w//2,y+dp(238),"🎯 気になるスタッフをタップ（TapGame）",11,WB["sub"])
    rr(d,x+dp(60),y+dp(286),w-dp(120),dp(44),16,WB["danger"]); ctr(d,x+w//2,y+dp(298),"⏹ キャンセル",14,"#fff")
    webnav(d,H,2); return im

def web_check():
    H=720; im,d=canv(720); webtop(d,"チェック 結果")
    x,y,w=dp(14),dp(60),W-dp(28)
    # 3 metric cards
    cw=int((w-dp(16))/3)
    for i,(lab,val,col) in enumerate([("担当不可","0",WB["teal"]),("絶対違反","2",WB["danger"]),("品質違反","31",WB["gold"])]):
        cx=x+i*(cw+dp(8)); rr(d,cx,y,cw,dp(80),12,WB["panel"]); ctr(d,cx+cw//2,y+dp(12),lab,11,WB["sub"]); ctr(d,cx+cw//2,y+dp(34),val,26,col)
    y+=dp(92)
    rr(d,x,y,w,dp(96),12,WB["panel2"]); tx(d,x+dp(12),y+dp(10),"配布審議（三賢人）",13,WB["gold"])
    tx(d,x+dp(12),y+dp(36),"MELCHIOR ◯ / BALTHASAR △ / CASPER ◯",12,WB["text"])
    tx(d,x+dp(12),y+dp(62),"判定：要確認（絶対違反2件）",13,WB["danger"]); y+=dp(108)
    rr(d,x,y,w,dp(44),12,WB["panel"]); tx(d,x+dp(12),y+dp(13),"🎛 複数の最適化案から選ぶ",12,WB["gold"]); y+=dp(54)
    for lab,dest in [("人員不足 (covU) 2件","必要人数へ »"),("希望未充足 (pref) 9件","希望シフトへ »"),("連続違反 (c3) 12件","勤務表で修正 »")]:
        rr(d,x,y,w,dp(40),10,WB["panel"]); tx(d,x+dp(12),y+dp(11),lab,12,WB["text"]); tx(d,x+w-dp(12),y+dp(11),dest,11,WB["teal"],anchor="ra"); y+=dp(48)
    webnav(d,H,3); return im

def web_settings():
    H=720; im,d=canv(720); webtop(d,"設定 詳細")
    x,y,w=dp(14),dp(60),W-dp(28)
    rr(d,x,y,w,dp(150),12,WB["panel"]); tx(d,x+dp(12),y+dp(10),"① 機能・運用",13,WB["gold"]); yy=y+dp(38)
    for lab,on in [("バックグラウンド最適化",True),("完了通知",True),("仕上げ研磨(SOFT)",False)]:
        rr(d,x+dp(12),yy,dp(40),dp(22),11,WB["teal"] if on else WB["panel2"]);
        if on: d.ellipse([x+dp(32),yy+dp(2),x+dp(50),yy+dp(20)],fill="#fff")
        else: d.ellipse([x+dp(14),yy+dp(2),x+dp(32),yy+dp(20)],fill=WB["sub"])
        tx(d,x+dp(64),yy+dp(2),lab,12,WB["text"]); yy+=dp(34)
    y+=dp(162)
    rr(d,x,y,w,dp(120),12,WB["panel"]); tx(d,x+dp(12),y+dp(10),"② 高度パラメータ",13,WB["gold"])
    tx(d,x+dp(12),y+dp(40),"時間予算 600s ・ 並列 8 ・ reheat 0.7",11,WB["sub"])
    tx(d,x+dp(12),y+dp(64),"RSI++ 学習キー: [covU][c3][pref] …",11,WB["sub"]); y+=dp(132)
    btns=[["📥JSON出力","📤JSON取込"],["📄CSV出力","📑CSV取込"],["🛡自己診断","🗑全リセット"]]
    for r0 in btns:
        for c0i,c0 in enumerate(r0):
            cx=x+c0i*(int(w/2)); rr(d,cx,y,int(w/2)-dp(8),dp(40),10,WB["panel2"]); ctr(d,cx+int(w/2)//2,y+dp(11),c0,12,WB["text"])
        y+=dp(48)
    webnav(d,H,4); return im

def web_input():
    H=720; im,d=canv(720); webtop(d,"入力 ②希望シフト")
    x,y,w=dp(14),dp(60),W-dp(28)
    rr(d,x,y,w,dp(34),10,WB["panel"]); tx(d,x+dp(10),y+dp(8),"タップで希望シフトを変更（担当可のみ）",12,WB["gold"]); y+=dp(42)
    rr(d,x,y,w,dp(30),8,WB["panel2"]); tx(d,x+dp(10),y+dp(6),"スタッフ: 古泉 健一 ▾    全スタッフに一括設定 »",12,WB["teal"]); y+=dp(40)
    days=["日","月","火","水","木","金","土"]; cw=int((w-dp(8))/7)
    for di,dn in enumerate(days): ctr(d,x+di*cw+cw//2,y,dn,10,WB["sub"])
    y+=dp(16)
    wish={(0,2):"夜",(1,4):"早",(2,0):"休"}
    for wk in range(4):
        for di in range(7):
            cx=x+di*cw; cyy=y+wk*(dp(46)+dp(4))
            has=(wk,di) in wish
            rr(d,cx,cyy,cw-dp(4),dp(46),8,WB["gold"] if has else WB["panel"])
            ctr(d,cx+(cw-dp(4))//2,cyy+dp(4),str(wk*7+di+1),9,WB["onGold"] if has else WB["sub"])
            if has: ctr(d,cx+(cw-dp(4))//2,cyy+dp(18),wish[(wk,di)],14,WB["onGold"])
    by=H-dp(58)-dp(54); rr(d,x,by,w,dp(46),12,WB["teal"]); ctr(d,x+w//2,by+dp(12),"＋ この希望を追加",14,"#fff")
    webnav(d,H,1); return im

# ---- compose comparison pages ----
NB=dict(bg="#FBF8F2",text="#221F19",sub="#7A736A",gold="#4E6FC2",ok="#2E6A4F",bad="#BA1A1A",band="#F0EAE0")
def F(sp): return ImageFont.truetype(JP,sp)
def page(title, web_im, nat_path, sweb, snat, reflect):
    H=1040
    web=web_im.resize((int(W*H/web_im.height*0.001*1000/1000), 0)) if False else web_im
    # scale both to height H
    def sc(im): return im.resize((int(im.width*H/im.height), H))
    web=sc(web_im); nat=sc(Image.open(nat_path).convert("RGB"))
    gap=70; mtop=170; mside=50
    cw=web.width+nat.width+gap
    PW=cw+mside*2; PH=H+mtop+60
    pg=Image.new("RGB",(PW,PH),NB["bg"]); d=ImageDraw.Draw(pg)
    # banner
    d.rectangle([0,0,PW,mtop-20],fill=NB["band"])
    d.text((mside,24),title,font=F(40),fill=NB["text"])
    # scores
    vw="合格" if sweb>=70 else "未満"; vn="合格" if snat>=70 else "未満"
    d.text((mside,84),f"Web版: {sweb}点（{vw}）   ネイティブ版: {snat}点（{vn}）   →  {'ネイティブ優位' if snat>=sweb else 'Web優位'}",font=F(26),fill=NB["text"])
    d.text((mside,120),f"反映: {reflect}",font=F(24),fill=NB["gold"] if reflect!='—' else NB["sub"])
    # mocks
    xw=mside; xn=mside+web.width+gap
    pg.paste(web,(xw,mtop)); pg.paste(nat,(xn,mtop))
    d.rectangle([xw,mtop,xw+web.width,mtop+H],outline=NB["sub"],width=2)
    d.rectangle([xn,mtop,xn+nat.width,mtop+H],outline=NB["sub"],width=2)
    d.text((xw,mtop+H+12),"Web版（墨と朱・暖色ダーク）",font=F(24),fill=NB["sub"])
    d.text((xn,mtop+H+12),"ネイティブ版（M3・プランナー調）",font=F(24),fill=NB["sub"])
    return pg

D="/home/user/MAGI-ShiftOptimizer/tools"
pages=[
 page("① ホーム（ワークフロー誘導）", web_home(), f"{D}/df_cur_home_green.png", 80,88,"次の一手カード=既にNative優位（反映不要）"),
 page("② 手で直す（手動編集→再最適化）", web_schedule(), f"{D}/df_cur_schedule.png", 84,88,"やり直し(Redo)を新設＝72→88"),
 page("③ 自動で組む（実行中）", web_running(), f"{D}/df_cur_home_running.png", 86,90,"画面消灯防止(Wake Lock相当)を反映。TapGameは不採用"),
 page("④ チェック（結果確認・修正導線）", web_check(), f"{D}/df_cur_analysis.png", 83,86,"修正導線=Native『なおすの手伝い』で同等。三賢人は不採用"),
 page("⑤ 設定（機能・データ）", web_settings(), f"{D}/df_cur_settings.png", 78,86,"折りたたみ詳細設定＋やさしい用語=Native優位（反映不要）"),
 page("⑥ 入力（希望シフト・年次/月次）", web_input(), f"{D}/df_cur_ws1_apt.png", 82,85,"月次/年次分離を反映。tap編集は同等"),
]
out=f"{D}/web_vs_native_compare.pdf"
pages[0].save(out,"PDF",save_all=True,append_images=pages[1:],resolution=120)
for i,p in enumerate(pages): p.save(f"{D}/cmp_{i+1}.png")
print("WROTE",out)

#!/usr/bin/env python3
# 各ネイティブ画面/ポップアップのモック画像 × コード照合チェック結果 を1ページにまとめた検証PDF。
from PIL import Image, ImageDraw, ImageFont
JP="/usr/share/fonts/truetype/fonts-japanese-gothic.ttf"
def F(s): return ImageFont.truetype(JP,s)
BG="#FBF8F2"; INK="#221F19"; SUB="#7A736A"; OK="#2E6A4F"; WARN="#9A6A00"; BAND="#EAE3D6"; GREEN="#D9F0E4"; CARD="#FFFFFF"
D="/home/user/MAGI-ShiftOptimizer/tools"
# (mock png, title, Web score, Native score, [ (item, '○'|'△'|'×') ... ])
PAGES=[
 ("df_cur_home_green","ホーム（配れます）",80,88,[
   ("主操作は大ボタン1つ＋補助（Hick/Fitts）","○"),("専門記号を出さない（c3n/covU等なし）","○"),
   ("状態チップ＝配れます（状態可視化）","○"),("下端固定CTA＝親指ゾーン","○"),
   ("M3コントラスト 本文≥4.5:1（実測）","○"),("文字200%リフロー","△要実機")]),
 ("df_cur_schedule","勤務表（見るだけ/直す）",84,88,[
   ("読取/編集の分離＋読取専用バッジ","○"),("違反=色＋形＋凡例（非色依存 WCAG1.4.1）","○"),
   ("セル≥52dp・tap編集（担当可のみ）","○"),("読み上げ名（日付/シフト/違反/タップ）","○"),
   ("7日/カレンダー/月＝横スクロール回避","○"),("回転/分割で崩れなし","△要実機")]),
 ("df_cur_edit_year","編集 年次マスター（スキルグループ）",0,88,[
   ("月次/年次の分離＋注意帯（誤編集防止 H5）","○"),("スキルグループCRUD＋職員割当（新設）","○"),
   ("『担当可否に影響しない』明示（H10）","○"),("各操作≥48dp","○"),
   ("用語平易（記号/内部コードなし）","○"),("Webに同等機能なし＝Native独自◎","○")]),
 ("df_cur_settings","設定",78,86,[
   ("外観4セグメント＋片手モード","○"),("詳細設定は折りたたみ（漸進的開示 H8）","○"),
   ("データ入出力＝SAFピッカー","○"),("やさしい用語（じっくり度/だいたいN分）","○"),
   ("タッチ48dp","○"),("ライト/ダーク/UD 3テーマ","○")]),
 ("df_cur_analysis","ようす（分析/チェック）",83,86,[
   ("やさしい俯瞰＋内訳（開発指標は隔離）","○"),("配布判定＝配れます/もう少し（平易）","○"),
   ("修正導線（なおすの手伝いへ）","○"),("色チップ＋数値（非色依存）","○"),
   ("三賢人/TapGameは不採用（認知負荷）","○"),("コントラスト実測","○")]),
 ("df_cur_guided_fix","ポップアップ：なおすのを手伝って",0,90,[
   ("不足枠を1つ提示＋候補1タップ","○"),("候補ボタン≥52dp・氏名2行折返し（文字欠け防止）","○"),
   ("閉じるは単一（重複ボタン排除）","○"),("『元に戻す』案内（H3 制御と自由）","○"),
   ("scrim＋安全な閉じる","○"),("Web=同等機能なし＝Native独自◎","○")]),
 ("df_cur_csv_choice","ポップアップ：CSV取込の選択",0,84,[
   ("勤務表/希望の2択を明示","○"),("各ボタン≥46dp・キャンセル安全既定","○"),
   ("用語平易（説明文つき）","○"),("選択式＝誤値防止（H5）","○")]),
 ("df_cur_skill_dialog","ポップアップ：スキルグループ追加",0,84,[
   ("記号/名前のラベル付き入力（A8）","○"),("OKは記号必須で活性（エラー予防）","○"),
   ("キャンセル安全既定（H3）","○"),("タッチ48dp","○")]),
 ("df_cur_c41s_dialog","ポップアップ：スキル別の回数 追加",0,86,[
   ("スキル/シフトを選択式（誤値防止）","○"),("下限/上限のラベル＋既定説明","○"),
   ("追加/キャンセルの48dpボタン","○"),("新ルール cons41s に対応（新設）","○")]),
 ("df_cur_shift_picker","ボトムシート：シフト選択（セルtap編集）",0,90,[
   ("担当可シフトのみタイル提示（誤配置の予防 H5）","○"),("タイル≥56dp・4列・間隔8dp（Fitts/誤タップ防止）","○"),
   ("選択中=primary強調・色＋記号で識別","○"),("親指ゾーンの下端シート＋ドラッグハンドル","○"),
   ("タップ即反映＋触覚（Undo可）","○"),("ModalBottomSheet＝ナビバーインセット既定対応","○")]),
]
def panel_h(items): return 200+len(items)*64
def page(png,title,sw,sn,items):
    mock=Image.open(f"{D}/{png}.png").convert("RGB")
    Hm=1180; mock=mock.resize((int(mock.width*Hm/mock.height),Hm))
    panelW=1000; gap=60; mside=50; mtop=40
    PW=mock.width+gap+panelW+mside*2; PH=Hm+mtop*2
    pg=Image.new("RGB",(PW,PH),BG); d=ImageDraw.Draw(pg)
    pg.paste(mock,(mside,mtop)); d.rectangle([mside,mtop,mside+mock.width,mtop+Hm],outline=SUB,width=2)
    px=mside+mock.width+gap; py=mtop+10
    d.text((px,py),title,font=F(40),fill=INK); py+=64
    vw=f"Web {sw}点" if sw>0 else "Web 該当なし"
    d.text((px,py),f"{vw}   /   ネイティブ {sn}点（{'合格' if sn>=70 else '未満'}）",font=F(28),fill=INK); py+=50
    d.text((px,py),"コード照合チェック（モック画像＋実コード）",font=F(26),fill=SUB); py+=52
    for it,st in items:
        col=OK if st=="○" else (WARN if st.startswith("△") else "#BA1A1A"); mark="✓" if st=="○" else ("△" if st.startswith("△") else "×")
        d.text((px,py),mark,font=F(30),fill=col); d.text((px+50,py+2),it+("" if st=="○" else f"  （{st}）"),font=F(26),fill=INK); py+=64
    return pg
pages=[page(*p) for p in PAGES]
out=f"{D}/native_verification_visual.pdf"
pages[0].save(out,"PDF",save_all=True,append_images=pages[1:],resolution=110)
print("WROTE",out,len(pages),"pages")

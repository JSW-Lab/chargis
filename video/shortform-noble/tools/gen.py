# -*- coding: utf-8 -*-
"""'귀하게 자란 내가' 숏폼의 장면 파일(compositions/*.html)과 index.html을 만든다.
장면마다 같은 캐릭터·말풍선·자막 마크업을 넣으려고 생성기로 둔다. 고친 뒤 `python tools/gen.py`로 다시 만든다."""
import os, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
C = dict(ground="#0f0e0d", stock="#1a1917", ink="#ede9e2", muted="#a19c93", rule="#3b3833",
         stamp="#8fb2ee", warn="#e8ae4a", gold="#c9a44c", carpet="#9e2b2b", paper="#e9dfc6")

CSS = """
@font-face { font-family: "Wanted Sans"; src: url("assets/fonts/wanted_sans_semibold.ttf"); font-weight: 600; }
@font-face { font-family: "Wanted Sans"; src: url("assets/fonts/wanted_sans_bold.ttf"); font-weight: 700; }
@font-face { font-family: "Wanted Sans"; src: url("assets/fonts/wanted_sans_extrabold.ttf"); font-weight: 800; }
@font-face { font-family: "Geist Mono"; src: url("assets/fonts/geist_mono_semibold.ttf"); font-weight: 600; }
#root { position: absolute; inset: 0; background: transparent; font-family: "Wanted Sans"; color: #ede9e2; overflow: hidden; }
.world { position: absolute; inset: 0; }
.abs { position: absolute; }
.noble { position: absolute; width: 220px; height: 440px; }
.noble svg { width: 100%; height: 100%; overflow: visible; }
.bub { position: absolute; width: 190px; height: 170px; }
.bub .shape { position: absolute; inset: 0; }
.bub img { position: absolute; left: 47px; top: 22px; width: 96px; height: 96px; }
.cap { position: absolute; left: 40px; right: 40px; top: 1120px; padding: 14px 0 18px; text-align: center; font-weight: 800; letter-spacing: -1px; background: rgba(15,14,13,.88); z-index: 30; }
.cap .l1 { font-size: 64px; line-height: 1.22; color: #c9a44c; }
.cap .l2 { font-size: 64px; line-height: 1.22; color: #ede9e2; }
.cap .gold { color: #c9a44c; }
.shot { position: absolute; border: 5px solid #c9a44c; background: #000; object-fit: cover; }
.stampbox { position: absolute; border: 8px solid; padding: 10px 26px; font-weight: 800; font-size: 54px; line-height: 1; }
.stampbox::after { content: ""; position: absolute; inset: 6px; border: 3px solid; border-color: inherit; }
.mono { font-family: "Geist Mono"; font-weight: 600; }
"""

def noble(pid, x, y, s=1.0, cup=False, z=5):
    """귀족 운전자. (x, y)는 머리 중심. 얼굴은 외알 안경 + 콧수염만."""
    w, h = 220 * s, 440 * s
    cupsvg = f"""<g id="{pid}-cup" transform="translate(120 190)"><path d="M-40 0 l80 0 q0 46 -40 46 q-40 0 -40 -46z" fill="{C['ground']}" stroke="{C['ink']}" stroke-width="5"/><path d="M40 10 q24 0 20 18 q-4 14 -22 10" fill="none" stroke="{C['ink']}" stroke-width="5"/><path d="M-56 52 l112 0" stroke="{C['gold']}" stroke-width="5"/></g>""" if cup else ""
    return f"""<div id="{pid}" class="noble" style="left:{x - w/2:.0f}px;top:{y - 170*s:.0f}px;width:{w:.0f}px;height:{h:.0f}px;z-index:{z}">
  <svg viewBox="-110 -170 220 440"><g fill="none" stroke="{C['ink']}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
    <path d="M-70 150 q0 -70 70 -86 q70 16 70 86 l0 110 l-140 0z" fill="{C['stock']}"/>
    <path d="M-30 74 l30 18 l30 -18 l0 30 l-30 -12 l-30 12z" fill="{C['carpet']}"/>
    <path d="M0 110 l0 140" stroke="{C['rule']}"/>
    <circle cx="0" cy="0" r="62" fill="{C['stock']}"/>
    <g id="{pid}-mono"><circle cx="24" cy="-8" r="20" stroke="{C['gold']}" stroke-width="6"/><path d="M44 -4 q14 34 2 70" stroke="{C['gold']}" stroke-width="3"/></g>
    <path id="{pid}-mus" d="M0 22 q-18 -14 -38 -2 q-14 10 -26 -6 q6 22 28 20 q20 -2 36 -12 q16 10 36 12 q22 2 28 -20 q-12 16 -26 6 q-20 -12 -38 2z" fill="{C['ink']}" stroke-width="3"/>
    <g id="{pid}-hat"><path d="M-48 -150 l96 0 l-6 104 l-84 0z" fill="#121110"/><path d="M-44 -70 l88 0" stroke="{C['gold']}" stroke-width="9"/><path d="M-92 -44 q92 -22 184 0 q-92 16 -184 0z" fill="#121110"/></g>
  </g>{cupsvg}</svg></div>"""

def bubble(bid, x, y, emojis, s=1.0, z=8):
    """말풍선. emojis는 Twemoji 코드 목록(겹쳐 두고 시간에 맞춰 바꿔 보인다)."""
    imgs = "".join(f'<img id="{bid}-{i}" src="assets/emoji/{e}.svg" style="opacity:{1 if i == 0 else 0}">' for i, e in enumerate(emojis))
    return f"""<div id="{bid}" class="bub" style="left:{x:.0f}px;top:{y:.0f}px;transform-origin:20% 100%;scale:{s};z-index:{z}">
  <svg class="shape" viewBox="0 0 190 170"><path d="M24 4 h142 q20 0 20 20 v92 q0 20 -20 20 h-96 l-44 30 l12 -30 h-14 q-20 0 -20 -20 v-92 q0 -20 20 -20z" fill="{C['ink']}" stroke="{C['gold']}" stroke-width="6"/></svg>{imgs}</div>"""

def cap(cid, l1, l2, l1cls="l1", l2cls="l2"):
    return f'<div id="{cid}" class="cap"><div id="{cid}-1" class="{l1cls}">{l1}</div><div id="{cid}-2" class="{l2cls}">{l2}</div></div>'

def stampbox(sid, text, x, y, color, rot=-6, size=54):
    return f'<div id="{sid}" class="stampbox" style="left:{x}px;top:{y}px;color:{color};border-color:{color};rotate:{rot}deg;font-size:{size}px;z-index:9">{text}</div>'

def car(x, y, s=1.0, i=""):
    return f'<svg id="car{i}" class="abs" style="left:{x-80*s:.0f}px;top:{y-50*s:.0f}px;width:{160*s:.0f}px;height:{100*s:.0f}px" viewBox="-80 -50 160 100"><g fill="{C["stock"]}" stroke="{C["muted"]}" stroke-width="4"><path d="M-70 0 l10 -34 q6 -14 22 -14 l76 0 q16 0 22 14 l10 34 z"/><rect x="-80" y="0" width="160" height="40" rx="10"/><circle cx="-46" cy="42" r="14" fill="{C["ground"]}"/><circle cx="46" cy="42" r="14" fill="{C["ground"]}"/></g></svg>'

def charger(x, y, busy=True, i=""):
    return f'<svg id="chg{i}" class="abs" style="left:{x-40}px;top:{y-125}px;width:110px;height:130px" viewBox="-40 -125 110 130"><rect x="-34" y="-120" width="68" height="120" fill="{C["stock"]}" stroke="{C["ink"]}" stroke-width="4"/><rect x="-22" y="-104" width="44" height="34" fill="{C["warn"] if busy else C["stamp"]}"/><path d="M34 -60 q30 0 30 40" stroke="{C["ink"]}" stroke-width="4" fill="none"/></svg>'

def GOLD_FRAME(fid): return f'<svg id="{fid}" class="abs" style="left:60px;top:180px;width:960px;height:920px" viewBox="0 0 960 920"><rect x="10" y="10" width="940" height="900" fill="none" stroke="{C["gold"]}" stroke-width="18"/><rect x="40" y="40" width="880" height="840" fill="none" stroke="{C["gold"]}" stroke-width="4"/><g fill="{C["ground"]}" stroke="{C["gold"]}" stroke-width="6"><circle cx="10" cy="10" r="22"/><circle cx="950" cy="10" r="22"/><circle cx="10" cy="910" r="22"/><circle cx="950" cy="910" r="22"/></g></svg>'

def scene(sid, dur, body, script, outer=""):
    html = f"""<template>
  <style>{CSS}</style>
  <div id="root" data-composition-id="{sid}" data-width="1080" data-height="1920">
    <div id="{sid}-world" class="world clip" data-start="0" data-duration="{dur}"><div id="{sid}-in" class="world">
{body}
    </div>{outer}</div>
  </div>
  <script>
    const tl = gsap.timeline({{ paused: true }});
{script}
    window.__timelines["{sid}"] = tl;
  </script>
</template>
"""
    (ROOT / "compositions" / f"{sid}.html").write_text(html, encoding="utf-8")

E = dict(scream="1f631", dizzy="1f635", think="1f914", sweat="1f630", boom="1f92f", calm="1f60c", cool="1f60e", flat="1f610", smirk="1f60f", watch="231a", love="1f970")
POP = 'tl.fromTo("{b}", {{scale: 0}}, {{scale: 1, duration: 0.32, ease: "back.out(2.6)"}}, {t});'
SWAP = 'tl.to("{a}", {{opacity: 0, duration: 0.06}}, {t}); tl.fromTo("{b}", {{opacity: 0, scale: 0.6}}, {{opacity: 1, scale: 1, duration: 0.25, ease: "back.out(3)"}}, {t});'
BLUR_OUT = 'tl.to("#{s}-in", {{opacity: 0, duration: 0.14, ease: "power2.in"}}, {t});'

# ---------------- S1 hook 0-3 s: 역줌 리빌 ----------------
body = f"""
<svg id="carpet" class="abs" style="left:0;top:0;width:1080px;height:1920px" viewBox="0 0 1080 1920"><path d="M300 1100 L470 600 L610 600 L780 1100z" fill="{C['carpet']}"/></svg>
<div id="h1-label" class="abs" style="left:0;right:0;top:300px;text-align:center;font-size:44px;font-weight:700;color:{C['warn']}">충전기 6/6 사용 중</div>
{''.join(charger(170+i*148, 545, True, f'h{i}') for i in range(6))}
{car(540, 700, .75, 'h1')}{car(540, 790, .85, 'h2')}{car(540, 890, .95, 'h3')}
{noble('h1n', 540, 840, .9, cup=True)}
<div id="h1-ticket" class="abs" style="left:70px;top:640px;width:230px;height:130px;background:{C['ink']};color:{C['ground']};text-align:center;rotate:-8deg;z-index:7">
  <div style="font-size:28px;font-weight:700;margin-top:14px">대기 번호표</div><div class="mono" style="font-size:52px;color:{C['carpet']}">31분</div></div>
{bubble('h1b', 650, 560, [E['scream']], .9)}
"""
HOOK_CAP = cap('h1c', '귀하게 자란 내가…', '충전 대기 31분을 해도 될까?')
script = f"""
    // 0프레임은 찻잔 든 귀족 얼굴 클로즈업(썸네일). 0.5 s부터 확 빠지며 대기 줄이 드러난다.
    tl.fromTo("#hook-in", {{scale: 2.7, transformOrigin: "540px 760px"}}, {{scale: 1, duration: 0.9, ease: "expo.inOut"}}, 0.45);
    // 썸네일(0프레임)부터 밈 문장 두 줄이 다 보인다.
    // 번호표가 위에서 떨어져 통 튄다.
    tl.fromTo("#h1-ticket", {{y: -900}}, {{y: 0, duration: 0.55, ease: "bounce.out"}}, 1.35);
    {POP.format(b="#h1b", t=1.75)}
    // 놀라서 실크해트가 튀어 오른다.
    tl.fromTo("#h1n-hat", {{y: 0}}, {{y: -60, duration: 0.16, ease: "power2.out", yoyo: true, repeat: 1}}, 1.75);
    tl.fromTo("#h1n-cup", {{rotate: 0, transformOrigin: "50% 50%"}}, {{rotate: -25, duration: 0.12, yoyo: true, repeat: 3}}, 1.75);
    {BLUR_OUT.format(s="hook", t=2.86)}
    tl.to("#h1c", {{opacity: 0, duration: 0.14}}, 2.86);"""
scene("hook", 3, body, script, HOOK_CAP)

# ---------------- S2 cutoff 3-7.5 s: 차량 배터리 + 도장 쾅 + 플러그 튕김 + 기절 ----------------
cells = "".join(f'<path d="M{24 + i*45.2:.1f} 34 l0 162" stroke="{C["ink"]}" stroke-width="3" opacity=".55"/>' for i in range(1, 10))
body = f"""
<div id="bat" class="abs" style="left:70px;top:250px;width:520px;height:230px;z-index:4">
  <svg class="abs" style="left:0;top:0;width:520px;height:230px;overflow:visible" viewBox="0 0 520 230">
    <rect x="10" y="20" width="480" height="190" rx="18" fill="{C['stock']}" stroke="{C['ink']}" stroke-width="7"/>
    <rect x="492" y="80" width="22" height="70" rx="4" fill="{C['ink']}"/>
    <rect id="bfill" x="24" y="34" width="172" height="162" rx="8" fill="{C['stamp']}" opacity=".85"/>
    {cells}
    <g id="crack" stroke="{C['ink']}" stroke-width="5" fill="none" opacity="0"><path d="M150 26 l24 50 l-30 40 l28 56"/><path d="M330 30 l-20 44 l34 36 l-14 60"/></g>
  </svg>
  <div id="c2-pct" class="abs mono" style="left:0;width:500px;top:58px;text-align:center;font-size:110px;line-height:1">38%</div>
</div>
<svg id="lead" class="abs" style="left:0;top:0;width:1080px;height:1100px;z-index:2" viewBox="0 0 1080 1100"><g stroke="{C['stamp']}" stroke-width="4" stroke-dasharray="14 12" fill="none"><path d="M90 480 L240 752"/><path d="M570 480 L420 752"/></g></svg>
<svg id="ev" class="abs" style="left:80px;top:600px;width:480px;height:230px;overflow:visible;z-index:3" viewBox="0 0 480 230">
  <path id="cable" d="M-90 250 Q-40 150 20 124" stroke="{C['ink']}" stroke-width="8" fill="none"/>
  <g id="plug"><rect x="6" y="110" width="30" height="26" rx="5" fill="{C['ink']}"/></g>
  <path d="M24 168 L28 128 Q34 108 70 102 L150 92 L200 52 Q216 40 246 40 L330 40 Q356 42 378 66 L404 94 Q452 100 462 124 L466 168 Z" fill="{C['stock']}" stroke="{C['ink']}" stroke-width="6" stroke-linejoin="round"/>
  <path d="M206 60 Q218 50 244 50 L290 50 L290 92 L172 94 Z" fill="{C['ground']}" stroke="{C['muted']}" stroke-width="4"/>
  <path d="M300 50 L328 50 Q350 52 368 72 L384 92 L300 92 Z" fill="{C['ground']}" stroke="{C['muted']}" stroke-width="4"/>
  <rect id="pack-glow" x="150" y="142" width="200" height="40" rx="10" fill="none" stroke="{C['stamp']}" stroke-width="6"/>
  <rect id="pack" x="160" y="150" width="180" height="24" rx="6" fill="{C['stamp']}" stroke="{C['ink']}" stroke-width="3"/>
  <g fill="{C['ground']}" stroke="{C['ink']}" stroke-width="6"><circle cx="118" cy="170" r="38"/><circle cx="380" cy="170" r="38"/></g>
  <g fill="none" stroke="{C['muted']}" stroke-width="4"><circle cx="118" cy="170" r="16"/><circle cx="380" cy="170" r="16"/></g>
</svg>
<div id="c2-cut" class="abs" style="left:30px;top:880px;font-size:46px;font-weight:800;color:{C['warn']};rotate:-6deg;opacity:0;z-index:6">충전 끊김!</div>
{stampbox('c2-stamp', '40분 제한', 300, 196, C['warn'], -10, 60)}
<svg id="sofa" class="abs" style="left:560px;top:760px;width:480px;height:300px" viewBox="0 0 480 300"><path d="M0 160 q0 -120 120 -120 l300 0 q60 0 60 60 l0 140 l-480 0z" fill="{C['carpet']}" stroke="{C['ink']}" stroke-width="5"/><rect x="20" y="240" width="440" height="40" fill="#6e1f1f" stroke="{C['ink']}" stroke-width="5"/></svg>
{noble('c2n', 790, 600, .85)}
{bubble('c2b', 850, 420, [E['dizzy']], .9)}
{cap('c2c', '귀하게 자란 내가…', '충전이 끊겨도 될까?')}"""
script = f"""
    tl.fromTo("#cutoff-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 차 바닥 배터리 팩이 숨 쉬듯 빛나고, 확대한 배터리 칸이 38 → 46%로 찬다.
    tl.fromTo("#pack-glow", {{opacity: 0.2}}, {{opacity: 1, duration: 0.35, yoyo: true, repeat: 3, ease: "sine.inOut"}}, 0);
    tl.fromTo("#bat", {{scale: 0.6, opacity: 0, transformOrigin: "50% 100%"}}, {{scale: 1, opacity: 1, duration: 0.35, ease: "back.out(2)"}}, 0.05);
    tl.fromTo("#lead", {{opacity: 0}}, {{opacity: 1, duration: 0.2}}, 0.1);
    tl.fromTo("#bfill", {{attr: {{width: 172}}}}, {{attr: {{width: 208}}, duration: 1.4, ease: "none"}}, 0.1);
    const pct = {{v: 38}};
    tl.to(pct, {{v: 46, duration: 1.4, ease: "none", onUpdate: () => {{ document.querySelector("#c2-pct").textContent = Math.round(pct.v) + "%"; }}}}, 0.1);
    // 1.5 s 도장 쾅 + 화면 흔들림 + 플러그 튕김 + 배터리에 금
    tl.fromTo("#c2-stamp", {{scale: 2.6, opacity: 0}}, {{scale: 1, opacity: 1, duration: 0.18, ease: "power4.in"}}, 1.5);
    tl.to("#cutoff-in", {{keyframes: [{{x: -18, y: 8}}, {{x: 16, y: -10}}, {{x: -10, y: 6}}, {{x: 6, y: -3}}, {{x: 0, y: 0}}], duration: 0.3, ease: "none"}}, 1.68);
    tl.to("#plug", {{x: -120, y: 70, rotate: -60, transformOrigin: "50% 50%", duration: 0.35, ease: "power3.out"}}, 1.68);
    tl.to("#cable", {{attr: {{d: "M-90 250 Q-80 220 -100 186"}}, duration: 0.35, ease: "power3.out"}}, 1.68);
    tl.to("#crack", {{opacity: 1, duration: 0.05}}, 1.7);
    tl.to(["#bfill", "#pack"], {{fill: "{C['warn']}", duration: 0.1}}, 1.7);
    tl.to("#pack-glow", {{stroke: "{C['warn']}", opacity: 1, duration: 0.1}}, 1.7);
    tl.to("#c2-pct", {{color: "{C['warn']}", duration: 0.1}}, 1.7);
    tl.fromTo("#c2-cut", {{opacity: 0, scale: 0.4}}, {{opacity: 1, scale: 1, duration: 0.25, ease: "back.out(3)"}}, 1.8);
    // 2.1 s 귀족 기절: 뒤로 천천히 쓰러지고 실크해트가 붕 떴다가 떨어진다.
    tl.fromTo("#c2n", {{rotate: 0, transformOrigin: "50% 90%"}}, {{rotate: -72, x: 40, y: 80, duration: 0.9, ease: "power2.in"}}, 2.1);
    tl.fromTo("#c2n-hat", {{y: 0, rotate: 0}}, {{keyframes: [{{y: -220, rotate: -40, duration: 0.45, ease: "power2.out"}}, {{y: 30, rotate: -100, duration: 0.5, ease: "bounce.out"}}]}}, 2.2);
    {POP.format(b="#c2b", t=3.1)}
    tl.fromTo("#c2c-1", {{x: 0}}, {{keyframes: [{{x: -8}}, {{x: 8}}, {{x: -6}}, {{x: 0}}], duration: 0.3}}, 1.7);
    tl.fromTo("#c2c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.3);
    {BLUR_OUT.format(s="cutoff", t=4.36)}"""
scene("cutoff", 4.5, body, script)

# ---------------- S3 calc 7.5-11.5 s: 펭귄 밈 구도. 가운데 아기 펭귄 자리 귀족을 계산기·앱이 쏘아붙인다 ----------------
def device(did, cx, cy, rot, label, calc=False):
    if calc:
        keys = "".join(f'<div style="position:absolute;left:{22 + (i % 3) * 52}px;top:{120 + (i // 3) * 46}px;width:40px;height:34px;border-radius:6px;background:{C["rule"]}"></div>' for i in range(12))
        inner = f'<div style="position:absolute;left:18px;top:20px;width:154px;height:80px;background:#26302a;border:3px solid {C["muted"]};font-family:Geist Mono;font-size:40px;color:#9fd0a8;text-align:right;padding:16px 10px 0 0">%?</div>{keys}'
    else:
        inner = f'<div style="position:absolute;left:0;right:0;top:24px;text-align:center;font-size:42px;font-weight:800;color:{C["ink"]}">{label}</div>' \
                f'<div style="position:absolute;left:22px;right:22px;top:96px;height:120px;border:3px solid {C["muted"]};border-radius:10px"></div>' \
                f'<div style="position:absolute;left:22px;right:22px;top:236px;height:16px;border-radius:8px;background:{C["rule"]}"></div><div style="position:absolute;left:22px;width:90px;top:266px;height:16px;border-radius:8px;background:{C["rule"]}"></div>'
    return f'<div id="{did}" class="abs" style="left:{cx-95}px;top:{cy-160}px;width:190px;height:320px;rotate:{rot}deg;border:6px solid {C["ink"]};border-radius:24px;background:{C["stock"]};z-index:4">{inner}</div>'

DEV = [("dv0", 135, 650, 34, "계산기", True, (120, 50)), ("dv1", 330, 285, 18, "앱 A", False, (70, 120)),
       ("dv2", 750, 285, -18, "앱 B", False, (-70, 120)), ("dv3", 945, 650, -34, "앱 C", False, (-120, 50))]
JABS = [(0.30, 0), (0.70, 2), (1.05, 1), (1.35, 3), (1.62, 0), (1.86, 2), (2.08, 1), (2.28, 3)]
WORDS = [(540, 410, "50%?", C['gold']), (750, 530, "대기?", C['warn']), (330, 530, "몇 km?", C['ink']), (790, 790, "80%?", C['gold']),
         (300, 1010, "제한?", C['warn']), (540, 200, "100%?", C['gold']), (300, 790, "몇 분?", C['ink']), (790, 1010, "어디서?", C['warn'])]
words = "".join(f'<div id="w{i}" class="abs mono" style="left:{x-90}px;top:{y-40}px;width:180px;text-align:center;white-space:nowrap;font-size:52px;color:{c};z-index:7;opacity:0">{t}</div>' for i, (x, y, t, c) in enumerate(WORDS))
body = f"""
<svg class="abs" style="left:0;top:0;width:1080px;height:1100px" viewBox="0 0 1080 1100"><ellipse cx="540" cy="1010" rx="420" ry="60" fill="{C['stock']}"/></svg>
{''.join(device(d, x, y, r, l, k) for d, x, y, r, l, k, _ in DEV)}
<div id="chick" class="abs" style="left:0;top:0;width:1080px;height:1100px">{noble('c3n', 540, 720, 1.0)}</div>
{words}
{cap('c3c', '귀하게 자란 내가…', '이걸 직접 계산해야 될까?')}"""
jab_js = ""
for t, k in JABS:
    d, *_, (dx, dy) = DEV[k]
    jab_js += f'tl.to("#{d}", {{x: {dx}, y: {dy}, duration: 0.09, ease: "power3.in", yoyo: true, repeat: 1}}, {t});\n'
    jab_js += f'tl.to("#chick", {{x: {-dx*0.12:.0f}, y: {abs(dy)*0.06:.0f}, duration: 0.07, yoyo: true, repeat: 1}}, {t + 0.08:.2f});\n'
    jab_js += f'tl.fromTo("#c3n-hat", {{y: 0}}, {{y: -26, duration: 0.07, yoyo: true, repeat: 1}}, {t + 0.08:.2f});\n'
for i, (t, _) in enumerate(JABS):
    jab_js += f'tl.fromTo("#w{i}", {{opacity: 0, scale: 0}}, {{opacity: 1, scale: 1, duration: 0.2, ease: "back.out(3)"}}, {t + 0.08:.2f});\n'
script = f"""
    tl.fromTo("#calc-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 아기 펭귄처럼 웅크린 귀족(살짝 눌림). 표정·말풍선 없음.
    tl.fromTo("#chick", {{scaleY: 0.92, transformOrigin: "540px 1000px"}}, {{scaleY: 0.92, duration: 0.01}}, 0);
    tl.fromTo([{', '.join(f'"#{d}"' for d, *_ in DEV)}], {{opacity: 0, scale: 0.7}}, {{opacity: 1, scale: 1, duration: 0.25, stagger: 0.05, ease: "back.out(2)"}}, 0.02);
    // 계산기·앱이 번갈아 고개를 들이밀며 쏘아붙인다(점점 빨라짐).
    {jab_js}
    // 2.55 s 넷이 한꺼번에 몰려들고, 실크해트가 튀어 오른다.
    {''.join(f'tl.to("#{d}", {{x: {dx*1.3:.0f}, y: {dy*1.3:.0f}, duration: 0.12, ease: "power4.in", yoyo: true, repeat: 1}}, 2.55);' for d, *_, (dx, dy) in DEV)}
    tl.to("#chick", {{keyframes: [{{x: -12}}, {{x: 12}}, {{x: -8}}, {{x: 0}}], duration: 0.3}}, 2.62);
    tl.fromTo("#c3n-hat", {{y: 0, rotate: 0}}, {{y: -150, rotate: 25, duration: 0.2, ease: "power2.out", yoyo: true, repeat: 1}}, 2.62);
    // 질문 글자가 바깥으로 흩어진다.
    {''.join(f'tl.to("#w{i}", {{x: {(x-540)*0.9:.0f}, y: {(y-640)*1.4:.0f}, opacity: 0, rotate: {(-1)**i*35}, duration: 0.45, ease: "power3.in"}}, 2.95);' for i, (x, y, _, _) in enumerate(WORDS))}
    tl.fromTo("#c3c-1", {{scale: 1}}, {{keyframes: [{{scale: 1.15, rotate: -3}}, {{scale: 0.95, rotate: 3}}, {{scale: 1, rotate: 0}}], duration: 0.35}}, 2.6);
    tl.fromTo("#c3c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.3);
    {BLUR_OUT.format(s="calc", t=3.86)}"""
scene("calc", 4, body, script)

# ---------------- S4 reveal 11.5-14 s: 정지 화면 + 스포트라이트 반전 ----------------
body = f"""
<div id="r4-no" class="abs" style="left:0;right:0;top:470px;text-align:center;font-size:260px;font-weight:800;z-index:9">안 돼.</div>
<svg id="spot" class="abs" style="left:0;top:0;width:1080px;height:1100px;opacity:0" viewBox="0 0 1080 1100"><path d="M470 0 L610 0 L900 1000 L180 1000z" fill="{C['gold']}" opacity=".22"/></svg>
{GOLD_FRAME('r4-frame')}
<svg id="bell" class="abs" style="left:820px;top:240px;width:140px;height:160px" viewBox="-70 -40 140 160"><path d="M0 0 q-46 0 -46 72 l92 0 q0 -72 -46 -72z" fill="{C['gold']}"/><circle cx="0" cy="84" r="12" fill="{C['gold']}"/><path d="M0 0 l0 -30" stroke="{C['gold']}" stroke-width="6"/></svg>
<svg id="tray" class="abs" style="left:230px;top:860px;width:620px;height:80px" viewBox="0 0 620 80"><path d="M10 20 q300 -40 600 0 l0 30 q-300 40 -600 0z" fill="#8e8a80" stroke="{C['ink']}" stroke-width="5"/></svg>
<div id="r4-ticket" class="abs" style="left:300px;top:640px;width:480px;height:210px;background:{C['stock']};border:4px solid {C['ink']};text-align:center;z-index:4">
  <div style="font-size:80px;font-weight:800;margin-top:22px">Chargis</div><div style="font-size:36px;font-weight:700;color:{C['stamp']}">고속도로 충전 통행권</div></div>
<svg id="cloche" class="abs" style="left:250px;top:560px;width:580px;height:320px;z-index:5" viewBox="-290 -300 580 320"><path d="M-270 0 q270 -320 540 0z" fill="#b9b4aa" stroke="{C['ink']}" stroke-width="5"/><circle cx="0" cy="-180" r="24" fill="{C['ink']}"/></svg>
<svg id="sparks" class="abs" style="left:240px;top:520px;width:600px;height:400px;opacity:0;z-index:6" viewBox="0 0 600 400"><g fill="{C['gold']}">{''.join(f'<path d="M{x} {y} l8 22 l22 8 l-22 8 l-8 22 l-8 -22 l-22 -8 l22 -8z"/>' for x, y in [(60, 60), (540, 80), (30, 300), (570, 290), (300, 10)])}</g></svg>
{cap('r4c', '', '<span class="gold">귀한 사람은</span><br>차가 먼저 알려 준다.', 'l1', 'l2')}"""
script = f"""
    // 0~0.45 s: 앞 장면이 멈춘 듯한 흑백 정지 + "안 돼." 쾅
    tl.fromTo("#r4-no", {{scale: 3, opacity: 0}}, {{scale: 1, opacity: 1, duration: 0.16, ease: "power4.in"}}, 0);
    tl.to("#r4-no", {{opacity: 0, scale: 0.9, duration: 0.15}}, 0.55);
    tl.fromTo("#r4-frame", {{opacity: 0, rotate: -9, y: -60}}, {{opacity: 1, rotate: 0, y: 0, duration: 0.5, ease: "back.out(2)"}}, 0.6);
    tl.fromTo(["#tray", "#cloche"], {{opacity: 0}}, {{opacity: 1, duration: 0.15}}, 0.6);
    // 집사 벨 딸랑 → 금빛 스포트라이트
    tl.fromTo("#bell", {{opacity: 0, rotate: 0, transformOrigin: "50% 0%"}}, {{opacity: 1, keyframes: [{{rotate: 22}}, {{rotate: -18}}, {{rotate: 10}}, {{rotate: 0}}], duration: 0.5}}, 0.65);
    tl.to("#spot", {{opacity: 1, duration: 0.3, ease: "power2.out"}}, 0.8);
    // 은쟁반 뚜껑이 휙 열리고 통행권이 떠오르며 반짝
    tl.to("#cloche", {{y: -380, x: 260, rotate: 35, opacity: 0, duration: 0.5, ease: "power3.in"}}, 1.15);
    tl.fromTo("#r4-ticket", {{y: 60, scale: 0.9}}, {{y: -20, scale: 1.05, duration: 0.5, ease: "back.out(2)"}}, 1.3);
    tl.fromTo("#sparks", {{opacity: 0, scale: 0.6}}, {{opacity: 1, scale: 1.1, duration: 0.4, ease: "power2.out"}}, 1.4);
    tl.fromTo("#r4c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.45);
    {BLUR_OUT.format(s="reveal", t=2.36)}"""
scene("reveal", 2.5, body, script)

# ---------------- S5 recommend 14-19.5 s: 두루마리 + 카운트업 ----------------
body = f"""
<div id="scroll-top" class="abs" style="left:70px;top:200px;width:940px;height:44px;border-radius:22px;background:#c8b27a;z-index:3"></div>
<div id="paper" class="abs" style="left:90px;top:222px;width:900px;height:660px;background:{C['paper']}"><img class="shot" src="assets/screens/03-recommend.png" style="left:25px;top:30px;width:850px;height:603px"></div>
<div id="scroll-bot" class="abs" style="left:70px;top:860px;width:940px;height:44px;border-radius:22px;background:#c8b27a;z-index:3"></div>
<div id="r5-pct" class="abs mono" style="left:110px;top:900px;font-size:150px;color:{C['gold']}">0%</div>
<div class="abs" style="left:120px;top:1060px;font-size:34px;font-weight:700;color:{C['muted']}">목표 충전 · B 휴게소</div>
{noble('r5n', 860, 990, .62)}
{bubble('r5b', 900, 790, [E['calm']], .75)}
{cap('r5c', '', 'B 휴게소,<br><span class="gold">50%까지면 충분하옵니다</span>')}"""
script = f"""
    tl.fromTo("#recommend-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 칙서처럼 위에서 아래로 촤르륵 펼친다.
    tl.fromTo("#scroll-bot", {{y: -640}}, {{y: 0, duration: 0.8, ease: "power3.out"}}, 0.1);
    tl.fromTo("#paper", {{clipPath: "inset(0 0 100% 0)"}}, {{clipPath: "inset(0 0 0% 0)", duration: 0.8, ease: "power3.out"}}, 0.1);
    const p5 = {{v: 0}};
    tl.to(p5, {{v: 50, duration: 1.2, ease: "power2.out", onUpdate: () => {{ document.querySelector("#r5-pct").textContent = Math.round(p5.v) + "%"; }}}}, 1.0);
    tl.fromTo("#r5-pct", {{scale: 1}}, {{scale: 1.12, duration: 0.15, yoyo: true, repeat: 1, transformOrigin: "0% 50%"}}, 2.2);
    {POP.format(b="#r5b", t=2.4)}
    // 손가락 까딱 대신 고개 끄덕 두 번
    tl.fromTo("#r5n", {{rotate: 0, transformOrigin: "50% 60%"}}, {{keyframes: [{{rotate: 6}}, {{rotate: 0}}, {{rotate: 6}}, {{rotate: 0}}], duration: 0.8}}, 2.6);
    tl.fromTo("#r5c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.6);
    {BLUR_OUT.format(s="recommend", t=5.36)}"""
scene("recommend", 5.5, body, script)

# ---------------- S6 gleo 19.5-23.5 s: 음성 파형 + 도장 낙하 ----------------
bars = "".join(f'<div id="wv{i}" class="abs" style="left:{540 + i*58}px;top:300px;width:22px;height:220px;border-radius:11px;background:{C["stamp"]}"></div>' for i in range(8))
body = f"""
<img class="shot" src="assets/screens/04-navigating.png" style="left:130px;top:620px;width:820px;height:440px;opacity:.9">
{noble('g6n', 290, 420, 1.0, cup=True)}
{bars}
<div class="abs" style="left:880px;top:540px;font-size:40px;font-weight:700;color:{C['stamp']}">Gleo</div>
{bubble('g6b', 360, 170, [E['cool']], .85)}
{stampbox('g6-stamp', '경유지 등록', 600, 820, C['stamp'], 0, 64)}
<svg id="ink" class="abs" style="left:560px;top:760px;width:460px;height:260px;opacity:0;z-index:8" viewBox="0 0 460 260"><g fill="{C['stamp']}">{''.join(f'<circle cx="{x}" cy="{y}" r="{r}"/>' for x, y, r in [(20, 30, 9), (440, 40, 7), (30, 230, 8), (430, 220, 10), (230, 10, 6)])}</g></svg>
<div id="g6c" class="cap"><div id="g6c-2" class="l2" style="font-size:84px">{''.join(f'<span class="ch" style="display:inline-block">{ch if ch != " " else "&nbsp;"}</span>' for ch in '“거기로 가자.”')}</div></div>"""
script = f"""
    tl.fromTo("#gleo-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 말소리에 맞춰 글자가 하나씩 튀어나온다.
    tl.fromTo("#g6c-2 .ch", {{opacity: 0, y: 40, scale: 0.5}}, {{opacity: 1, y: 0, scale: 1, duration: 0.22, ease: "back.out(3)", stagger: 0.07}}, 0.2);
    // 음성 물결이 출렁인다.
    {''.join(f'tl.fromTo("#wv{i}", {{scaleY: 0.15}}, {{keyframes: [{{scaleY: {a}}}, {{scaleY: {b}}}, {{scaleY: {c}}}, {{scaleY: 0.15}}], duration: 1.3, ease: "none"}}, {0.2 + i*0.04:.2f});' for i, (a, b, c) in enumerate([(.5, .9, .3), (.8, .4, .9), (1, .6, .5), (.6, 1, .7), (.9, .5, 1), (.4, .8, .6), (.7, .3, .8), (.3, .6, .4)]))}
    {POP.format(b="#g6b", t=0.4)}
    // 찻잔 홀짝
    tl.fromTo("#g6n-cup", {{rotate: 0, transformOrigin: "50% 50%"}}, {{rotate: -35, y: -20, duration: 0.3, yoyo: true, repeat: 1}}, 1.0);
    // 1.7 s 도장이 회전하며 떨어져 쾅, 잉크가 튄다.
    tl.fromTo("#g6-stamp", {{y: -700, rotate: -60, scale: 1.6, opacity: 0}}, {{y: 0, rotate: -7, scale: 1, opacity: 1, duration: 0.42, ease: "power4.in"}}, 1.7);
    tl.fromTo("#ink", {{opacity: 0, scale: 0.6}}, {{opacity: 1, scale: 1.1, duration: 0.2, ease: "power2.out"}}, 2.12);
    tl.to("#gleo-in", {{keyframes: [{{y: 8}}, {{y: -6}}, {{y: 0}}], duration: 0.18}}, 2.12);
    {BLUR_OUT.format(s="gleo", t=3.86)}"""
scene("gleo", 4, body, script)

# ---------------- S7 congested 23.5-28 s: 분할 슬라이드 + 레드카펫 ----------------
body = f"""
<img class="shot" src="assets/screens/10-congested.png" style="left:110px;top:190px;width:860px;height:440px">
<div id="leftp" class="abs" style="left:0;top:650px;width:540px;height:470px">
  <div class="abs" style="left:0;right:0;top:0;text-align:center;font-size:50px;font-weight:800">B 휴게소</div>
  <div class="abs" style="left:0;right:0;top:62px;text-align:center;font-size:38px;font-weight:700;color:{C['warn']}">대기 31분</div>
  {charger(270, 250, True, 'b')}
  {''.join(car(270, 290 + i*55, .6, f'b{i}') for i in range(3))}
</div>
<div id="rightp" class="abs" style="left:540px;top:650px;width:540px;height:470px">
  <div class="abs" style="left:0;right:0;top:0;text-align:center;font-size:50px;font-weight:800">C 휴게소</div>
  <div class="abs" style="left:0;right:0;top:62px;text-align:center;font-size:38px;font-weight:700;color:{C['stamp']}">대기 8분</div>
  {charger(270, 250, False, 'c')}
  <div id="carpet7" class="abs" style="left:205px;top:260px;width:140px;height:200px;background:{C['carpet']}"></div>
</div>
<div class="abs" style="left:538px;top:660px;width:4px;height:440px;background:{C['rule']}"></div>
{noble('k7n', 540, 980, .5, z=7)}
<svg id="fan" class="abs" style="left:590px;top:900px;width:150px;height:150px;z-index:8" viewBox="0 0 150 150"><path d="M10 140 L140 60 A150 150 0 0 0 90 10z" fill="{C['gold']}" stroke="{C['ink']}" stroke-width="4"/></svg>
{bubble('k7b', 330, 790, [E['flat'], E['smirk']], .7)}
{cap('k7c', '', '줄은 C 휴게소가<br><span class="gold">21분 짧사옵니다</span>')}"""
script = f"""
    // 좌우 화면이 반대 방향에서 밀려 들어온다.
    tl.fromTo("#leftp", {{x: -560}}, {{x: 0, duration: 0.4, ease: "power3.out"}}, 0.05);
    tl.fromTo("#rightp", {{x: 560}}, {{x: 0, duration: 0.4, ease: "power3.out"}}, 0.05);
    {''.join(f'tl.fromTo("#carb{i}", {{y: -300, opacity: 0}}, {{y: 0, opacity: 1, duration: 0.3, ease: "bounce.out"}}, {0.5 + i*0.18:.2f});' for i in range(3))}
    {POP.format(b="#k7b", t=0.9)}
    // 부채 촥 → 😏, C 쪽 레드카펫이 굴러가며 깔린다.
    tl.fromTo("#fan", {{rotate: -70, scale: 0.4, transformOrigin: "7% 93%"}}, {{rotate: 0, scale: 1, duration: 0.25, ease: "back.out(3)"}}, 2.0);
    {SWAP.format(a="#k7b-0", b="#k7b-1", t=2.1)}
    tl.fromTo("#carpet7", {{scaleY: 0, transformOrigin: "50% 0%"}}, {{scaleY: 1, duration: 0.45, ease: "power3.out"}}, 2.2);
    tl.fromTo("#k7c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.5);
    {BLUR_OUT.format(s="congested", t=4.36)}"""
scene("congested", 4.5, body, script)

# ---------------- S8 charging 28-33 s: 돋보기 줌 + 회중시계 ----------------
body = f"""
{GOLD_FRAME('w8-frame')}
<img class="shot" src="assets/screens/06-charging.png" style="left:130px;top:250px;width:820px;height:582px;border:none">
<div id="lens" class="abs" style="left:0;top:0;width:300px;height:300px;border-radius:50%;border:12px solid {C['gold']};overflow:hidden;z-index:6;background:#000">
  <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center"><div style="font-size:34px;font-weight:700;color:{C['muted']}">출발 예정</div><div class="mono" style="font-size:78px;color:{C['gold']}">10:14</div></div></div>
<div id="lens-h" class="abs" style="left:0;top:0;width:22px;height:120px;background:{C['gold']};rotate:-45deg;z-index:6"></div>
{noble('w8n', 250, 930, .5, z=7)}
<svg id="watch" class="abs" style="left:350px;top:930px;width:120px;height:120px;z-index:8" viewBox="-60 -60 120 120"><circle r="52" fill="{C['gold']}" stroke="{C['ink']}" stroke-width="5"/><circle r="40" fill="{C['ink']}"/>{''.join(f'<path d="M0 -36 l0 7" stroke="{C["ground"]}" stroke-width="3" transform="rotate({i*30})"/>' for i in range(12))}<path id="hand-h" d="M0 4 L0 -20" stroke="{C['ground']}" stroke-width="7" stroke-linecap="round"/><path id="hand" d="M0 5 L0 -31" stroke="{C['carpet']}" stroke-width="4" stroke-linecap="round"/><circle r="5" fill="{C['ground']}"/></svg>
{bubble('w8b', 290, 760, [E['watch']], .7)}
{cap('w8c', '', '귀한 시간,<br><span class="gold">출발은 10시 14분</span>')}"""
script = f"""
    tl.fromTo("#charging-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 돋보기가 화면을 훑다가 "출발 예정"에 멈춰 확대한다. (렌즈 속 이미지는 2배, 반대로 움직여 그 자리를 비춘다)
    const L = {{x: 200, y: 280}};
    const lens = () => {{ const c = document.querySelector("#lens"), h = document.querySelector("#lens-h");
      c.style.transform = `translate(${{L.x}}px, ${{L.y}}px)`; h.style.transform = `translate(${{L.x + 255}}px, ${{L.y + 245}}px)`;
      }};
    tl.fromTo(L, {{x: 160, y: 300}}, {{x: 560, y: 360, duration: 1.4, ease: "power2.inOut", onUpdate: lens}}, 0.2);
    tl.to(L, {{x: 350, y: 190, duration: 0.6, ease: "back.out(2)", onUpdate: lens}}, 1.6);
    {POP.format(b="#w8b", t=1.2)}
    // 회중시계 바늘이 시계 가운데를 축으로 빙글빙글 빨리 감기다 10시 14분에 딱 멈춘다(분침 3바퀴 = 시침 3칸).
    tl.fromTo("#hand", {{rotation: 0, svgOrigin: "0 0"}}, {{rotation: 1080 + 84, svgOrigin: "0 0", duration: 1.6, ease: "power3.out"}}, 1.3);
    tl.fromTo("#hand-h", {{rotation: 217, svgOrigin: "0 0"}}, {{rotation: 307, svgOrigin: "0 0", duration: 1.6, ease: "power3.out"}}, 1.3);
    tl.fromTo("#watch", {{scale: 1}}, {{scale: 1.18, duration: 0.12, yoyo: true, repeat: 1}}, 2.9);
    tl.fromTo("#w8c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.4);
    {BLUR_OUT.format(s="charging", t=4.86)}"""
scene("charging", 5, body, script)

# ---------------- S9 stop 33-37 s: 게이지 + 딸깍 ----------------
gauge = "".join(f'<div id="gb{i}" class="abs" style="left:190px;top:{880 - i*58}px;width:240px;height:42px;border:3px solid {C["rule"]};background:{C["stamp"]};transform-origin:0 50%"></div>' for i in range(10))
body = f"""
<div class="abs" style="left:150px;top:270px;width:320px;height:680px;border:5px solid {C['ink']};background:{C['stock']}"></div>
{gauge}
<div id="g9-pct" class="abs mono" style="left:150px;width:320px;top:290px;text-align:center;font-size:80px;color:{C['stamp']};z-index:3">0%</div>
<div class="abs" style="left:640px;top:380px;width:200px;height:330px;border:5px solid {C['ink']};border-radius:18px;background:{C['stock']}">
  <div style="text-align:center;font-size:34px;font-weight:700;color:{C['muted']};margin-top:14px">충전</div>
  <div style="position:absolute;left:55px;top:80px;width:90px;height:170px;border-radius:45px;border:4px solid {C['muted']};background:{C['ground']}"></div>
  <div id="knob" style="position:absolute;left:67px;top:92px;width:66px;height:66px;border-radius:50%;background:{C['stamp']}"></div>
  <div id="state" class="mono" style="position:absolute;left:0;right:0;top:268px;text-align:center;font-size:38px;color:{C['stamp']}">ON</div></div>
<svg id="glove" class="abs" style="left:820px;top:440px;width:300px;height:120px;z-index:7" viewBox="-130 -50 260 100"><rect x="0" y="-34" width="130" height="68" rx="10" fill="{C['ink']}"/><path d="M0 -40 q-50 -10 -70 10 l-40 0 q-14 10 0 20 l40 0 q-10 10 0 20 l40 0 q-8 10 4 18 l26 0 q30 -4 30 -30z" fill="#f6f3ec" stroke="{C['ground']}" stroke-width="4"/></svg>
<div id="ttalkkak" class="abs" style="left:600px;top:760px;font-size:120px;font-weight:800;color:{C['gold']};z-index:8;opacity:0">딸깍!</div>
<div id="flash" class="abs" style="left:0;top:0;width:1080px;height:1920px;background:#fff;opacity:0;z-index:20"></div>
<svg id="dust" class="abs" style="left:520px;top:300px;width:460px;height:520px;opacity:0;z-index:8" viewBox="0 0 460 520"><g fill="{C['gold']}">{''.join(f'<circle cx="{x}" cy="{y}" r="{r}"/>' for x, y, r in [(30, 40, 8), (420, 30, 10), (10, 300, 7), (450, 280, 9), (230, 10, 6), (100, 500, 8), (380, 500, 7)])}</g></svg>
{noble('s9n', 330, 1010, .5, z=6)}
{bubble('s9b', 400, 840, [E['love']], .7)}
{cap('s9c', '', '목표에 닿으면,<br><span class="gold">충전은 Chargis가 끕니다</span>')}"""
script = f"""
    tl.fromTo("#stop-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 게이지가 한 칸씩 띵띵 차오른다(50%).
    {''.join(f'tl.fromTo("#gb{i}", {{scaleX: 0}}, {{scaleX: 1, duration: 0.1, ease: "power2.out"}}, {0.2 + i*0.15:.2f});' for i in range(10))}
    const p9 = {{v: 0}};
    tl.to(p9, {{v: 50, duration: 1.5, ease: "none", onUpdate: () => {{ document.querySelector("#g9-pct").textContent = Math.round(p9.v) + "%"; }}}}, 0.2);
    // 50%에서 흰 장갑이 쓱 들어와 딸깍
    tl.fromTo("#glove", {{x: 320}}, {{x: -90, duration: 0.35, ease: "power3.out"}}, 1.75);
    tl.to("#knob", {{y: 92, backgroundColor: "{C['muted']}", duration: 0.1}}, 2.1);
    const st = {{v: 0}};
    tl.to(st, {{v: 1, duration: 0.04, onUpdate: () => {{ const e = document.querySelector("#state"); e.textContent = st.v > 0.5 ? "OFF" : "ON"; e.style.color = st.v > 0.5 ? "{C['ink']}" : "{C['stamp']}"; }}}}, 2.1);
    tl.fromTo("#flash", {{opacity: 0}}, {{opacity: 0.55, duration: 0.05, yoyo: true, repeat: 1}}, 2.1);
    tl.fromTo("#ttalkkak", {{opacity: 0, scale: 0.3, rotate: -12}}, {{opacity: 1, scale: 1, rotate: -6, duration: 0.25, ease: "back.out(3)"}}, 2.12);
    tl.fromTo("#dust", {{opacity: 0, scale: 0.5}}, {{opacity: 1, scale: 1.15, duration: 0.4, ease: "power2.out"}}, 2.12);
    tl.to("#glove", {{x: 320, duration: 0.3, ease: "power2.in"}}, 2.6);
    {POP.format(b="#s9b", t=2.4)}
    tl.fromTo("#s9c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.4);
    {BLUR_OUT.format(s="stop", t=3.86)}"""
scene("stop", 4, body, script)

# ---------------- S10 receipt 37-41 s: 봉투 개봉 + 영수증 ----------------
body = f"""
<div id="rc-sheet" class="abs" style="left:190px;top:240px;width:700px;height:560px;overflow:hidden;border:5px solid {C['gold']};z-index:2"><img src="assets/screens/09-finished.png" style="position:absolute;left:-10px;top:0;width:900px;height:562px"></div>
<svg id="env" class="abs" style="left:110px;top:700px;width:860px;height:380px;z-index:3" viewBox="0 0 860 380"><path d="M0 40 l860 0 l0 330 l-860 0z" fill="#d9ccaa" stroke="{C['ink']}" stroke-width="5"/><path d="M0 40 l430 220 l430 -220" fill="none" stroke="{C['ink']}" stroke-width="5"/></svg>
<svg id="flap" class="abs" style="left:110px;top:500px;width:860px;height:250px;z-index:4" viewBox="0 0 860 250"><path d="M0 240 l430 -220 l430 220z" fill="#cdbf9b" stroke="{C['ink']}" stroke-width="5"/></svg>
<div id="r10-big" class="abs" style="left:0;right:0;top:810px;text-align:center;font-size:150px;font-weight:800;color:{C['stamp']};z-index:6;opacity:0">18분 <span style="font-size:70px">아낌</span></div>
{stampbox('r10-stamp', '정산 완료', 600, 300, C['stamp'], -9, 58)}
{cap('r10c', '', '80%까지 채울 때보다<br><span class="gold">18분 아꼈사옵니다</span>')}"""
script = f"""
    tl.fromTo("#receipt-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 봉투가 열리고 영수증이 위로 쭉 뽑혀 나온다.
    tl.fromTo("#flap", {{scaleY: 1, transformOrigin: "50% 100%"}}, {{scaleY: -1, duration: 0.4, ease: "power2.inOut"}}, 0.2);
    tl.fromTo("#rc-sheet", {{y: 520}}, {{y: 0, duration: 0.9, ease: "power3.out"}}, 0.6);
    tl.fromTo("#r10-big", {{opacity: 0, scale: 0.4}}, {{opacity: 1, scale: 1, duration: 0.35, ease: "back.out(2.5)"}}, 1.6);
    tl.fromTo("#r10-stamp", {{scale: 2.4, opacity: 0}}, {{scale: 1, opacity: 1, duration: 0.18, ease: "power4.in"}}, 2.3);
    tl.to("#receipt-in", {{keyframes: [{{y: 8}}, {{y: -6}}, {{y: 0}}], duration: 0.18}}, 2.48);
    tl.fromTo("#r10c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.5);
    {BLUR_OUT.format(s="receipt", t=3.86)}"""
scene("receipt", 4, body, script)

# ---------------- S11 ending 41-46 s: 액자 걸기 → 얼굴 확대 → 외알 안경 윙크 → 브라운관 TV 꺼짐 ----------------
body = f"""
<div id="e11-zoom" class="abs" style="left:0;top:0;width:1080px;height:1920px">
<div id="e11-frame" class="abs" style="left:0;top:0;width:1080px;height:1120px">{GOLD_FRAME('e11-frame-art')}
  {noble('e11n', 540, 520, 1.5)}
  <div class="abs" style="left:300px;top:880px;width:480px;height:110px;background:{C['gold']};color:{C['ground']};text-align:center;font-size:72px;font-weight:800;line-height:110px">Chargis</div></div>
{bubble('e11b', 700, 260, [E['cool']], 1.0)}
<div id="e11-tags" class="abs" style="left:0;right:0;top:1322px;z-index:31;text-align:center;font-size:30px;font-weight:600;color:{C['muted']}">#HMG차량용앱개발공모전 #전기차 #충전</div>
{cap('e11c', '귀하게 자란 내가 쓰는 앱,', 'Chargis')}
<svg id="e11-star" class="abs" style="left:600px;top:448px;width:44px;height:44px;opacity:0;z-index:12" viewBox="-22 -22 44 44"><path d="M0 -22 L5 -5 L22 0 L5 5 L0 22 L-5 5 L-22 0 L-5 -5z" fill="#fff"/></svg>
</div>
<style>#ending-in {{ overflow: hidden; }}</style><div id="tv-white" class="abs" style="left:0;top:0;width:1080px;height:1920px;background:#fff;opacity:0;z-index:40"></div>"""
END_OUTER = '<div id="tv-dot" class="abs" style="left:530px;top:950px;width:20px;height:20px;border-radius:50%;background:#fff;opacity:0;z-index:2"></div>'
script = f"""
    tl.fromTo("#ending-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 액자가 위에서 내려와 덜컹 걸린다.
    tl.fromTo("#e11-frame", {{y: -1200}}, {{y: 0, duration: 0.7, ease: "bounce.out"}}, 0.1);
    tl.fromTo("#e11-frame", {{rotate: 0, transformOrigin: "50% 10%"}}, {{keyframes: [{{rotate: 3}}, {{rotate: -2}}, {{rotate: 0}}], duration: 0.5}}, 0.8);
    {POP.format(b="#e11b", t=1.1)}
    tl.fromTo("#e11n-mono", {{opacity: 1}}, {{opacity: 0.3, duration: 0.08, yoyo: true, repeat: 3}}, 1.4);
    tl.fromTo("#e11c-1", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.5);
    tl.fromTo("#e11c-2", {{opacity: 0, scale: 0.5}}, {{opacity: 1, scale: 1, duration: 0.35, ease: "back.out(2.5)"}}, 0.9);
    tl.fromTo("#e11-tags", {{opacity: 0}}, {{opacity: 1, duration: 0.3}}, 1.4);
    // 2.5 s: 외알 안경 쪽 얼굴로 확 들어가(3.2배) 화면 가운데에 둔다.
    tl.fromTo("#e11-zoom", {{scale: 1, x: 0, y: 0, transformOrigin: "576px 508px"}}, {{scale: 3.2, x: -36, y: 252, duration: 0.5, ease: "expo.inOut"}}, 2.5);
    // 3.4 s 윙크: 외알 안경이 찡긋 눌렸다 튀고, 같은 쪽 콧수염이 올라가고, 반짝.
    tl.to("#e11n-mono", {{keyframes: [{{scaleY: 0.12, scaleX: 1.15, rotation: -14, duration: 0.08, ease: "power3.in"}}, {{scaleY: 1.15, scaleX: 0.95, rotation: 4, duration: 0.12, ease: "power2.out"}}, {{scaleY: 1, scaleX: 1, rotation: 0, duration: 0.12}}], svgOrigin: "24 -8"}}, 3.4);
    tl.to("#e11n-mus", {{keyframes: [{{rotation: -9, y: -4, duration: 0.08}}, {{rotation: 0, y: 0, duration: 0.25, ease: "power2.out"}}], svgOrigin: "0 22"}}, 3.4);
    tl.to("#e11n-hat", {{keyframes: [{{rotation: 6, y: -6, duration: 0.1}}, {{rotation: 0, y: 0, duration: 0.25, ease: "back.out(3)"}}], svgOrigin: "0 -60"}}, 3.42);
    tl.fromTo("#e11-star", {{opacity: 0, scale: 0, rotation: 0}}, {{opacity: 1, scale: 1.25, rotation: 90, duration: 0.18, ease: "back.out(3)"}}, 3.48);
    tl.to("#e11-star", {{opacity: 0, scale: 0.6, rotation: 160, duration: 0.3, ease: "power2.in"}}, 3.75);
    // 3.9 s 브라운관 TV 꺼짐: 하얗게 번쩍이며 가로 한 줄로 눌리고 → 점으로 줄어 → 점이 사라진다.
    tl.to("#tv-white", {{opacity: 1, duration: 0.09, ease: "power2.in"}}, 3.9);
    tl.to("#ending-in", {{scaleY: 0.004, transformOrigin: "540px 960px", duration: 0.17, ease: "power3.in"}}, 3.9);
    tl.to("#ending-in", {{scaleX: 0, transformOrigin: "540px 960px", duration: 0.14, ease: "power3.in"}}, 4.08);
    tl.fromTo("#tv-dot", {{opacity: 0, scale: 1.4}}, {{opacity: 1, scale: 1, duration: 0.05}}, 4.2);
    tl.to("#tv-dot", {{opacity: 0, scale: 0.2, duration: 0.4, ease: "power2.in"}}, 4.28);"""
scene("ending", 5, body, script, END_OUTER)

# ---------------- 클럽 11.5-41.5 s: "안 돼."부터 Classic Party(색소폰 하우스)가 도는 동안 ----------------
# 곡은 120 BPM. 11.5 s부터 41.5 s까지 30 s = 60박(15마디), 마디 첫 박에서 시작한다. 움직임은 모두 이 박자에 맞춘다.
# clubbg: 미러볼·빛줄기·빛 점, clubsax: 네 모서리 색소폰 귀족. 둘 다 장면 뒤에 깔려 앱 화면·자막을 가리지 않는다.
CLUB_DUR = 30.0
BEAT = 0.5  # Classic Party (Saxophone Beat), 120 BPM
BEATS = 60  # 30 s = 60박(15마디). 41.5 s에 곡이 끝난다
MUSIC_END = BEATS * BEAT
BEAT_OFS = 0.0  # 렌더한 영상에서 잰 박 소리와 박 격자의 차이. 움직임을 여기에 맞춘다.
EXIT_AT = BEATS * BEAT - 0.3  # 곡 끝 직전 반 박에 퇴장을 시작해 41.5 s에 사라진다
BEAMS = [("#c9a44c", -38), ("#8fb2ee", -16), ("#d0508f", 6), ("#5fc39a", 24), ("#e8ae4a", 42)]
beams = "".join(f'<g id="bm{i}" transform="rotate({a} 540 150)"><path d="M540 150 L470 1900 L610 1900z" fill="{c}" opacity=".20"/></g>' for i, (c, a) in enumerate(BEAMS))
DOTS = [((i * 211) % 1080, 260 + (i * 137) % 980, 6 + (i * 7) % 9, ["#ede9e2", "#8fb2ee", "#d0508f", "#c9a44c", "#5fc39a"][i % 5]) for i in range(42)]
dots = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{c}" opacity=".55"/>' for x, y, r, c in DOTS)
facets = "".join(f'<rect x="{-400 + col*26}" y="{-90 + row*26}" width="24" height="24" fill="{["#d8d4cc", "#8e8a80", "#f4f1ea", "#a8b8d8", "#6f6b63"][(col*3 + row*2) % 5]}"/>' for col in range(64) for row in range(7))
body = f"""
<svg id="cb-light" class="abs" style="left:0;top:0;width:1080px;height:1920px" viewBox="0 0 1080 1920">
  <g id="cb-dots" style="transform-origin:540px 150px">{dots}</g>
  <g id="cb-beams">{beams}</g>
  <rect id="cb-flash" x="0" y="0" width="1080" height="1920" fill="#fff" opacity="0"/>
</svg>
<svg id="ball" class="abs" style="left:440px;top:0;width:200px;height:260px;overflow:visible" viewBox="-100 -110 200 260">
  <path d="M0 -400 L0 -88" stroke="{C['muted']}" stroke-width="4"/>
  <clipPath id="ballclip"><circle cx="0" cy="0" r="88"/></clipPath>
  <g clip-path="url(#ballclip)"><g id="facets">{facets}</g></g>
  <circle cx="0" cy="0" r="88" fill="none" stroke="{C['ink']}" stroke-width="4"/>
  <path id="glint" d="M-34 -40 l6 16 l16 6 l-16 6 l-6 16 l-6 -16 l-16 -6 l16 -6z" fill="#fff"/>
</svg>"""
beam_js = "".join(f'tl.fromTo("#bm{i}", {{rotation: {a}, svgOrigin: "540 150"}}, {{rotation: {a}, svgOrigin: "540 150", duration: 0.01}}, 0);' for i, (_, a) in enumerate(BEAMS))
# 빛줄기는 2박마다 방향을 홱 바꾸고, 마디 첫 박마다 한 번 번쩍인다.
for k in range(2, BEATS - 1, 2):
    d = 1 if (k // 2) % 2 else -1
    beam_js += "".join(f'tl.to("#bm{i}", {{rotation: {a + 24 * d * (1 if i % 2 else -1)}, svgOrigin: "540 150", duration: 0.28, ease: "power2.inOut"}}, {k*BEAT + BEAT_OFS:.3f});' for i, (_, a) in enumerate(BEAMS))
for k in range(4, BEATS, 4):
    beam_js += f'tl.to("#cb-flash", {{keyframes: [{{opacity: 0.09, duration: 0.04}}, {{opacity: 0, duration: 0.3}}]}}, {k*BEAT + BEAT_OFS:.3f});'
script = f"""
    // 0.25 s: 미러볼이 위에서 내려와 통 튀고, 빛줄기가 켜진다.
    tl.fromTo("#ball", {{y: -320}}, {{y: 0, duration: 0.6, ease: "bounce.out"}}, 0.25);
    tl.fromTo("#cb-light", {{opacity: 0}}, {{opacity: 1, duration: 0.3}}, 0.6);
    // 미러볼 면이 옆으로 흘러 도는 것처럼 보인다.
    tl.fromTo("#facets", {{x: 0}}, {{x: -26 * 40, duration: {CLUB_DUR}, ease: "none"}}, 0);
    tl.fromTo("#glint", {{opacity: 1, scale: 1, transformOrigin: "50% 50%"}}, {{opacity: 0.2, scale: 0.5, duration: {BEAT/2:.4f}, yoyo: true, repeat: {2*BEATS - 2}}}, {BEAT_OFS});
    {beam_js}
    tl.fromTo("#cb-dots", {{rotation: 0, svgOrigin: "540 150"}}, {{rotation: 140, svgOrigin: "540 150", duration: {CLUB_DUR - 0.6}, ease: "none"}}, 0.6);
    // 곡이 끝나기 직전(41.2 s): 미러볼이 올라가고 빛이 꺼진다.
    tl.to("#cb-light", {{opacity: 0, duration: 0.25}}, {EXIT_AT:.3f});
    tl.to("#ball", {{y: -320, duration: 0.35, ease: "power2.in"}}, {EXIT_AT:.3f});"""
html = f"""<template>
  <style>{CSS} #root {{ background: transparent; }}</style>
  <div id="root" data-composition-id="clubbg" data-width="1080" data-height="1920">
    <div id="clubbg-world" class="world clip" data-start="0" data-duration="{CLUB_DUR}">
{body}
    </div>
  </div>
  <script>
    const tl = gsap.timeline({{ paused: true }});
{script}
    window.__timelines["clubbg"] = tl;
  </script>
</template>
"""
(ROOT / "compositions" / "clubbg.html").write_text(html, encoding="utf-8")

def sax_noble(pid, x, y, s, flip):
    """색소폰 부는 귀족. 몸은 noble()과 같고 금색 색소폰을 든다. flip이면 좌우 반전."""
    base = noble(f"{pid}-n", 110, 170, 1.0)
    sax = f"""<g id="{pid}-sax"><path d="M10 26 L34 40 L44 150 Q48 192 84 178 L98 130" fill="none" stroke="{C['gold']}" stroke-width="18" stroke-linecap="round" stroke-linejoin="round"/>
<ellipse cx="100" cy="122" rx="22" ry="12" fill="{C['gold']}" stroke="#8a6d2a" stroke-width="4"/><g fill="#8a6d2a"><circle cx="38" cy="70" r="5"/><circle cx="40" cy="96" r="5"/><circle cx="42" cy="122" r="5"/></g></g>"""
    base = base.replace("</svg></div>", sax + "</svg></div>")
    w, h = 220 * s, 440 * s
    return f"""<div id="{pid}" class="abs" style="left:{x - w/2:.0f}px;top:{y - 170*s:.0f}px;width:{w:.0f}px;height:{h:.0f}px">
  <div id="{pid}-d" style="position:absolute;inset:0"><div style="position:absolute;inset:0;transform:scaleX({-1 if flip else 1})"><div style="position:absolute;left:0;top:0;width:220px;height:440px;transform:scale({s});transform-origin:0 0">{base}</div></div></div></div>"""

SAX = [("sx0", 105, 300, 0.58, False, -1), ("sx1", 975, 300, 0.58, True, 1), ("sx2", 105, 890, 0.58, False, -1), ("sx3", 975, 890, 0.58, True, 1)]
body = "".join(sax_noble(p, x, y, s, f) for p, x, y, s, f, _ in SAX)
js = ""
for i, (p, x, y, s_, f, side) in enumerate(SAX):
    # 첫 박 뒤(1박)에 모서리로 뛰어 들어온다.
    js += f'tl.fromTo("#{p}", {{x: {side * 260}, opacity: 0}}, {{x: 0, opacity: 1, duration: 0.2, ease: "back.out(2)"}}, {BEAT:.3f});\n'
    js += f'tl.fromTo("#{p}-d", {{rotation: 0, x: 0, y: 0, scaleY: 1, transformOrigin: "50% 95%"}}, {{rotation: 0, duration: 0.01}}, 0);\n'
    js += f'tl.fromTo("#{p}-sax", {{rotation: 0, svgOrigin: "10 26"}}, {{rotation: 0, svgOrigin: "10 26", duration: 0.01}}, 0);\n'
    for k in range(2, BEATS - 1):
        t = k * BEAT + BEAT_OFS; sk = 1 if k % 2 else -1
        # 매 박: 골반을 반대쪽으로 홱 튕기고(±14°, ±30 px) 몸이 눌렸다 펴진다.
        js += f'tl.to("#{p}-d", {{rotation: {14*sk*side}, x: {30*sk*side}, duration: 0.11, ease: "power3.out"}}, {t:.3f});'
        js += f'tl.to("#{p}-d", {{keyframes: [{{scaleY: 0.88, duration: 0.05}}, {{scaleY: 1, duration: 0.25, ease: "power2.out"}}]}}, {t:.3f});'
        if k % 4 == 0:
            # 마디 첫 박: 크게 튀어 오르고 실크해트가 머리 위로 떴다 떨어진다.
            js += f'tl.to("#{p}-d", {{keyframes: [{{y: -38, duration: 0.12, ease: "power2.out"}}, {{y: 0, duration: 0.2, ease: "power2.in"}}]}}, {t:.3f});'
            js += f'tl.to("#{p}-n-hat", {{keyframes: [{{y: -90, rotation: {-12*side}, duration: 0.16, ease: "power2.out"}}, {{y: 0, rotation: 0, duration: 0.2, ease: "power2.in"}}]}}, {t:.3f});'
        else:
            # 반 박 뒤: 고개 까딱 + 모자 들썩
            js += f'tl.to("#{p}-n-hat", {{keyframes: [{{y: -24, duration: 0.09}}, {{y: 0, duration: 0.12}}]}}, {t + BEAT/2:.3f});'
        if k % 2 == 0:
            # 2박마다 색소폰을 크게 휘두르고, 4마디마다 하늘로 치켜든다.
            r = -55 if k % 16 == 0 else (20 if k % 4 == 0 else -20)
            js += f'tl.to("#{p}-sax", {{rotation: {r}, svgOrigin: "10 26", duration: 0.14, ease: "power3.out"}}, {t:.3f});'
        js += "\n"
    # 곡이 끝나기 직전: 자세를 풀고 모서리 밖으로 빠진다.
    js += f'tl.to("#{p}-d", {{rotation: 0, x: 0, duration: 0.1}}, {EXIT_AT:.3f});\n'
    js += f'tl.to("#{p}", {{x: {side * 300}, opacity: 0, duration: 0.25, ease: "power2.in"}}, {EXIT_AT:.3f});\n'
html = f"""<template>
  <style>{CSS} #root {{ background: transparent; }}</style>
  <div id="root" data-composition-id="clubsax" data-width="1080" data-height="1920">
    <div id="clubsax-world" class="world clip" data-start="0" data-duration="{CLUB_DUR}" data-layout-allow-overlap>
{body}
    </div>
  </div>
  <script>
    const tl = gsap.timeline({{ paused: true }});
{js}
    window.__timelines["clubsax"] = tl;
  </script>
</template>
"""
(ROOT / "compositions" / "clubsax.html").write_text(html, encoding="utf-8")

# ---------------- index.html ----------------
SC = [("hook", 0, 3), ("cutoff", 3, 4.5), ("calc", 7.5, 4), ("reveal", 11.5, 2.5), ("recommend", 14, 5.5), ("gleo", 19.5, 4),
      ("congested", 23.5, 4.5), ("charging", 28, 5), ("stop", 33, 4), ("receipt", 37, 4), ("ending", 41, 5)]
hosts = "\n".join(f'      <div id="{s}" data-composition-id="{s}" data-composition-src="compositions/{s}.html" data-start="{a}" data-duration="{d}" data-track-index="1" data-track-kind="graphics" data-width="1080" data-height="1920" style="z-index:2"></div>' for s, a, d in SC)
hosts += f'\n      <div id="clubbg" data-composition-id="clubbg" data-composition-src="compositions/clubbg.html" data-start="11.5" data-duration="{CLUB_DUR}" data-track-index="2" data-track-kind="graphics" data-width="1080" data-height="1920" style="z-index:1"></div>'
hosts += f'\n      <div id="clubsax" data-composition-id="clubsax" data-composition-src="compositions/clubsax.html" data-start="11.5" data-duration="{CLUB_DUR}" data-track-index="4" data-track-kind="graphics" data-width="1080" data-height="1920" style="z-index:1"></div>'
SFX = [  # id, file, start, duration, volume
    ("harp-in", "harp.wav", 0.0, 1.5, 0.28), ("scratch", "scratch.wav", 1.38, 0.5, 0.25), ("dingdong", "dingdong.wav", 1.45, 1.3, 0.25),
    ("whoosh-1", "whoosh_deep.mp3", 0.45, 1.0, 0.2), ("stamp-1", "stamp_1.mp3", 4.6, 0.8, 0.55), ("thud-1", "thud.wav", 4.66, 0.6, 0.4),
    ("whoosh-2", "whoosh_short.mp3", 5.1, 0.8, 0.25), ("thud-2", "thud.wav", 6.0, 0.6, 0.3),
    *[(f"pop-{i}", "click.mp3", round(7.5 + t + 0.08, 2), 0.3, 0.25) for i, (t, _) in enumerate(JABS)], ("whoosh-3", "whoosh_deep.mp3", 10.1, 1.0, 0.22),
    ("stamp-no", "stamp_2.mp3", 11.5, 0.8, 0.9), ("bell", "bell.wav", 12.15, 1.4, 0.6), ("sparkle-1", "sparkle.wav", 12.85, 0.9, 0.5),
    ("whoosh-4", "whoosh_short.mp3", 14.1, 0.8, 0.4), *[(f"tick5-{i}", "tick.wav", round(15.0 + i*0.12, 2), 0.12, 0.35) for i in range(10)],
    ("sparkle-2", "sparkle.wav", 16.2, 0.9, 0.4), ("stamp-2", "stamp_3.mp3", 21.6, 0.8, 0.9), ("whoosh-5", "whoosh_short.mp3", 23.5, 0.8, 0.45),
    ("fan", "whoosh_short.mp3", 25.5, 0.6, 0.5), *[(f"tick8-{i}", "tick.wav", round(29.3 + i*0.15, 2), 0.12, 0.3) for i in range(10)],
    *[(f"tick9-{i}", "tick.wav", round(33.2 + i*0.15, 2), 0.12, 0.35) for i in range(10)], ("click-9", "click.mp3", 35.1, 0.4, 0.9),
    ("sparkle-3", "sparkle.wav", 35.12, 0.9, 0.55), ("whoosh-6", "whoosh_short.mp3", 37.2, 0.8, 0.45), ("stamp-3", "stamp_1.mp3", 39.3, 0.8, 0.9),
    ("thud-3", "thud.wav", 41.65, 0.6, 0.6), ("whoosh-7", "whoosh_deep.mp3", 43.45, 0.6, 0.4), ("wink", "wink.wav", 44.4, 0.35, 0.8), ("sparkle-4", "sparkle.wav", 44.48, 0.9, 0.45), ("tvoff", "tvoff.wav", 44.9, 0.45, 0.85)]
sfx = "\n".join(f'      <audio id="sfx-{i}" class="clip" src="assets/sfx/{f}" data-start="{s}" data-duration="{d}" data-track-index="3" data-volume="{v}"></audio>' for i, f, s, d, v in SFX)
index = f"""<!doctype html>
<html lang="ko" data-resolution="portrait">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1080, height=1920" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
    <style>
      * {{ margin: 0; padding: 0; box-sizing: border-box; }}
      html, body {{ margin: 0; width: 1080px; height: 1920px; overflow: hidden; background: #0f0e0d; }}
      #root {{ width: 100%; height: 100%; position: relative; background: #0f0e0d; }}
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="46" data-width="1080" data-height="1920">
      <!-- 귀하게 자란 내가: 투덜(0-11.5) / 반전(11.5-14) / 집사 Chargis(14-41) / 엔딩·윙크·TV 꺼짐(41-46) -->
{hosts}
      <!-- 음악(music.mp3): 0-11.5 Fluffing a Duck / 11.5-41.5 Classic Party 120 BPM / 41.45-44.9 박수+환호. tools/make_music.sh -->
      <audio id="bgm" class="clip" src="assets/bgm/music.mp3" data-start="0" data-duration="46" data-track-index="5" data-volume="0.55"></audio>
{sfx}
    </div>
    <script>
      window.__timelines = window.__timelines || {{}};
      window.__timelines["main"] = gsap.timeline({{ paused: true }});
    </script>
  </body>
</html>
"""
(ROOT / "index.html").write_text(index, encoding="utf-8")
print("ok", len(SC), "scenes")

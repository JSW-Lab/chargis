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
#root { position: absolute; inset: 0; background: #0f0e0d; font-family: "Wanted Sans"; color: #ede9e2; overflow: hidden; }
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

# ---------------- S2 cutoff 3-7.5 s: 도장 쾅 + 흔들림 + 모자 튀기 ----------------
body = f"""
<svg id="glass" class="abs" style="left:150px;top:300px;width:360px;height:620px" viewBox="-180 -330 360 620">
  <g fill="none" stroke="{C['ink']}" stroke-width="7"><path d="M-130 -300 l260 0 l-46 330 q-84 46 -168 0z"/><path d="M0 50 l0 200 M-100 260 l200 0"/></g>
  <clipPath id="gclip"><path d="M-130 -300 l260 0 l-46 330 q-84 46 -168 0z"/></clipPath>
  <rect id="liquid" x="-140" y="-300" width="280" height="360" fill="{C['stamp']}" opacity=".75" clip-path="url(#gclip)"/>
  <g id="crack" stroke="{C['ink']}" stroke-width="4" fill="none" opacity="0"><path d="M-40 -260 l20 60 l-30 50 l26 70"/><path d="M50 -200 l-26 40 l30 40"/></g>
</svg>
<div id="c2-pct" class="abs mono" style="left:150px;width:360px;top:420px;text-align:center;font-size:84px">38%</div>
{stampbox('c2-stamp', '40분 제한', 160, 190, C['warn'], -10, 60)}
<svg id="sofa" class="abs" style="left:560px;top:760px;width:480px;height:300px" viewBox="0 0 480 300"><path d="M0 160 q0 -120 120 -120 l300 0 q60 0 60 60 l0 140 l-480 0z" fill="{C['carpet']}" stroke="{C['ink']}" stroke-width="5"/><rect x="20" y="240" width="440" height="40" fill="#6e1f1f" stroke="{C['ink']}" stroke-width="5"/></svg>
{noble('c2n', 760, 600, .85)}
{bubble('c2b', 820, 420, [E['dizzy']], .9)}
{cap('c2c', '귀하게 자란 내가…', '충전이 끊겨도 될까?')}"""
script = f"""
    tl.fromTo("#cutoff-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 샴페인 잔 게이지가 38 → 46%로 차오른다.
    tl.fromTo("#liquid", {{attr: {{y: 0}}}}, {{attr: {{y: -150}}, duration: 1.4, ease: "none"}}, 0.1);
    const pct = {{v: 38}};
    tl.to(pct, {{v: 46, duration: 1.4, ease: "none", onUpdate: () => {{ document.querySelector("#c2-pct").textContent = Math.round(pct.v) + "%"; }}}}, 0.1);
    // 1.6 s 도장 쾅 + 화면 흔들림 + 잔에 금
    tl.fromTo("#c2-stamp", {{scale: 2.6, opacity: 0}}, {{scale: 1, opacity: 1, duration: 0.18, ease: "power4.in"}}, 1.5);
    tl.to("#cutoff-in", {{keyframes: [{{x: -18, y: 8}}, {{x: 16, y: -10}}, {{x: -10, y: 6}}, {{x: 6, y: -3}}, {{x: 0, y: 0}}], duration: 0.3, ease: "none"}}, 1.68);
    tl.to("#crack", {{opacity: 1, duration: 0.05}}, 1.7);
    tl.to("#c2-pct", {{color: "{C['warn']}", duration: 0.1}}, 1.7);
    // 2.1 s 귀족 기절: 뒤로 천천히 쓰러지고 실크해트가 붕 떴다가 떨어진다.
    tl.fromTo("#c2n", {{rotate: 0, transformOrigin: "50% 90%"}}, {{rotate: -72, x: 40, y: 80, duration: 0.9, ease: "power2.in"}}, 2.1);
    tl.fromTo("#c2n-hat", {{y: 0, rotate: 0}}, {{keyframes: [{{y: -220, rotate: -40, duration: 0.45, ease: "power2.out"}}, {{y: 30, rotate: -100, duration: 0.5, ease: "bounce.out"}}]}}, 2.2);
    {POP.format(b="#c2b", t=3.1)}
    tl.fromTo("#c2c-1", {{x: 0}}, {{keyframes: [{{x: -8}}, {{x: 8}}, {{x: -6}}, {{x: 0}}], duration: 0.3}}, 1.7);
    tl.fromTo("#c2c-2", {{opacity: 0, y: 30}}, {{opacity: 1, y: 0, duration: 0.3, ease: "power3.out"}}, 0.3);
    {BLUR_OUT.format(s="cutoff", t=4.36)}"""
scene("cutoff", 4.5, body, script)

# ---------------- S3 calc 7.5-11.5 s: 빙빙 도는 앱 + 숫자 폭탄 ----------------
apps = "".join(f'<div id="app{i}" class="abs" style="left:{x}px;top:{y}px;width:170px;height:300px;border:5px solid {C["ink"]};border-radius:22px;background:{C["stock"]};display:flex;align-items:center;justify-content:center;font-size:44px;font-weight:700;color:{C["muted"]}">{t}</div>'
               for i, (x, y, t) in enumerate([(455, 160, '앱 A'), (800, 520, '앱 B'), (455, 860, '앱 C'), (110, 520, '계산기')]))
nums = "".join(f'<div id="num{i}" class="abs mono" style="left:{x}px;top:{y}px;font-size:{s}px;color:{c};z-index:6;opacity:0">{t}</div>'
               for i, (x, y, s, t, c) in enumerate([(120, 250, 64, '50%?', C['gold']), (720, 260, 72, '80%?', C['warn']), (90, 860, 60, '100%?', C['gold']),
                                                   (740, 900, 58, '몇 km?', C['ink']), (380, 120, 54, '대기?', C['warn']), (360, 1000, 56, '제한?', C['ink'])]))
body = f"""
<div id="orbit" class="abs" style="left:0;top:0;width:1080px;height:1100px">{apps}</div>
{nums}
{noble('c3n', 540, 560, 1.15)}
{bubble('c3b', 660, 300, [E['think'], E['sweat'], E['boom']], 1.0)}
{cap('c3c', '귀하게 자란 내가…', '이걸 직접 계산해야 될까?')}"""
script = f"""
    tl.fromTo("#calc-in", {{opacity: 0}}, {{opacity: 1, duration: 0.12}}, 0);
    // 앱·계산기가 귀족 주위를 빙빙 돈다(점점 빨라짐).
    tl.fromTo("#orbit", {{rotate: 0, transformOrigin: "540px 690px"}}, {{rotate: 420, duration: 3.6, ease: "power2.in"}}, 0);
    {POP.format(b="#c3b", t=0.25)}
    {SWAP.format(a="#c3b-0", b="#c3b-1", t=1.4)}
    {SWAP.format(a="#c3b-1", b="#c3b-2", t=2.6)}
    // 숫자들이 사방에서 튀어나와 화면을 채운다.
    {''.join(f'tl.fromTo("#num{i}", {{opacity: 0, scale: 0}}, {{opacity: 1, scale: 1, duration: 0.25, ease: "back.out(3)"}}, {0.5 + i*0.3:.2f});' for i in range(6))}
    // 🤯 순간 숫자 폭발: 바깥으로 흩어진다.
    {''.join(f'tl.to("#num{i}", {{x: {dx}, y: {dy}, opacity: 0, rotate: {r}, duration: 0.45, ease: "power3.in"}}, 2.65);' for i, (dx, dy, r) in enumerate([(-300, -200, -40), (300, -220, 50), (-300, 260, 30), (300, 240, -60), (0, -380, 20), (0, 380, -20)]))}
    tl.fromTo("#c3n-hat", {{y: 0}}, {{y: -80, rotate: 15, duration: 0.18, ease: "power2.out", yoyo: true, repeat: 1}}, 2.6);
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
<svg id="watch" class="abs" style="left:350px;top:930px;width:120px;height:120px;z-index:8" viewBox="-60 -60 120 120"><circle r="52" fill="{C['gold']}" stroke="{C['ink']}" stroke-width="5"/><circle r="40" fill="{C['ink']}"/><path id="hand" d="M0 0 l0 -32" stroke="{C['ground']}" stroke-width="6" stroke-linecap="round"/></svg>
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
    // 회중시계 바늘이 빙글빙글 돌다 딱 멈춘다.
    tl.fromTo("#hand", {{rotate: 0, transformOrigin: "0px 0px"}}, {{rotate: 1080 + 60, duration: 1.6, ease: "power3.out"}}, 1.3);
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

# ---------------- S11 ending 41-45 s: 액자 걸기 + 루프 ----------------
body = f"""
<div id="e11-frame" class="abs" style="left:0;top:0;width:1080px;height:1120px">{GOLD_FRAME('e11-frame-art')}
  {noble('e11n', 540, 520, 1.5)}
  <div class="abs" style="left:300px;top:880px;width:480px;height:110px;background:{C['gold']};color:{C['ground']};text-align:center;font-size:72px;font-weight:800;line-height:110px">Chargis</div></div>
{bubble('e11b', 700, 260, [E['cool']], 1.0)}
<div id="e11-tags" class="abs" style="left:0;right:0;top:1290px;text-align:center;font-size:30px;font-weight:600;color:{C['muted']}">#HMG차량용앱개발공모전 #전기차 #충전</div>
{cap('e11c', '귀하게 자란 내가 쓰는 앱,', 'Chargis')}"""
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
    // 루프: 마지막 0.5 s 귀족 얼굴로 확 들어가 첫 장면(클로즈업)과 이어진다.
    tl.to("#ending-in", {{scale: 2.7, transformOrigin: "540px 480px", duration: 0.5, ease: "expo.in"}}, 3.5);"""
scene("ending", 4, body, script)

# ---------------- index.html ----------------
SC = [("hook", 0, 3), ("cutoff", 3, 4.5), ("calc", 7.5, 4), ("reveal", 11.5, 2.5), ("recommend", 14, 5.5), ("gleo", 19.5, 4),
      ("congested", 23.5, 4.5), ("charging", 28, 5), ("stop", 33, 4), ("receipt", 37, 4), ("ending", 41, 4)]
hosts = "\n".join(f'      <div id="{s}" data-composition-id="{s}" data-composition-src="compositions/{s}.html" data-start="{a}" data-duration="{d}" data-track-index="1" data-track-kind="graphics" data-width="1080" data-height="1920"></div>' for s, a, d in SC)
SFX = [  # id, file, start, duration, volume
    ("harp-in", "harp.wav", 0.0, 1.5, 0.55), ("scratch", "scratch.wav", 1.38, 0.5, 0.5), ("dingdong", "dingdong.wav", 1.45, 1.3, 0.5),
    ("whoosh-1", "whoosh_deep.mp3", 0.45, 1.0, 0.4), ("stamp-1", "stamp_1.mp3", 4.6, 0.8, 0.9), ("thud-1", "thud.wav", 4.66, 0.6, 0.7),
    ("whoosh-2", "whoosh_short.mp3", 5.1, 0.8, 0.5), ("thud-2", "thud.wav", 6.0, 0.6, 0.5),
    *[(f"pop-{i}", "click.mp3", round(8.0 + i*0.3, 2), 0.3, 0.45) for i in range(6)], ("whoosh-3", "whoosh_deep.mp3", 10.1, 1.0, 0.45),
    ("stamp-no", "stamp_2.mp3", 11.5, 0.8, 0.9), ("bell", "bell.wav", 12.15, 1.4, 0.6), ("sparkle-1", "sparkle.wav", 12.85, 0.9, 0.5),
    ("whoosh-4", "whoosh_short.mp3", 14.1, 0.8, 0.4), *[(f"tick5-{i}", "tick.wav", round(15.0 + i*0.12, 2), 0.12, 0.35) for i in range(10)],
    ("sparkle-2", "sparkle.wav", 16.2, 0.9, 0.4), ("stamp-2", "stamp_3.mp3", 21.6, 0.8, 0.9), ("whoosh-5", "whoosh_short.mp3", 23.5, 0.8, 0.45),
    ("fan", "whoosh_short.mp3", 25.5, 0.6, 0.5), *[(f"tick8-{i}", "tick.wav", round(29.3 + i*0.15, 2), 0.12, 0.3) for i in range(10)],
    *[(f"tick9-{i}", "tick.wav", round(33.2 + i*0.15, 2), 0.12, 0.35) for i in range(10)], ("click-9", "click.mp3", 35.1, 0.4, 0.9),
    ("sparkle-3", "sparkle.wav", 35.12, 0.9, 0.55), ("whoosh-6", "whoosh_short.mp3", 37.2, 0.8, 0.45), ("stamp-3", "stamp_1.mp3", 39.3, 0.8, 0.9),
    ("thud-3", "thud.wav", 41.65, 0.6, 0.6), ("harp-out", "harp.wav", 42.0, 2.6, 0.55), ("whoosh-7", "whoosh_deep.mp3", 44.4, 0.6, 0.4)]
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
    <div id="root" data-composition-id="main" data-start="0" data-duration="45" data-width="1080" data-height="1920">
      <!-- 귀하게 자란 내가: 투덜(0-11.5) / 반전(11.5-14) / 집사 Chargis(14-41) / 엔딩·루프(41-45) -->
{hosts}
      <!-- 음악: 우아한 하프로 열고, 번호표 순간 레코드 긁힘으로 끊는다. 반전부터 배경음악 -->
      <audio id="bgm" class="clip" src="assets/bgm/track.mp3" data-start="11.6" data-duration="33.4" data-media-start="4" data-track-index="5" data-volume="0.09" data-fade-in="0.4" data-fade-out="1.2"></audio>
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

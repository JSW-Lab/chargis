# -*- coding: utf-8 -*-
"""시연 영상(약 2분 40초)을 만든다.
1) Pleos 에뮬레이터 실제 녹화(segA.mp4) 앞부분: 발권 → 주행 → 추천 → [경유지로 수락] → "앱에서 안내"
2) 같은 날 에뮬레이터에서 찍은 화면(docs/screenshots): 도착 → 충전(차량 한도) → 동승자 → 충전 완료 → 영수증
앱이 낸 음성 안내(logcat ChargisTts)를 그 시각에 자막으로 넣고, 장면 제목을 왼쪽 위에 둔다.
사용: python make_demo.py <segA.mp4> <tts_epoch.log> <start_epoch_file> <out.mp4> [녹화에서 쓸 길이(초)]"""
import re, subprocess, sys, pathlib
src, log, start, out = sys.argv[1], sys.argv[2], float(open(sys.argv[3]).read().strip()), sys.argv[4]
CUT = float(sys.argv[5]) if len(sys.argv) > 5 else 118.0
here = pathlib.Path(__file__).resolve().parent
shots = here.parent.parent / 'docs' / 'screenshots'
INTRO, SLIDE, OUTRO = 3.0, 6.0, 4.0
FA, FB, FK = 14.0, 82.0, 4.0   # 녹화의 이 구간은 주행만 이어져 4배속으로 줄인다
def M(t):
    return t if t < FA else (FA + (t - FA) / FK if t < FB else FA + (FB - FA) / FK + (t - FB))

SLIDES = [('05-arrived.png', '④ 휴게소 도착: 현재 37% → 목표 50%, 예상 9분, 제한 40분'),
          ('06-charging.png', '⑤ 충전 중: 차량 충전 한도 60% 설정, 출발 예정 10:14'),
          ('23-passenger.png', '⑥ 동승자 화면: 출발 예정 시각만 크게'),
          ('07-charge-done.png', '⑦ 목표 50%에서 Chargis가 차량 충전을 끔'),
          ('09-finished.png', '⑧ 목적지 도착: 정산 영수증, 80%까지보다 18분 아낌')]

def ts(t):
    return f'{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}'

events = []
for ln in open(log, encoding='utf-8', errors='replace'):
    m = re.match(r'\s*(\d+\.\d+)\s.*ChargisTts: \[\w+\] (.*)', ln)
    if m and 0 <= float(m.group(1)) - start < CUT:
        events.append((float(m.group(1)) - start, m.group(2).strip()))

head = ['[Script Info]', 'ScriptType: v4.00+', 'PlayResX: 1920', 'PlayResY: 1080', '', '[V4+ Styles]',
        'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
        'Style: Sub,Wanted Sans,44,&H00FFFFFF,&H00FFFFFF,&H00000000,&HC0000000,1,0,0,0,100,100,0,0,3,14,0,2,80,80,40,1',
        'Style: Chap,Wanted Sans,38,&H004CA4C9,&H00FFFFFF,&H00000000,&HC0000000,1,0,0,0,100,100,0,0,3,12,0,7,30,30,30,1',
        'Style: Card,Wanted Sans,72,&H00E2E9ED,&H00FFFFFF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,0,0,5,80,80,0,1',
        '', '[Events]', 'Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text']
ev = []
for i, (t, text) in enumerate(events):
    nxt = events[i + 1][0] if i + 1 < len(events) else CUT
    ev.append((INTRO + M(t), INTRO + M(min(max(t + 3, min(t + 6, nxt)), CUT)), 'Sub', '🔊 ' + text))
def first(word, default):
    return next((t for t, x in events if word in x), default)
chap = [(0, '① 발권: S1 서울→속초(가상), 출발 배터리 60%'), (first('추천', 60) - 1, '② 주행 중 추천: 어디서·몇 %까지·얼마 동안'),
        (first('안내합니다', 95) - 1, '③ [경유지로 수락]: 내비 목적지가 없어 "앱에서 안내"')]
for i, (a, title) in enumerate(chap):
    b = chap[i + 1][0] if i + 1 < len(chap) else CUT
    ev.append((INTRO + M(max(a, 0)), INTRO + M(b), 'Chap', title))
base = INTRO + M(CUT)
for i, (_, title) in enumerate(SLIDES):
    ev.append((base + i * SLIDE, base + (i + 1) * SLIDE, 'Chap', title))
end = base + len(SLIDES) * SLIDE
ev.append((0, INTRO, 'Card', 'Chargis 시연\\N{\\fs40\\c&HA19C93&}Pleos Connect 에뮬레이터 v2.1.0 · 앱 0.2.0 · 2026-11-09 녹화'))
ev.append((base, base + 0.01, 'Card', ''))
ev.append((end, end + OUTRO, 'Card', 'github.com/JSW-Lab/chargis\\N{\\fs40\\c&HA19C93&}④~⑧은 같은 날 에뮬레이터에서 찍은 화면입니다'))
open(here / 'demo.ass', 'w', encoding='utf-8-sig').write('\n'.join(head + [f'Dialogue: 0,{ts(a)},{ts(b)},{st},,0,0,0,,{tx}' for a, b, st, tx in ev]))

fit = 'scale=1522:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0x0f0e0d,setsar=1,fps=30,format=yuv420p'
inputs = ['-i', src] + sum([['-loop', '1', '-t', str(SLIDE), '-i', str(shots / f)] for f, _ in SLIDES], [])
parts = [f'[0:v]crop=1280:908:640:0,split=3[r1][r2][r3]', f'[r1]trim=0:{FA},setpts=PTS-STARTPTS,{fit}[a1]',
         f'[r2]trim={FA}:{FB},setpts=(PTS-STARTPTS)/{FK},{fit}[a2]', f'[r3]trim={FB}:{CUT},setpts=PTS-STARTPTS,{fit}[a3]', '[a1][a2][a3]concat=n=3:v=1:a=0[app]']
parts += [f'[{i + 1}:v]{fit}[s{i}]' for i in range(len(SLIDES))]
parts += [f'color=c=0x0f0e0d:s=1920x1080:d={INTRO}:r=30,format=yuv420p[in]', f'color=c=0x0f0e0d:s=1920x1080:d={OUTRO}:r=30,format=yuv420p[out]']
parts += ['[in][app]' + ''.join(f'[s{i}]' for i in range(len(SLIDES))) + f'[out]concat=n={len(SLIDES) + 3}:v=1:a=0,subtitles=demo.ass:fontsdir=fonts[v]']
fontdir = here / 'fonts'
fontdir.mkdir(exist_ok=True)
for f in ['wanted_sans_bold.ttf']:
    (fontdir / f).write_bytes((here.parent / 'shortform-noble' / 'assets' / 'fonts' / f).read_bytes())
subprocess.check_call(['ffmpeg', '-v', 'error', '-y', *inputs, '-filter_complex', ';'.join(parts), '-map', '[v]',
                       '-c:v', 'libx264', '-crf', '20', '-preset', 'medium', '-an', out], cwd=here)
print('ok', round(end + OUTRO, 1), 's,', len(events), 'voice lines')

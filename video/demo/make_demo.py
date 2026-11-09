# -*- coding: utf-8 -*-
"""시연 영상을 만든다: Pleos 에뮬레이터에서 S1을 처음부터 끝까지 한 번에 녹화한 화면을 잘라 붙인다.
발권 → 주행 → B 휴게소 추천 → [경유지로 수락] → "앱에서 안내" → 도착·충전(차량 충전 한도) → 목표 50%에서 Chargis가
충전을 멈춤 → 출발 → 목적지 정산 영수증. 운전만 이어지는 구간은 빨리 감고, 앱이 낸 음성 안내(logcat ChargisTts)를
그 시각에 자막으로 넣는다.

사용: python make_demo.py <녹화 폴더> <out.mp4>
녹화 폴더: d1.mp4 d2.mp4 ...(adb screenrecord 1920x1080, 3분씩), start1 start2 ...(각 녹화를 시작한 PC 시각),
events.txt(PC 시각 + 사건 이름), tts.log(logcat -v epoch -s ChargisTts)"""
import re, subprocess, sys, pathlib

rec, out = pathlib.Path(sys.argv[1]), sys.argv[2]
here = pathlib.Path(__file__).resolve().parent
EMU_TO_HOST = 1.0      # 에뮬레이터 시계가 PC보다 약 1 s 늦다(수락 탭과 그 안내 음성 시각으로 잼)
INTRO, OUTRO = 3.0, 4.0
CROP = 'crop=1280:908:640:67'   # 2560x1440 화면의 앱 영역(854,90)-(2560,1300)을 1920x1080 녹화 비율로

segs = sorted(rec.glob('d*.mp4'), key=lambda p: int(p.stem[1:]))
starts = [float((rec / f'start{p.stem[1:]}').read_text()) for p in segs]
durs = [float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', str(p)])) for p in segs]
H0 = starts[0]
ev = {}
for ln in (rec / 'events.txt').read_text(encoding='utf-8').splitlines():
    t, name, *_ = ln.split()
    ev.setdefault(name, float(t))

# (PC 시각 시작, 끝, 배속, 빨리 감기 표시)
E = ev
PLAN = [(E['launch'] + 0.5, E['speed20'], 1),
        (E['speed20'], E['recommend'] - 2.2, 4),
        (E['recommend'] - 2.2, E['accept'] + 6.0, 1),
        (E['accept'] + 6.0, E['arrived'] - 1.5, 4),
        (E['arrived'] - 1.5, E['charge_on'] + 2.0, 1),
        (E['charge_on'] + 2.0, E['done'] - 1.0, 2),
        (E['done'] - 1.0, E['depart'] + 4.0, 1),
        (E['depart'] + 4.0, E['at_destination'] - 1.5, 8),
        (E['at_destination'] - 1.5, E['rec_stopped'] - 4.0, 1)]

def out_time(h):
    """PC 시각 h가 완성 영상의 몇 초인지."""
    t = INTRO
    for a, b, k in PLAN:
        if h < a: return None
        if h <= b: return t + (h - a) / k
        t += (b - a) / k
    return None
total = INTRO + sum((b - a) / k for a, b, k in PLAN) + OUTRO

def ts(t):
    return f'{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}'

lines = []
for ln in (rec / 'tts.log').read_text(encoding='utf-8', errors='replace').splitlines():
    m = re.match(r'\s*(\d+\.\d+)\s.*ChargisTts: \[\w+\] (.*)', ln)
    if m:
        t = out_time(float(m.group(1)) + EMU_TO_HOST)
        if t is not None: lines.append((t, m.group(2).strip()))
# 거의 동시에 나온 두 안내(0.6 s 안)는 겹쳐 쌓이지 않게 뒤 것을 2.6 s 미룬다.
for i in range(1, len(lines)):
    if lines[i][0] - lines[i - 1][0] < 0.6: lines[i] = (lines[i - 1][0] + 2.6, lines[i][1])
ass = []
for i, (t, text) in enumerate(lines):
    nxt = lines[i + 1][0] if i + 1 < len(lines) else total
    ass.append((t, min(t + 4.5, nxt - 0.02), 'Sub', '🔊 ' + text))
chap = [(E['launch'] + 0.5, '① 발권: S1 서울→속초(가상), 출발 배터리 60%'),
        (E['speed20'], '② 주행: B 휴게소까지 86km, 목표 50%로 미리 계획'),
        (E['recommend'] - 2.2, '③ 주행 중 추천 → [경유지로 수락] → 내비 목적지가 없어 "앱에서 안내"'),
        (E['arrived'] - 1.5, '④ B 휴게소 도착 · 충전 시작(차량 충전 한도 60%)'),
        (E['done'] - 1.0, '⑤ 목표 50%에서 Chargis가 충전을 멈춤 → 기어 D로 출발'),
        (E['depart'] + 4.0, '⑥ 목적지까지 주행'),
        (E['at_destination'] - 1.5, '⑦ 목적지 도착: 정산 영수증(아낀 시간 18분)')]
for i, (h, title) in enumerate(chap):
    a = out_time(h); b = out_time(chap[i + 1][0]) if i + 1 < len(chap) else total - OUTRO
    ass.append((a, b, 'Chap', title))
t = INTRO
for a, b, k in PLAN:
    d = (b - a) / k
    if k > 1: ass.append((t, t + d, 'Fast', f'▶▶ {k}배속'))
    t += d
ass.append((0, INTRO, 'Card', 'Chargis 시연\\N{\\fs40\\c&HA19C93&}Pleos Connect 에뮬레이터 v2.1.0 · 앱 0.2.0 · S1 처음부터 끝까지 한 번에 녹화'))
ass.append((total - OUTRO, total, 'Card', 'github.com/JSW-Lab/chargis\\N{\\fs40\\c&HA19C93&}운전·충전 구간은 빨리 감았습니다'))
head = ['[Script Info]', 'ScriptType: v4.00+', 'PlayResX: 1920', 'PlayResY: 1080', '', '[V4+ Styles]',
        'Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding',
        'Style: Sub,Wanted Sans,40,&H00FFFFFF,&H00FFFFFF,&H00000000,&HC0000000,1,0,0,0,100,100,0,0,3,10,0,2,80,80,22,1',
        'Style: Chap,Wanted Sans,36,&H004CA4C9,&H00FFFFFF,&H00000000,&HC0000000,1,0,0,0,100,100,0,0,3,8,0,7,30,30,14,1',
        'Style: Fast,Wanted Sans,36,&H0000D7FF,&H00FFFFFF,&H00000000,&HC0000000,1,0,0,0,100,100,0,0,3,8,0,9,30,30,14,1',
        'Style: Card,Wanted Sans,72,&H00E2E9ED,&H00FFFFFF,&H00000000,&H00000000,1,0,0,0,100,100,0,0,1,0,0,5,80,80,0,1',
        '', '[Events]', 'Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text']
(here / 'demo.ass').write_text('\n'.join(head + [f'Dialogue: 0,{ts(a)},{ts(b)},{st},,0,0,0,,{tx}' for a, b, st, tx in ass]), encoding='utf-8-sig')

# 녹화 조각들을 PC 시각 축 하나로 잇는다(조각 사이 틈은 앞 조각 마지막 장면으로 채움).
inputs, parts, lab = [], [], []
for i, (p, s, d) in enumerate(zip(segs, starts, durs)):
    inputs += ['-i', str(p)]
    gap = (starts[i + 1] - s - d) if i + 1 < len(segs) else 0
    parts.append(f'[{i}:v]fps=30,{CROP},setsar=1,tpad=stop_mode=clone:stop_duration={max(gap, 0):.3f}[m{i}]')
    lab.append(f'[m{i}]')
parts.append(''.join(lab) + f'concat=n={len(segs)}:v=1:a=0,split={len(PLAN)}' + ''.join(f'[s{i}]' for i in range(len(PLAN))))
# 앱 화면을 조금 줄여 위(장면 제목)와 아래(음성 자막)에 띠를 남긴다. 자막이 앱 버튼을 가리지 않게.
fit = 'scale=1268:900,pad=1920:1080:(ow-iw)/2:72:color=0x0f0e0d,setsar=1,fps=30,format=yuv420p'
for i, (a, b, k) in enumerate(PLAN):
    parts.append(f'[s{i}]trim={a - H0:.3f}:{b - H0:.3f},setpts=(PTS-STARTPTS)/{k},{fit}[p{i}]')
parts.append(f'color=c=0x0f0e0d:s=1920x1080:d={INTRO}:r=30,format=yuv420p[in]')
parts.append(f'color=c=0x0f0e0d:s=1920x1080:d={OUTRO}:r=30,format=yuv420p[out]')
parts.append('[in]' + ''.join(f'[p{i}]' for i in range(len(PLAN))) + f'[out]concat=n={len(PLAN) + 2}:v=1:a=0,subtitles=demo.ass:fontsdir=fonts[v]')
fontdir = here / 'fonts'
fontdir.mkdir(exist_ok=True)
(fontdir / 'wanted_sans_bold.ttf').write_bytes((here.parent / 'shortform-noble' / 'assets' / 'fonts' / 'wanted_sans_bold.ttf').read_bytes())
subprocess.check_call(['ffmpeg', '-v', 'error', '-y', *inputs, '-filter_complex', ';'.join(parts), '-map', '[v]',
                       '-c:v', 'libx264', '-crf', '20', '-preset', 'medium', '-an', out], cwd=here)
print('ok', round(total, 1), 's,', len(lines), 'voice lines')

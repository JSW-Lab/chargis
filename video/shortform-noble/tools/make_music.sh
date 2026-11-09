#!/usr/bin/env bash
# 숏폼 배경음악 assets/bgm/music.mp3(46 s)를 만든다. 프로젝트 폴더에서 실행: bash tools/make_music.sh
#  0-11.5 s     sound/fluffing_a_duck.mp3 첫 11.5 s ("Fluffing a Duck", Kevin MacLeod, CC BY 4.0). "안 돼." 직전 0.12 s에 끊는다
#  11.5-41.5 s  sound/classic_party.mp3 113.10-143.10 s ("Classic Party (Saxophone Beat)", 9JackJack8, Pixabay).
#               120 BPM, 113.10 s는 브레이크 뒤 킥이 다시 들어오는 마디 첫 박. 30 s = 60박(15마디)
#  41.45-44.9 s sound/aplausos_3.mp3 + assets/sfx/cheer.mp3(CC0) 박수·환호, TV가 꺼지는 순간(44.9 s) 뚝 끊긴다
set -e
ffmpeg -v error -y \
 -i sound/fluffing_a_duck.mp3 -ss 113.10 -t 30.0 -i sound/classic_party.mp3 -i sound/aplausos_3.mp3 -i assets/sfx/cheer.mp3 -filter_complex "
[0:a]aresample=44100,atrim=0:11.5,loudnorm=I=-19:TP=-2,afade=t=out:st=11.38:d=0.12[A];
[1:a]aresample=44100,loudnorm=I=-18:TP=-2,afade=t=out:st=29.9:d=0.1,adelay=11500|11500[B];
[2:a]aresample=44100,atrim=0:3.6,loudnorm=I=-19:TP=-2,afade=t=in:d=0.12,afade=t=out:st=3.32:d=0.13,adelay=41450|41450[C];
[3:a]aresample=44100,atrim=0:3.5,loudnorm=I=-21:TP=-3,afade=t=in:d=0.1,afade=t=out:st=3.22:d=0.13,adelay=41550|41550[D];
[A][B][C][D]amix=inputs=4:normalize=0:duration=longest,atrim=0:46,aformat=channel_layouts=stereo" -c:a libmp3lame -q:a 2 assets/bgm/music.mp3

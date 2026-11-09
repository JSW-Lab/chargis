#!/usr/bin/env bash
# 숏폼 배경음악 assets/bgm/music.mp3(46 s)를 만든다. 프로젝트 폴더에서 실행: bash tools/make_music.sh
#  0-11.5 s   sound/1-jwajeol.mp3 (10.9 s) 두 번을 0.3 s 겹쳐 잇고 "안 돼." 직전 0.12 s에 끊는다
#  11.5-41.12 sound/epicsaxguy.mp3 (14.811 s = 8마디) 두 번을 그대로 잇는다(이음새 없음)
#  41.05-44.9 sound/aplausos_3.mp3 + assets/sfx/cheer.mp3(CC0) 박수·환호, TV가 꺼지는 순간(44.9 s) 뚝 끊긴다
set -e
ffmpeg -v error -y \
 -i sound/1-jwajeol.mp3 -i sound/1-jwajeol.mp3 -i sound/epicsaxguy.mp3 -i sound/epicsaxguy.mp3 -i sound/aplausos_3.mp3 -i assets/sfx/cheer.mp3 -filter_complex "
[0:a]aresample=44100,loudnorm=I=-20:TP=-2[j0];[1:a]aresample=44100,loudnorm=I=-20:TP=-2[j1];
[j0][j1]acrossfade=d=0.3:c1=tri:c2=tri,atrim=0:11.5,afade=t=out:st=11.38:d=0.12[A];
[2:a]aresample=44100[s0];[3:a]aresample=44100[s1];[s0][s1]concat=n=2:v=0:a=1,loudnorm=I=-18:TP=-2,afade=t=out:st=29.55:d=0.07,adelay=11500|11500[B];
[4:a]aresample=44100,atrim=0:4,loudnorm=I=-19:TP=-2,afade=t=in:d=0.12,afade=t=out:st=3.72:d=0.13,adelay=41050|41050[C];
[5:a]aresample=44100,atrim=0:3.9,loudnorm=I=-21:TP=-3,afade=t=in:d=0.1,afade=t=out:st=3.62:d=0.13,adelay=41150|41150[D];
[A][B][C][D]amix=inputs=4:normalize=0:duration=longest,atrim=0:46,aformat=channel_layouts=stereo" -c:a libmp3lame -q:a 2 assets/bgm/music.mp3

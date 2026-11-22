"""Small deterministic original cues, synthesized offline to mono PCM."""
from array import array
import math
import random
import subprocess
import sys
import wave

# duration, start hertz, end hertz, noise, beat modulation, attack seconds
CUES = {
    "focus": (.34, 134, 372, .17, 13, .08),
    "ignite": (.23, 438, 710, .23, 18, .022),
    "sweep": (.20, 560, 160, .74, 0, .018),
    "shield_form": (.42, 265, 485, .10, 10, .065),
    "shield_break": (.21, 1030, 265, .55, 26, .005),
    "stone_break": (.32, 115, 47, .66, 17, .006),
    "rewind": (.41, 720, 192, .13, 15, .11),
    "rift": (.30, 158, 75, .32, 22, .04),
    "detonate": (.42, 107, 35, .53, 3, .006),
    "evolution": (.49, 260, 690, .06, 7, .036),
    "synergy": (.51, 194, 550, .09, 5, .041),
    "seal": (.31, 392, 510, .12, 8, .024),
    "prism": (.33, 451, 914, .04, 9, .020),
}


def bake(sounds, temporary):
    rate = 24000
    for name, (duration,start,end,noise,beat,attack) in CUES.items():
        rng = random.Random(name)
        phase = smoothed = 0.
        pcm = array("h")
        for i in range(round(duration * rate)):
            time = i / rate
            t = time / duration
            phase += math.tau * (start + (end - start)*t*t) / rate
            white = rng.uniform(-1,1)
            smoothed += (.24 if noise > .5 else .075) * (white - smoothed)
            env = min(1, time / attack) * (1-t)**2.15
            carrier = (math.sin(phase) + .30*math.sin(phase*1.504) + .18*math.sin(phase*2.001))
            signal = (carrier*(1-noise)*.52 + smoothed*noise*2.8) * env
            signal *= .83 + .17 * math.cos(math.tau * beat * time) if beat else 1
            pcm.append(int(max(-1,min(1,signal)) * 25100))
        if sys.byteorder == "big":
            pcm.byteswap()
        with wave.open(str(temporary), "wb") as wav:
            wav.setparams((1,2,rate,0,"NONE","not compressed"))
            wav.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg","-v","error","-y","-i",str(temporary),"-map_metadata","-1",
                        "-c:a","libvorbis","-q:a","3","-fflags","+bitexact","-flags:a","+bitexact",
                        str(sounds / f"{name}.ogg")],check=True)

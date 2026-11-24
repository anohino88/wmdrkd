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
    "prism": (.55, 451, 914, .04, 9, .020),
    "ui": (.19, 660, 470, .05, 0, .005),
}


# gain, frequency ratio, onset and decay. Separate the contact noise from the tonal tail.
VOICES = {
    "rift": ((.34,.49,0,9),(.21,3.0,.033,13),(.13,.26,.084,11)),
    "sweep": ((.27,1.7,0,14),(.33,.49,.04,21)),
    "stone_break": ((.48,.65,0,17),(.26,3.12,.018,21),(.18,1.25,.052,19)),
    "rewind": ((.29,1.33,0,12),(.20,1.67,.063,14),(.15,2.4,.136,10)),
    "focus": ((.34,.5,0,7),(.21,1.77,.07,9),(.12,2.07,.15,12)),
    "ignite": ((.31,2.3,0,15),(.25,1.1,.032,17),(.14,1.44,.084,14)),
    "detonate": ((.59,.75,0,11),(.27,2.22,.023,17),(.19,3.48,.074,21)),
    "shield_form": ((.24,1.33,0,13),(.31,1.78,.07,10),(.18,2.9,.14,12)),
    "shield_break": ((.43,1.18,0,29),(.25,1.41,.018,22),(.14,1.67,.04,24)),
    "seal": ((.24,.67,0,13),(.18,2.11,.022,11),(.26,1.53,.072,12)),
    "evolution": ((.24,1,0,12),(.23,1.25,.09,10),(.19,1.5,.18,10)),
    "synergy": ((.31,1,0,11),(.25,1.5,.12,11),(.24,2,.20,10)),
    "prism": ((.24,1,0,10),(.24,1.25,.09,10),(.24,1.5,.18,10),(.20,2,.27,10)),
    "ui": ((.3,1.25,0,26),(.24,2,.023,21)),
}


def voice(time, start, profile):
    total = 0
    for gain,ratio,onset,decay in profile:
        elapsed = time-onset
        if elapsed > 0:
            total += gain * math.sin(math.tau * start * ratio * elapsed) * min(1,elapsed*250) * math.exp(-decay*elapsed)
    return total

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
            signal = (carrier*(1-noise)*.22 + smoothed*noise*1.8) * env
            signal += .64 * voice(time,start,VOICES[name]) * min(1,(duration-time)*30)
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

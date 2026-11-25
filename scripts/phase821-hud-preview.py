#!/usr/bin/env python3
"""Draw an explicitly offline HUD walkthrough driven by the production queue."""
import argparse
import csv
import io
from pathlib import Path
import runpy
import subprocess
import tempfile
from PIL import Image

root = Path(__file__).resolve().parents[1]
helper = runpy.run_path(str(root/"scripts/visual-release-sheet.py"))
page,font,art = (helper[key] for key in ("page","font","art"))
args = argparse.ArgumentParser(description=__doc__)
args.add_argument("output",type=Path)
options = args.parse_args()
out = options.output
out.mkdir(parents=True,exist_ok=True)

source = r"""
import io.github.augmentwild.domain.HudMessages;
import io.github.augmentwild.domain.HudMessages.Kind;
import java.lang.management.ManagementFactory;

public class HudTrace {
    static void offer(HudMessages<String> hud,Kind kind,String id,long now) {
        hud.offer(kind,id,now,count -> id + (count == 1 ? "" : " ×" + count));
    }

    public static void main(String[] args) {
        var meters = new HudMessages.Metrics();
        var hud = new HudMessages<String>(meters);
        String previous = null;
        for (int ms = 0; ms <= 9000; ms += 50) {
            long now = ms * 1_000_000L;
            switch (ms) {
                case 0 -> offer(hud,Kind.PROC,"강철심장 발동",now);
                case 200 -> offer(hud,Kind.PROC,"강철심장 발동",now);
                case 250 -> offer(hud,Kind.PROC,"검풍 발동",now);
                case 300 -> offer(hud,Kind.PROC,"마법 미사일 발동",now);
                case 800 -> offer(hud,Kind.COMBAT,"피격 · 체력 −4 · 흡수 −2",now);
                case 3100 -> offer(hud,Kind.INPUT,"시간 역행 · 복귀 기록 대기",now);
                default -> { }
            }
            String next = hud.current(now, ms >= 600 && ms < 1400 ? "위험 · 경계 밖" : null);
            if (!java.util.Objects.equals(previous,next)) {
                System.out.println(ms + "," + (next == null ? "" : next));
                previous = next;
            }
        }
        System.out.println("9000,");
        System.err.println("shown="+meters.displayed()+" merged="+meters.merged()+" expired="+meters.expired());
        for (int repetition=0; repetition<4; repetition++) benchmark(repetition);
    }

    static void benchmark(int repetition) {
        var mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        var meters=new HudMessages.Metrics();
        var queues = java.util.stream.IntStream.range(0,16)
            .mapToObj(i -> new HudMessages<String>(meters)).toList();
        var samples=new long[1000];
        String[] previous = new String[16];
        long sends=0;
        long before=mx.getThreadAllocatedBytes(thread);
        for (int tick=0;tick<samples.length;tick++) {
            long now=tick*50_000_000L;
            long started=System.nanoTime();
            int player=0;
            for (var queue : queues) {
                if (tick%40==0) {
                    offer(queue,Kind.PROC,"steel",now);
                    offer(queue,Kind.PROC,"steel",now);
                    offer(queue,Kind.PROC,"blade",now);
                    offer(queue,Kind.COMBAT,"damage",now);
                }
                String message=queue.current(now,tick%80>=62 ? "border" : null);
                if (!java.util.Objects.equals(previous[player],message)) sends++;
                previous[player++]=message;
            }
            samples[tick]=System.nanoTime()-started;
        }
        long allocated=mx.getThreadAllocatedBytes(thread)-before;
        java.util.Arrays.sort(samples);
        System.err.printf(java.util.Locale.ROOT,
            "rep=%d 16 viewers p95_us=%.3f max_us=%.3f bytes=%d transitions=%d peak=%d merged=%d expired=%d budget=%d%n",
            repetition,samples[949]/1000d,samples[999]/1000d,allocated,sends,meters.peak(),meters.merged(),meters.expired(),meters.budget());
    }
}
"""
with tempfile.TemporaryDirectory() as temp:
    fixture=Path(temp)/"HudTrace.java"
    fixture.write_text(source)
    subprocess.run(["javac","-d",temp,str(fixture),
                    str(root/"src/main/java/io/github/augmentwild/domain/HudMessages.java")],check=True)
    trial=subprocess.run(["java","-cp",temp,"HudTrace"],check=True,text=True,capture_output=True)
rows=[(int(ms),label) for ms,label in csv.reader(io.StringIO(trial.stdout))]
(out/"timing.csv").write_text("ms,visible\n"+trial.stdout)
print(trial.stderr,end="")

assets = root/"resource-pack/pack/assets/augmentwild/textures/gui/skill_state.png"
marks=Image.open(assets).convert("RGBA")
icons=[art(root,"folio/"+key) for key in ("shadow_dash","bomb_alchemy","time_rewind")]
def frame(label, ms, new=True):
    image, draw=page("전투 HUD · "+("PART 2" if new else "PART 1"),
                    "오프라인 도식 · 배포 아이콘 및 실제 서버 알림 큐 샘플",760,300)
    draw.rounded_rectangle((32,101,728,262),radius=10,fill="#222c3a",outline="#657080")
    draw.line((380,177,380,194),fill="#637587")
    draw.line((371,186,389,186),fill="#637587")
    draw.rectangle((187,130,574,134),fill="#c5a36a" if new else "#775099")
    if new:
        for i,icon in enumerate(icons):
            x=240+i*100
            if not i:
                draw.rounded_rectangle((x-9,100,x+70,126),radius=3,outline="#f2d6a6")
            image.paste(icon.resize((18,18)),(x+10,104),icon.resize((18,18)))
            index=(0,1,2)[i]
            emblem=marks.crop((index*16,0,index*16+16,16))
            image.paste(emblem,(x+32,104),emblem)
            draw.text((x-1,104),str(i+1),font=font(11),fill="#efe8d8")
            if i==1:
                draw.text((x+51,104),"8초",font=font(11),fill="#efe8d8")
    else:
        draw.text((380,109),"[1 ✓] 2 ⌛ 8초   3 ×   ·   그림자 질주 · 사용 가능",
                  anchor="mm",font=font(13),fill="#efe8d8")
    draw.text((380,227),label,anchor="mm",font=font(19,True),fill="#ffcfa0" if "위험" in label else "#e5e8f0")
    draw.text((39,270),f"{ms/1000:.2f} s",font=font(15),fill="#9ab2c8")
    return image

images=[frame(label,ms) for ms,label in rows[:-1]]
durations=[rows[i+1][0]-value for i,(value,_) in enumerate(rows[:-1])]
images[0].save(out/"hud-timeline.gif",save_all=True,append_images=images[1:],loop=0,duration=durations,optimize=True)
old=frame("첫 증강 반복 표시",250,False)
new=frame("서로 다른 알림은 유효 시간 안에 순차 표시",250,True)
comparison=Image.new("RGB",(760,600))
comparison.paste(old,(0,0))
comparison.paste(new,(0,300))
comparison.save(out/"hud-comparison.png",optimize=True)

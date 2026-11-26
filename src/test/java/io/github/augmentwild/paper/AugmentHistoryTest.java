package io.github.augmentwild.paper;

import static org.junit.jupiter.api.Assertions.*;
import io.github.augmentwild.domain.AugmentTelemetry;
import io.github.augmentwild.domain.AugmentTelemetry.Metric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AugmentHistoryTest {
    @TempDir Path directory;

    @Test void duplicateMatchDoesNotOverwritePreviouslySavedStats() throws Exception {
        UUID match = UUID.randomUUID();
        var stats = new AugmentTelemetry.Snapshot(Map.of("steel_heart",Map.of(Metric.OFFERED,2d,Metric.PICKED,1d)),Map.of());
        Logger logger = Logger.getAnonymousLogger();
        try (var history = new AugmentHistory(directory,logger,90)) {
            history.append(match,2,stats).get(5,TimeUnit.SECONDS);
            history.append(match,2,new AugmentTelemetry.Snapshot(Map.of(),Map.of())).get(5,TimeUnit.SECONDS);
            assertTrue(String.join("\n",history.report("steel_heart","8.2")).contains("50.0%"));
            assertTrue(history.summary().contains("완료 경기 1"));
        }
        try (var recovered = new AugmentHistory(directory,logger,90)) {
            recovered.append(match,2,stats).get(5,TimeUnit.SECONDS);
            assertTrue(Files.readString(directory.resolve(match+".tsv")).contains("steel_heart\tPICKED\t1.0"));
            assertTrue(recovered.summary().contains("완료 경기 1"));
            assertTrue(recovered.report("steel_heart","0.0").getFirst().contains("데이터 없음"));
        }
    }

    @Test void isolatesVersionsAndMatchesPicksToTheirOfferRound() throws Exception {
        UUID match = UUID.randomUUID();
        var rows = Map.of("steel_heart",Map.of(Metric.OFFERED,5d,Metric.PICKED,2d,Metric.OWNED,1d,
                Metric.WON,1d,Metric.EVOLUTION_ELIGIBLE,1d,Metric.EVOLVED,1d));
        var rounds = Map.of("steel_heart/GOLD/1",3L,"steel_heart/GOLD/2",2L,
                "pick/steel_heart/GOLD/1",1L,"pick/steel_heart/GOLD/2",1L);
        try (var history = new AugmentHistory(directory,Logger.getAnonymousLogger(),90)) {
            history.append(match,16,new AugmentTelemetry.Snapshot(rows,rounds),"8.2.1").get(4,TimeUnit.SECONDS);
            var filter = AugmentHistory.Filter.parse(new String[]{"players=16","days=3","round=2"},0);
            var report = String.join("\n",history.report("steel_heart",filter));
            assertTrue(report.contains("2회차 50.0% (1/2)"));
            assertTrue(report.contains("진화율 100.0%"));
            assertTrue(history.report("steel_heart","8.2").getFirst().contains("데이터 없음"));
            assertTrue(history.report("steel_heart",new AugmentHistory.Filter("8.2.1",4,3,2))
                    .getFirst().contains("데이터 없음"));
            assertThrows(IllegalArgumentException.class,() -> AugmentHistory.Filter.parse(new String[]{"round=2","round=3"},0));
        }
    }

}

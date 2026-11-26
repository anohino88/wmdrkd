package io.github.augmentwild.paper;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MatchArchiveTest {
    @TempDir Path folder;
    private static final Logger LOG = Logger.getAnonymousLogger();

    private static MatchArchive.Result result(UUID id, UUID owner, Instant ended,int kills) {
        var p = new MatchArchive.PlayerResult(owner,1,kills,2,1,224,23,12,7,5,6,true,3,1,
                List.of("steel_heart"),List.of("진화 · steel_heart"));
        return new MatchArchive.Result(id,"0.1.0-SNAPSHOT","abc",ended.minusSeconds(224),ended,MatchArchive.Mode.NORMAL,
                MatchArchive.Outcome.WIN,owner,224,Map.of(owner,p),Map.of("premium-supply.claim_ms",4500L));
    }

    private static MatchArchive.Result read(MatchArchive archive, UUID match,UUID viewer,boolean admin) throws Exception {
        var future = new CompletableFuture<MatchArchive.Result>();
        assertTrue(archive.lookup(match,viewer,admin,future::complete));
        return future.get(4,TimeUnit.SECONDS);
    }

    @Test void savesOnceAndEnforcesParticipantAuthorizationAfterRestart() throws Exception {
        var saved = result(UUID.randomUUID(),UUID.randomUUID(),Instant.now(),3);
        try (var archive = new MatchArchive(folder,90,LOG)) {
            assertTrue(archive.append(saved));
            assertTrue(archive.append(result(saved.id(),saved.winner(),saved.ended(),99)));
            assertEquals(saved,read(archive,saved.id(),saved.winner(),false));
            assertNull(read(archive,saved.id(),UUID.randomUUID(),false));
        }
        try (var archive = new MatchArchive(folder,90,LOG)) {
            assertEquals(saved,read(archive,saved.id(),null,true));
            var entries = archive.list(saved.winner(),0,9);
            assertEquals(saved.id(),entries.getFirst().id());
            assertTrue(archive.list(UUID.randomUUID(),0,9).isEmpty());
        }
    }

    @Test void retryAfterWriteFailureAndPreserveCorruptFiles() throws Exception {
        var directory = Files.writeString(folder.resolve("occupied"),"not a directory");
        var saved = result(UUID.randomUUID(),UUID.randomUUID(),Instant.now(),3);
        try (var archive = new MatchArchive(directory,90,LOG)) {
            archive.append(saved);
            assertNull(read(archive,saved.id(),saved.winner(),false));
            assertTrue(archive.status().contains("미저장 1"));
            Files.delete(directory);
            Files.createDirectory(directory);
            Files.writeString(directory.resolve(UUID.randomUUID()+".tsv"),"corrupt");
            archive.retry();
            assertEquals(saved,read(archive,saved.id(),saved.winner(),false));
            try (var files = Files.list(directory.resolve("quarantine"))) {
                assertEquals("corrupt",Files.readString(files.findFirst().orElseThrow()));
            }
        }
    }

    @Test void agesOutExpiredResultsAndQuarantinesOversizedFiles() throws Exception {
        var old = result(UUID.randomUUID(),UUID.randomUUID(),Instant.now().minusSeconds(4*86_400),3);
        try (var archive = new MatchArchive(folder,90,LOG)) {
            archive.append(old);
            assertEquals(old,read(archive,old.id(),old.winner(),false));
        }
        var oversized = folder.resolve(UUID.randomUUID()+".tsv");
        Files.write(oversized,new byte[512_001]);
        try (var archive = new MatchArchive(folder,2,LOG)) {
            assertNull(read(archive,old.id(),old.winner(),false));
            assertFalse(Files.exists(folder.resolve(old.id()+".tsv")));
            assertFalse(Files.exists(oversized));
        }
    }
}

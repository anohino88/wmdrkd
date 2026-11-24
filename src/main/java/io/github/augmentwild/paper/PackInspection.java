package io.github.augmentwild.paper;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;
import javax.net.ssl.HttpsURLConnection;

/** Checks a configured download independently of the client's resource-pack status. */
final class PackInspection {
    private static final int MAX_DOWNLOAD = 32 * 1024 * 1024;
    private static final int MAX_EXPANDED = 64 * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;
    private static final String ASSETS = "assets/augmentwild/";
    private static final Set<String> REQUIRED = Set.of("pack.mcmeta",
            ASSETS + "items/augment/steel_heart.json", ASSETS + "models/item/augment/steel_heart.json",
            ASSETS + "textures/item/augment/steel_heart.png", ASSETS + "items/fx/crescent.json",
            ASSETS + "font/skills.json", ASSETS + "sounds.json");

    record Archive(String release,int entries) {}

    static URI directUri(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443
                || (uri.getHost().equalsIgnoreCase("github.com") && uri.getPath().contains("/actions/"))) {
            throw new IllegalArgumentException("art.url에 인증 정보 없는 직접 HTTPS ZIP 주소를 지정하세요.");
        }
        return uri;
    }

    private static void publicHost(URI uri) throws IOException {
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            // Exclude local, special-purpose and private ranges, including IPv6 ULA.
            byte[] bytes = address.getAddress();
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean reserved = bytes.length == 4
                    ? first == 0 || (first == 100 && second >= 64 && second < 128)
                    : (first & 0xfe) == 0xfc;
            if (reserved || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IOException("공개 인터넷 호스트만 서버에서 검증할 수 있습니다.");
            }
        }
    }

    static String download(URI uri,String expectedHash) throws IOException {
        publicHost(uri);
        var connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent","AugmentWild/8.1 (resource pack validation)");
        Path archive = Files.createTempFile("augmentwild-pack-", ".zip");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status + " · 리디렉션 없이 ZIP을 반환하는 URL을 사용하세요.");
            long length = connection.getContentLengthLong();
            if (length > MAX_DOWNLOAD) throw new IOException("ZIP 크기 상한 초과");
            var hash = sha1();
            long bytes = 0;
            long started = System.nanoTime();
            try (var input = connection.getInputStream(); var output = Files.newOutputStream(archive)) {
                byte[] buffer = new byte[8192];
                for (int n; (n = input.read(buffer)) != -1;) {
                    bytes += n;
                    if (bytes > MAX_DOWNLOAD || System.nanoTime() - started > 20_000_000_000L)
                        throw new IOException("다운로드 크기 또는 시간 상한 초과");
                    hash.update(buffer,0,n);
                    output.write(buffer,0,n);
                }
            }
            if (length >= 0 && length != bytes) throw new IOException("HTTP 200 · 다운로드 크기 불일치");
            if (!HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(expectedHash))
                throw new IOException("HTTP 200 · " + bytes + " B · SHA-1 불일치");
            Archive verified = inspect(archive);
            return "HTTP 200 · " + bytes + " B · SHA-1 일치 · 97.1 · "
                    + verified.release() + " · ZIP " + verified.entries() + " 항목";
        } finally {
            connection.disconnect();
            Files.deleteIfExists(archive);
        }
    }

    private static MessageDigest sha1() {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static Archive inspect(Path path) throws IOException {
        try (var zip = new ZipFile(path.toFile())) {
            if (zip.size() > MAX_ENTRIES) throw new IOException("ZIP 항목 상한 초과");
            var names = new HashSet<String>();
            byte[] buffer = new byte[8192];
            long expanded = 0;
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!names.add(name) || name.startsWith("/") || name.indexOf('\\') >= 0
                        || java.util.List.of(name.split("/")).contains("..")) throw new IOException("ZIP 경로 오류");
                if (entry.isDirectory()) continue;
                var crc = new CRC32();
                long size = 0;
                try (var input = zip.getInputStream(entry)) {
                    for (int n; (n = input.read(buffer)) != -1;) {
                        size += n;
                        expanded += n;
                        if (expanded > MAX_EXPANDED) throw new IOException("ZIP 압축 해제 상한 초과");
                        crc.update(buffer,0,n);
                    }
                }
                if (crc.getValue() != entry.getCrc() || size != entry.getSize()) throw new IOException("ZIP CRC 또는 항목 크기 불일치");
            }
            if (!REQUIRED.stream().allMatch(name -> names.contains(name) && !zip.getEntry(name).isDirectory()))
                throw new IOException("ZIP 루트의 pack.mcmeta 또는 필수 augmentwild 에셋 누락");
            var metadata = JsonParser.parseString(smallText(zip,"pack.mcmeta")).getAsJsonObject().getAsJsonObject("pack");
            if (!metadata.get("min_format").toString().equals("[97,1]") || !metadata.get("max_format").toString().equals("[97,1]"))
                throw new IOException("Paper 26.3용 pack format 97.1이 아닙니다.");
            inspectReferences(zip,names);
            String release = "레거시 ZIP · 릴리스 표식 없음";
            if (names.contains("augmentwild-pack.json")) {
                release = JsonParser.parseString(smallText(zip,"augmentwild-pack.json")).getAsJsonObject().get("release").getAsString();
                if (!release.matches("[a-zA-Z0-9._-]{1,32}")) throw new IOException("팩 릴리스 표식 오류");
            }
            return new Archive(release,zip.size());
        } catch (RuntimeException invalid) {
            throw new IOException("팩 메타데이터 읽기 실패",invalid);
        }
    }

    private static void inspectReferences(ZipFile zip,Set<String> names) throws IOException {
        int augments = 0;
        for (String name : names) {
            if (!name.startsWith(ASSETS + "items/") || !name.endsWith(".json")) continue;
            if (name.startsWith(ASSETS + "items/augment/")) augments++;
            var item = JsonParser.parseString(smallText(zip,name)).getAsJsonObject().getAsJsonObject("model");
            if (!item.get("type").getAsString().equals("minecraft:model")) throw new IOException("아이템 모델 형식 오류");
            String modelPath = reference(item.get("model").getAsString(),"models/",".json");
            if (!names.contains(modelPath)) throw new IOException("아이템 모델 참조 누락 · " + modelPath);
            var model = JsonParser.parseString(smallText(zip,modelPath)).getAsJsonObject();
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String path = reference(texture.getValue().getAsString(),"textures/",".png");
                if (!names.contains(path)) throw new IOException("모델 텍스처 누락 · " + path);
            }
        }
        if (augments != 84) throw new IOException("84개 증강 아이템 정의가 필요합니다.");
        var font = JsonParser.parseString(smallText(zip,ASSETS + "font/skills.json")).getAsJsonObject().getAsJsonArray("providers");
        if (font.size() < 7 || font.size() > 8) throw new IOException("스킬 글리프와 상태 표식 정의 수 오류");
        var glyphs = new java.util.HashSet<Integer>();
        for (var entry : font) {
            var provider = entry.getAsJsonObject();
            int height = provider.get("height").getAsInt();
            int ascent = provider.get("ascent").getAsInt();
            if (!provider.get("type").getAsString().equals("bitmap") || height < 1 || height > 8 || ascent < 0 || ascent > height
                    || !names.contains(reference(provider.get("file").getAsString(),"textures/","")))
                throw new IOException("스킬 글리프 크기 또는 텍스처 참조 오류");
            String file = provider.get("file").getAsString();
            boolean status = file.equals("augmentwild:gui/skill_state.png");
            var rows = provider.getAsJsonArray("chars");
            if (rows.size() != 1 || rows.get(0).getAsString().length() != (status ? 4 : 1))
                throw new IOException("스킬 글리프 행 크기 오류");
            for (int code : rows.get(0).getAsString().chars().toArray()) {
                if ((status ? code < 0xe110 || code > 0xe113 : code < 0xe100 || code > 0xe106) || !glyphs.add(code))
                    throw new IOException("스킬 글리프 누락 또는 중복");
            }
        }
        if (glyphs.size() != (font.size() == 8 ? 11 : 7)) throw new IOException("스킬 글리프 집합 불일치");
        var sounds = JsonParser.parseString(smallText(zip,ASSETS + "sounds.json")).getAsJsonObject();
        if (!sounds.keySet().containsAll(Set.of("cast","evolve","supply"))) throw new IOException("기본 효과음 정의 누락");
        for (String effect : sounds.keySet()) {
            for (var sound : sounds.getAsJsonObject(effect).getAsJsonArray("sounds")) {
                if (!names.contains(reference(sound.getAsString(),"sounds/",".ogg"))) throw new IOException("효과음 참조 누락");
            }
        }
    }

    private static String reference(String value,String folder,String extension) throws IOException {
        if (!value.matches("augmentwild:[a-z0-9_./-]+")) throw new IOException("잘못된 augmentwild 네임스페이스 참조");
        return ASSETS + folder + value.substring("augmentwild:".length()) + extension;
    }

    private static String smallText(ZipFile zip,String name) throws IOException {
        try (var input = zip.getInputStream(zip.getEntry(name))) {
            byte[] bytes = input.readNBytes(24577);
            if (bytes.length > 24576) throw new IOException("메타데이터 크기 상한 초과");
            return new String(bytes,StandardCharsets.UTF_8);
        }
    }
}

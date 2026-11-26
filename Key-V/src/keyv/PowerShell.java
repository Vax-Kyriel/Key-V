package keyv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PowerShell {
    List<String> run(String scriptName, Map<String, String> env, Duration timeout) throws IOException, InterruptedException {
        String script = Scripts.load(scriptName);
        Path out = Files.createTempFile("keyv-", ".out");
        Path ps1 = Files.createTempFile("keyv-", ".ps1");
        try {
            writeUtf8Bom(ps1, script);
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden",
                    "-File", ps1.toString());
            builder.environment().put("KEYV_OUT", out.toString());
            builder.environment().putAll(env);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            ByteArrayOutputStream console = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(console);
                } catch (IOException ignored) {
                    // Le flux se ferme quand le processus se termine ou est interrompu.
                }
            }, "keyv-powershell");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(1000);
                throw new IOException("PowerShell n'a pas répondu à temps.");
            }
            reader.join(1000);
            byte[] consoleBytes = console.toByteArray();
            List<String> lines = readLines(out);
            if (lines.stream().noneMatch(line -> line.startsWith("OK") || line.startsWith("ERR\t")) && consoleBytes.length > 0) {
                String consoleText = decode(consoleBytes).trim();
                if (!consoleText.isEmpty()) {
                    lines.add("ERR\t" + consoleText.replace("\r", " ").replace("\n", " "));
                }
            }
            return lines;
        } finally {
            Files.deleteIfExists(ps1);
            Files.deleteIfExists(out);
        }
    }

    private static void writeUtf8Bom(Path path, String script) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        buffer.write(script.getBytes(StandardCharsets.UTF_8));
        Files.write(path, buffer.toByteArray());
    }

    private static List<String> readLines(Path path) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return new ArrayList<>();
        }
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String decode(byte[] raw) {
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) {
            return new String(raw, StandardCharsets.UTF_16LE);
        }
        int zeros = 0;
        for (byte value : raw) {
            if (value == 0) {
                zeros++;
            }
        }
        if (raw.length > 4 && zeros > raw.length / 4) {
            return new String(raw, StandardCharsets.UTF_16LE);
        }
        return new String(raw, StandardCharsets.UTF_8);
    }
}

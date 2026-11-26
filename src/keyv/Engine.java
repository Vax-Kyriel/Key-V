package keyv;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Engine {
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(40);
    private static final Duration ACTION_TIMEOUT = Duration.ofSeconds(90);

    private final PowerShell powerShell = new PowerShell();
    private final SecureBootMemory memory;

    Engine() throws IOException {
        memory = new SecureBootMemory();
    }

    Snapshot list() throws IOException, InterruptedException {
        return parseSnapshot(powerShell.run("list.ps1", Map.of(), LIST_TIMEOUT));
    }

    ActionResult connect(UsbDisk disk, VirtualMachine vm, boolean disableSecureBoot, boolean start)
            throws IOException, InterruptedException {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("KEYV_DISK", Integer.toString(disk.number()));
        env.put("KEYV_VM", vm.name());
        env.put("KEYV_DISABLE_SB", disableSecureBoot ? "1" : "0");
        env.put("KEYV_START", start ? "1" : "0");
        ActionResult result = parseAction(powerShell.run("attach.ps1", env, ACTION_TIMEOUT));
        if (result.ok() && "On".equals(result.previousSecureBoot())) {
            memory.remember(vm.name(), result.previousSecureBoot(), result.previousTemplate());
        }
        return result;
    }

    ActionResult release(UsbDisk disk, VirtualMachine vm) throws IOException, InterruptedException {
        SecureBootMemory.Previous previous = memory.previous(vm.name());
        Map<String, String> env = new LinkedHashMap<>();
        env.put("KEYV_DISK", Integer.toString(disk.number()));
        env.put("KEYV_VM", vm.name());
        if (previous != null && "On".equals(previous.state())) {
            env.put("KEYV_RESTORE_SB", "On");
            env.put("KEYV_RESTORE_TEMPLATE", previous.template() == null ? "" : previous.template());
        }
        ActionResult result = parseAction(powerShell.run("detach.ps1", env, ACTION_TIMEOUT));
        if (result.ok()) {
            memory.clear(vm.name());
        }
        return result;
    }

    ActionResult stop(VirtualMachine vm) throws IOException, InterruptedException {
        return parseAction(powerShell.run("stop.ps1", Map.of("KEYV_VM", vm.name()), ACTION_TIMEOUT));
    }

    ActionResult console(VirtualMachine vm) throws IOException, InterruptedException {
        return parseAction(powerShell.run("console.ps1", Map.of("KEYV_VM", vm.name()), LIST_TIMEOUT));
    }

    ActionResult startService() throws IOException, InterruptedException {
        return parseAction(powerShell.run("service.ps1", Map.of(), LIST_TIMEOUT));
    }

    ActionResult elevate() throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElse("javaw.exe");
        String classpath = System.getProperty("java.class.path");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("KEYV_JAVA", java);
        env.put("KEYV_ARGS", "-cp \"" + classpath + "\" keyv.KeyVApp");
        env.put("KEYV_WORKDIR", System.getProperty("user.dir"));
        return parseAction(powerShell.run("elevate.ps1", env, Duration.ofSeconds(20)));
    }

    private static Snapshot parseSnapshot(List<String> lines) {
        boolean admin = false;
        String hyperv = "missing";
        String edition = "";
        List<UsbDisk> disks = new ArrayList<>();
        List<VirtualMachine> vms = new ArrayList<>();
        String error = null;
        for (String line : lines) {
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "META" -> {
                    if (parts.length >= 3 && "EDITION".equals(parts[1])) {
                        edition = parts[2];
                    } else if (parts.length >= 3 && "ADMIN".equals(parts[1])) {
                        admin = "true".equals(parts[2]);
                    } else if (parts.length >= 3 && "HYPERV".equals(parts[1])) {
                        hyperv = parts[2];
                    }
                }
                case "USB" -> disks.add(parseDisk(parts));
                case "VM" -> vms.add(parseVm(parts));
                case "ERR" -> error = Messages.error(joinFrom(parts, 1));
                default -> {
                }
            }
        }
        if (error == null && lines.stream().noneMatch(line -> line.equals("OK") || line.startsWith("OK\t"))) {
            error = "Hyper-V n'a pas répondu.";
        }
        return new Snapshot(admin, hyperv, edition, List.copyOf(disks), List.copyOf(vms), List.of(), error);
    }

    private static UsbDisk parseDisk(String[] parts) {
        int number = Integer.parseInt(parts[1]);
        boolean ventoy = "true".equals(field(parts, 5));
        String style = field(parts, 8);
        boolean gpt = "GPT".equalsIgnoreCase(style);
        boolean bootable = ventoy || gpt || "true".equals(field(parts, 9));
        return new UsbDisk(
                number,
                field(parts, 2),
                field(parts, 3),
                field(parts, 4),
                ventoy,
                field(parts, 6),
                field(parts, 7),
                style,
                bootable);
    }

    private static VirtualMachine parseVm(String[] parts) {
        List<Integer> disks = new ArrayList<>();
        String owned = field(parts, 4);
        if (!owned.isBlank()) {
            for (String item : owned.split(",")) {
                if (!item.isBlank()) {
                    disks.add(Integer.parseInt(item.trim()));
                }
            }
        }
        return new VirtualMachine(field(parts, 1), field(parts, 2), Integer.parseInt(field(parts, 3)), List.copyOf(disks));
    }

    private static ActionResult parseAction(List<String> lines) {
        List<String> logs = new ArrayList<>();
        String previousSecureBoot = null;
        String previousTemplate = null;
        String error = null;
        for (String line : lines) {
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "LOG" -> logs.add(Messages.log(field(parts, 1), List.of(parts).subList(Math.min(2, parts.length), parts.length)));
                case "PREVSB" -> {
                    previousSecureBoot = field(parts, 1);
                    previousTemplate = joinFrom(parts, 2);
                }
                case "ERR" -> error = Messages.error(joinFrom(parts, 1));
                default -> {
                }
            }
        }
        if (error == null && lines.stream().noneMatch(line -> line.equals("OK") || line.startsWith("OK\t"))) {
            error = "Hyper-V n'a pas répondu.";
        }
        return new ActionResult(List.copyOf(logs), previousSecureBoot, previousTemplate, error);
    }

    private static String field(String[] parts, int index) {
        return index < parts.length ? parts[index] : "";
    }

    private static String joinFrom(String[] parts, int start) {
        if (start >= parts.length) {
            return "";
        }
        return String.join("\t", List.of(parts).subList(start, parts.length));
    }
}

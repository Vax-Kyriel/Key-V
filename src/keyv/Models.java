package keyv;

import java.util.List;

record UsbDisk(
        int number,
        String sizeGb,
        String name,
        String status,
        boolean ventoy,
        String labels,
        String letters,
        String partitionStyle,
        boolean bootable) {
    boolean accepted() {
        return ventoy || bootable;
    }
}

record VirtualMachine(String name, String state, int generation, List<Integer> physicalDisks) {
    boolean isOff() {
        return "Off".equals(state);
    }

    boolean owns(int diskNumber) {
        return physicalDisks.contains(diskNumber);
    }
}

record Snapshot(
        boolean admin,
        String hyperv,
        String edition,
        List<UsbDisk> disks,
        List<VirtualMachine> vms,
        List<String> logs,
        String error) {
}

record ActionResult(List<String> logs, String previousSecureBoot, String previousTemplate, String error) {
    boolean ok() {
        return error == null;
    }
}

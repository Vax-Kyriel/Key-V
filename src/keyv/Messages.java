package keyv;

import java.util.List;

final class Messages {
    private Messages() {
    }

    static String log(String code, List<String> args) {
        boolean en = Lang.english();
        return switch (code) {
            case "OFFLINE" -> en
                    ? "The key goes offline. Explorer hides it while the VM is using it."
                    : "La clé passe hors ligne. Elle quitte l'Explorateur tant qu'elle est dans la VM.";
            case "STILL_ONLINE" -> en
                    ? "Windows left this bootable key online. Key-V still attaches it to the VM."
                    : "Windows a laissé cette clé démarrable en ligne. Key-V la branche quand même sur la VM.";
            case "SB_OFF" -> en
                    ? "Secure Boot is off. Ventoy usually does not start with Hyper-V Secure Boot."
                    : "Secure Boot désactivé. Ventoy ne démarre en général pas avec le Secure Boot Hyper-V.";
            case "ATTACHED_SCSI" -> en
                    ? "Key attached as SCSI and set as the first boot device."
                    : "Clé attachée en SCSI et placée en premier démarrage.";
            case "ATTACHED_GPT" -> en
                    ? "GPT key attached as SCSI and set first. The generation 2 VM boots from it."
                    : "Clé GPT branchée en SCSI et placée en premier. La VM génération 2 démarre dessus.";
            case "ATTACHED_GPT_IDE" -> en
                    ? "GPT key attached at IDE 0:0. The generation 1 VM boots from it."
                    : "Clé GPT branchée en IDE 0:0. La VM génération 1 démarre dessus.";
            case "ATTACHED_IDE" -> en
                    ? "Key attached at IDE 0:0. A generation 1 VM boots from it."
                    : "Clé attachée en IDE 0:0. La VM génération 1 démarre dessus.";
            case "ATTACHED_SCSI_GEN1" -> en
                    ? "IDE refused the key, so it is attached on SCSI. A generation 1 VM can see it. It boots from IDE, not from SCSI."
                    : "L'IDE a refusé la clé, elle est donc branchée en SCSI. La VM génération 1 la voit. Elle démarre sur l'IDE, pas sur le SCSI.";
            case "BIOS_KEPT" -> en
                    ? "The key is on IDE 0:0. The BIOS boot order was left as it is: " + arg(args, 0)
                    : "La clé est en IDE 0:0. L'ordre BIOS est resté tel quel : " + arg(args, 0);
            case "BOOT_SKIPPED" -> en
                    ? "The key is attached. Firmware did not put it first: " + arg(args, 0)
                    : "La clé est branchée. Le micrologiciel ne l'a pas mise en premier : " + arg(args, 0);
            case "MBR_GEN2" -> en
                    ? "This key is not GPT. It is still attached. A generation 2 VM boots it only when Ventoy is installed as GPT."
                    : "Cette clé n'est pas en GPT. Elle est quand même branchée. Une VM génération 2 ne démarre dessus que si Ventoy est installé en GPT.";
            case "SAVED_CLEARED" -> en
                    ? "Saved state removed so the key can be attached."
                    : "État enregistré retiré pour pouvoir brancher la clé.";
            case "DVD_MOVED" -> en
                    ? "DVD moved off IDE 0:0 so the key can boot."
                    : "DVD déplacé hors de IDE 0:0 pour que la clé puisse démarrer.";
            case "MOVED_VHD" -> en
                    ? "Virtual disk moved to IDE " + arg(args, 0) + "."
                    : "Disque virtuel déplacé vers IDE " + arg(args, 0) + ".";
            case "VHD_REATTACH_FAIL" -> en
                    ? "The key is at IDE 0:0, but the virtual disk could not be reattached: " + arg(args, 0)
                    : "La clé est en IDE 0:0, mais le disque virtuel n'a pas pu être rebranché : " + arg(args, 0);
            case "CHECKPOINT" -> en
                    ? "Checkpoints are unavailable while this key is attached."
                    : "Les points de contrôle sont indisponibles tant que cette clé est attachée.";
            case "VM_STARTED" -> en ? "VM started." : "VM démarrée.";
            case "CONSOLE_OPENED" -> en ? "Hyper-V console opened." : "Console Hyper-V ouverte.";
            case "NO_VMCONNECT" -> en
                    ? "vmconnect.exe was not found. Open the VM from Hyper-V Manager."
                    : "vmconnect.exe est introuvable. Ouvre la VM depuis le Gestionnaire Hyper-V.";
            case "START_FAIL" -> en
                    ? "The key is attached, but the VM did not start: " + arg(args, 0)
                    : "La clé est attachée, mais la VM n'a pas démarré : " + arg(args, 0);
            case "DETACHED" -> en
                    ? "Key detached. It is visible in Windows again."
                    : "Clé détachée. Elle est de nouveau visible dans Windows.";
            case "ONLINE_FAIL" -> en
                    ? "The key left the VM, but Windows did not bring it back online. Unplug it and plug it back in if needed."
                    : "Clé retirée de la VM, mais Windows ne l'a pas remise en ligne. Débranche-la et rebranche-la si besoin.";
            case "SB_ON" -> en
                    ? "Secure Boot turned back on (" + arg(args, 0) + ")."
                    : "Secure Boot réactivé (" + arg(args, 0) + ").";
            case "SB_RESTORE_FAIL" -> en
                    ? "The key is detached, but Secure Boot was not restored: " + arg(args, 0)
                    : "La clé est détachée, mais le Secure Boot n'a pas été réactivé : " + arg(args, 0);
            case "VHD_RESTORED" -> en ? "Virtual disk returned to IDE 0:0." : "Disque virtuel remis en IDE 0:0.";
            case "VHD_RESTORE_FAIL" -> en
                    ? "The key is back in Windows, but the virtual disk did not return to IDE 0:0: " + arg(args, 0)
                    : "La clé est rendue à Windows, mais le disque virtuel n'est pas revenu en IDE 0:0 : " + arg(args, 0);
            case "SERVICE_STARTED" -> en ? "Hyper-V service started." : "Service Hyper-V démarré.";
            case "VM_STOPPED" -> en ? "VM stopped." : "VM éteinte.";
            case "ALREADY_OFF" -> en ? "The VM is already off." : "La VM est déjà arrêtée.";
            default -> code + (args.isEmpty() ? "" : " " + String.join(" ", args));
        };
    }

    static String error(String raw) {
        int tab = raw.indexOf('\t');
        String code = tab < 0 ? raw : raw.substring(0, tab);
        String extra = tab < 0 ? "" : raw.substring(tab + 1);
        boolean en = Lang.english();
        return switch (code) {
            case "NO_HYPERV" -> en
                    ? "Hyper-V is not installed. After moving to Windows Pro: Windows Features, turn on Hyper-V, then restart."
                    : "Hyper-V n'est pas installé. Après le passage à Windows Pro : Fonctionnalités Windows, coche Hyper-V, puis redémarre.";
            case "HYPERV_STOPPED" -> en ? "The Hyper-V service is stopped." : "Le service Hyper-V est arrêté.";
            case "NO_VM" -> en ? "Choose a virtual machine." : "Choisis une machine virtuelle.";
            case "BAD_DISK" -> en ? "Invalid disk number." : "Numéro de disque invalide.";
            case "NOT_USB" -> en ? "Only a USB disk can be attached." : "Seul un disque USB peut être attaché.";
            case "IS_SYSTEM" -> en ? "This is a system disk. Key-V leaves it alone." : "Ce disque est un disque système. Key-V ne le touche pas.";
            case "VM_NOT_OFF" -> en ? "The VM must be fully turned off." : "La VM doit être complètement arrêtée.";
            case "NOT_GPT" -> en
                    ? "This key is not GPT. A generation 2 VM boots with UEFI: reinstall Ventoy as GPT, or use a generation 1 VM."
                    : "Cette clé n'est pas en GPT. Une VM génération 2 démarre en UEFI : réinstalle Ventoy en GPT, ou utilise une VM génération 1.";
            case "NOT_ADDED" -> en ? "Hyper-V did not accept the physical disk." : "Hyper-V n'a pas accepté le disque physique.";
            case "IN_USE" -> en
                    ? "Windows is still using the key. Close any windows open on it, then try again."
                    : "Windows utilise encore la clé. Ferme les fenêtres ouvertes dessus, puis réessaie.";
            case "ACCESS_DENIED" -> en ? "Access denied. Start Key-V as administrator." : "Accès refusé. Relance Key-V en administrateur.";
            case "ALREADY_ATTACHED" -> en ? "This key is already attached to: " + extra : "Cette clé est déjà attachée à : " + extra;
            case "NOT_ATTACHED" -> en ? "This key is not attached to this VM." : "Cette clé n'est pas attachée à cette VM.";
            case "IDE_DVD" -> en
                    ? "A DVD occupies IDE 0:0. Remove it in the VM settings, then try again."
                    : "Un DVD occupe IDE 0:0. Retire-le dans les paramètres de la VM, puis réessaie.";
            case "IDE_PHYSICAL" -> en ? "IDE 0:0 is already a physical disk." : "IDE 0:0 est déjà un disque physique.";
            case "IDE_FULL" -> en
                    ? "No free IDE slot to keep the virtual disk and boot from the key."
                    : "Pas d'emplacement IDE libre pour garder le disque virtuel et démarrer sur la clé.";
            case "REMOVABLE" -> en
                    ? "Hyper-V refused this key on IDE and on SCSI. Take it offline in Disk Management, then try again."
                    : "Hyper-V a refusé cette clé en IDE et en SCSI. Passe-la hors ligne dans la Gestion des disques, puis réessaie.";
            case "CHECKPOINTS" -> en
                    ? "This VM has checkpoints. Delete them in Hyper-V, then connect the key again."
                    : "Cette VM a des points de contrôle. Supprime-les dans Hyper-V, puis reconnecte la clé.";
            case "SAVED_STATE" -> en
                    ? "The VM is saved. Start it and shut down the guest, or delete the saved state in Hyper-V."
                    : "La VM est en veille enregistrée. Démarre-la puis arrête le système invité, ou supprime l'état enregistré dans Hyper-V.";
            case "ELEVATION_CANCELLED" -> en ? "The administrator prompt was cancelled." : "La demande administrateur a été annulée.";
            default -> raw;
        };
    }

    static String state(String state) {
        boolean en = Lang.english();
        return switch (state) {
            case "Off" -> en ? "Off" : "Arrêtée";
            case "Running" -> en ? "Running" : "En cours";
            case "Saved" -> en ? "Saved" : "Enregistrée";
            case "Paused" -> en ? "Paused" : "En pause";
            case "Starting" -> en ? "Starting" : "Démarrage";
            case "Stopping" -> en ? "Stopping" : "Arrêt";
            case null -> "";
            default -> state;
        };
    }

    static boolean homeEdition(String edition) {
        return edition != null && (edition.equals("Core") || edition.startsWith("Core"));
    }

    private static String arg(List<String> args, int index) {
        return index < args.size() ? args.get(index) : "";
    }
}

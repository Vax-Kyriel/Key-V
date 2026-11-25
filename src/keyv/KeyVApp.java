package keyv;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

final class KeyVApp {
    private final Engine engine;
    private final DefaultListModel<UsbDisk> diskModel = new DefaultListModel<>();
    private final DefaultListModel<VirtualMachine> vmModel = new DefaultListModel<>();
    private final JList<UsbDisk> disks = new JList<>(diskModel);
    private final JList<VirtualMachine> vms = new JList<>(vmModel);
    private final JLabel banner = new JLabel(" ");
    private final JLabel subtitle = new JLabel(" ");
    private final JLabel diskTitle = new JLabel(" ");
    private final JLabel diskHint = new JLabel(" ");
    private final JLabel vmTitle = new JLabel(" ");
    private final JLabel vmHint = new JLabel(" ");
    private final JLabel journalTitle = new JLabel(" ");
    private final LangChip frenchChip = new LangChip(Lang.Code.FR, () -> useLanguage(Lang.Code.FR));
    private final LangChip englishChip = new LangChip(Lang.Code.EN, () -> useLanguage(Lang.Code.EN));
    private final JTextArea journal = new JTextArea(7, 40);
    private final JCheckBox secureBootBox = new JCheckBox("Couper le Secure Boot le temps du démarrage Ventoy", true);
    private final JCheckBox startBox = new JCheckBox("Démarrer la VM et ouvrir la console", true);
    private final SoftButton connectButton = new SoftButton("Connecter la clé", SoftButton.Kind.GREEN);
    private final SoftButton releaseButton = new SoftButton("Rendre la clé à Windows", SoftButton.Kind.BLUE);
    private final SoftButton stopButton = new SoftButton("Arrêter la VM", SoftButton.Kind.BLUE);
    private final SoftButton consoleButton = new SoftButton("Console", SoftButton.Kind.BLUE);
    private final SoftButton refreshButton = new SoftButton("Actualiser", SoftButton.Kind.BLUE);
    private final SoftButton adminButton = new SoftButton("Relancer en administrateur", SoftButton.Kind.GREEN);
    private final SoftButton serviceButton = new SoftButton("Démarrer Hyper-V", SoftButton.Kind.GREEN);

    private Snapshot snapshot = new Snapshot(false, "missing", "", List.of(), List.of(), List.of(), null);
    private boolean busy;

    private KeyVApp(Engine engine) {
        this.engine = engine;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--list".equals(args[0])) {
            printList(new Engine().list());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                new KeyVApp(new Engine()).open();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Key-V", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void open() {
        disks.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        vms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        disks.setVisibleRowCount(10);
        vms.setVisibleRowCount(10);
        disks.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(diskLabel(value));
            styleRow(label, selected, value.ventoy() ? Theme.GREEN_INK : Theme.INK);
            return label;
        });
        vms.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(vmLabel(value));
            styleRow(label, selected, Theme.INK);
            return label;
        });
        disks.addListSelectionListener(event -> updateButtons());
        vms.addListSelectionListener(event -> updateButtons());

        journal.setEditable(false);
        journal.setLineWrap(true);
        journal.setWrapStyleWord(true);
        journal.setFont(Theme.UI);
        journal.setBackground(Theme.CARD);
        journal.setForeground(Theme.INK);
        journal.setCaretColor(Theme.INK);
        journal.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        styleChoice(secureBootBox);
        styleChoice(startBox);
        styleList(disks);
        styleList(vms);

        JLabel title = new JLabel("Key-V");
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.INK);
        subtitle.setFont(Theme.UI);
        subtitle.setForeground(Theme.MUTED);
        banner.setFont(Theme.UI);
        banner.setForeground(Theme.BLUE_INK);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(subtitle);
        titles.add(Box.createVerticalStrut(8));
        titles.add(banner);
        JPanel brand = new JPanel(new BorderLayout(14, 0));
        brand.setOpaque(false);
        brand.add(new Logo(), BorderLayout.WEST);
        brand.add(titles, BorderLayout.CENTER);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.CANVAS);
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 10, 16));
        JPanel languages = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        languages.setOpaque(false);
        languages.add(frenchChip);
        languages.add(englishChip);
        header.add(brand, BorderLayout.CENTER);
        header.add(languages, BorderLayout.EAST);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                column(diskTitle, disks, diskHint, true),
                column(vmTitle, vms, vmHint, true));
        split.setResizeWeight(0.56);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        split.setBackground(Theme.CANVAS);
        split.setOpaque(true);
        styleDivider(split);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        options.setOpaque(false);
        options.add(secureBootBox);
        options.add(startBox);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(connectButton);
        buttons.add(releaseButton);
        buttons.add(stopButton);
        buttons.add(consoleButton);
        buttons.add(refreshButton);
        buttons.add(adminButton);
        buttons.add(serviceButton);

        journalTitle.setFont(Theme.UI);
        journalTitle.setForeground(Theme.MUTED);
        JScrollPane journalScroll = cardScroll(journal, false);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setBackground(Theme.CANVAS);
        south.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(options, BorderLayout.NORTH);
        actions.add(buttons, BorderLayout.SOUTH);
        JPanel journalBlock = new JPanel(new BorderLayout(0, 6));
        journalBlock.setOpaque(false);
        journalBlock.add(journalTitle, BorderLayout.NORTH);
        journalBlock.add(journalScroll, BorderLayout.CENTER);
        south.add(actions, BorderLayout.NORTH);
        south.add(journalBlock, BorderLayout.CENTER);

        JFrame frame = new JFrame("Key-V");
        frame.setIconImage(Logo.icon());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.CANVAS);
        frame.setLayout(new BorderLayout());
        frame.add(header, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(860, 560));
        frame.setSize(980, 640);
        frame.setLocationRelativeTo(null);
        FluffyCat cat = new FluffyCat();
        frame.setGlassPane(cat);
        cat.setVisible(true);
        cat.startSchedule();

        connectButton.addActionListener(event -> connect());
        releaseButton.addActionListener(event -> release());
        stopButton.addActionListener(event -> stopVm());
        consoleButton.addActionListener(event -> openConsole());
        refreshButton.addActionListener(event -> refresh());
        adminButton.addActionListener(event -> elevate());
        serviceButton.addActionListener(event -> startService());

        frame.setVisible(true);
        applyLanguage();
        note(Lang.t(
                "Choisis la clé GPT, même en ligne, et une VM génération 1 ou 2. La clé devient le disque de démarrage.",
                "Pick the GPT key, even while it is online, and a generation 1 or 2 VM. The key becomes the boot disk."));
        refresh();
    }

    private void useLanguage(Lang.Code code) {
        Lang.set(code);
        applyLanguage();
        banner.setText(bannerText(snapshot));
        frenchChip.repaint();
        englishChip.repaint();
        disks.repaint();
        vms.repaint();
        if (connectButton.getParent() != null) {
            connectButton.getParent().revalidate();
        }
    }

    private void applyLanguage() {
        subtitle.setText(Lang.t(
                "Une clé GPT, même en ligne, se branche sur une VM génération 1 ou 2.",
                "A GPT key, even while online, connects to a generation 1 or 2 VM."));
        diskTitle.setText(Lang.t("1. Clé branchée sur ce PC", "1. Key plugged into this PC"));
        diskHint.setText(Lang.t(
                "Fais glisser la barre verte pour voir la clé choisie.",
                "Drag the green bar to see the key you picked."));
        vmTitle.setText(Lang.t("2. Machine virtuelle Hyper-V", "2. Hyper-V virtual machine"));
        vmHint.setText(Lang.t("La VM doit être éteinte.", "The VM must be turned off."));
        journalTitle.setText(Lang.t("Journal", "Log"));
        secureBootBox.setText(Lang.t(
                "Couper le Secure Boot le temps du démarrage Ventoy",
                "Turn off Secure Boot while Ventoy boots"));
        startBox.setText(Lang.t(
                "Démarrer la VM et ouvrir la console",
                "Start the VM and open the console"));
        connectButton.setText(Lang.t("Connecter la clé", "Connect the key"));
        releaseButton.setText(Lang.t("Rendre la clé à Windows", "Give the key back to Windows"));
        stopButton.setText(Lang.t("Arrêter la VM", "Stop the VM"));
        consoleButton.setText(Lang.t("Console", "Console"));
        refreshButton.setText(Lang.t("Actualiser", "Refresh"));
        adminButton.setText(Lang.t("Relancer en administrateur", "Restart as administrator"));
        serviceButton.setText(Lang.t("Démarrer Hyper-V", "Start Hyper-V"));
    }

    private static JPanel column(JLabel title, JList<?> list, JLabel hint, boolean alwaysBar) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        title.setFont(Theme.UI);
        title.setForeground(Theme.INK);
        title.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        panel.add(title, BorderLayout.NORTH);
        JScrollPane scroll = cardScroll(list, alwaysBar);
        scroll.setPreferredSize(new Dimension(440, 240));
        panel.add(scroll, BorderLayout.CENTER);
        hint.setFont(Theme.UI);
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private void refresh() {
        run(Lang.t("Lecture des clés et des VM…", "Reading keys and VMs…"), () -> engine.list(), this::showSnapshot);
    }

    private void connect() {
        UsbDisk disk = disks.getSelectedValue();
        VirtualMachine vm = vms.getSelectedValue();
        if (disk == null || vm == null) {
            note(Lang.t("Choisis une clé et une VM.", "Choose a key and a VM."));
            return;
        }
        if (!snapshot.admin()) {
            askElevation(Lang.t(
                    "Donner la clé à Hyper-V demande les droits administrateur.",
                    "Giving the key to Hyper-V requires administrator rights."));
            return;
        }
        if (!disk.accepted()) {
            int answer = JOptionPane.showConfirmDialog(
                    null,
                    Lang.t(
                            "Cette clé ne ressemble pas à Ventoy. La donner quand même à « " + vm.name() + " » ?",
                            "This key does not look like Ventoy. Give it to \"" + vm.name() + "\" anyway?"),
                    "Key-V",
                    JOptionPane.OK_CANCEL_OPTION);
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
        }
        StringBuilder message = new StringBuilder();
        message.append(Lang.t(
                "Donner « " + disk.name() + " » à « " + vm.name() + " » ?\n\nWindows retire la clé de l'Explorateur. La VM démarre dessus tant qu'elle reste branchée.",
                "Give \"" + disk.name() + "\" to \"" + vm.name() + "\"?\n\nWindows removes the key from Explorer. The VM boots from it while it stays plugged in."));
        if (!vm.isOff()) {
            message.append(Lang.t("\n\nLa VM va d'abord être éteinte.", "\n\nThe VM will be turned off first."));
        }
        int answer = JOptionPane.showConfirmDialog(null, message.toString(), "Key-V", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        boolean disableSecureBoot = secureBootBox.isSelected();
        boolean start = startBox.isSelected();
        run(Lang.t("Connexion de la clé à " + vm.name() + "…", "Connecting the key to " + vm.name() + "…"), () -> {
            if (!vm.isOff()) {
                ActionResult stopped = engine.stop(vm);
                if (!stopped.ok()) {
                    return stopped;
                }
            }
            return engine.connect(disk, vm, disableSecureBoot, start);
        }, this::showAction);
    }

    private void release() {
        UsbDisk disk = disks.getSelectedValue();
        VirtualMachine vm = vms.getSelectedValue();
        if (disk == null || vm == null) {
            note(Lang.t("Choisis la clé et la VM qui la détient.", "Choose the key and the VM that holds it."));
            return;
        }
        if (!snapshot.admin()) {
            askElevation(Lang.t(
                    "Rendre la clé à Windows demande les droits administrateur.",
                    "Giving the key back to Windows requires administrator rights."));
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                null,
                Lang.t(
                        "Retirer la clé de « " + vm.name() + " » et la rendre à l'Explorateur ?",
                        "Remove the key from \"" + vm.name() + "\" and give it back to Explorer?"),
                "Key-V",
                JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        run(Lang.t("Retour de la clé vers Windows…", "Returning the key to Windows…"), () -> {
            if (!vm.isOff()) {
                ActionResult stopped = engine.stop(vm);
                if (!stopped.ok()) {
                    return stopped;
                }
            }
            return engine.release(disk, vm);
        }, this::showAction);
    }

    private void stopVm() {
        VirtualMachine vm = vms.getSelectedValue();
        if (vm == null) {
            return;
        }
        if (!snapshot.admin()) {
            askElevation(Lang.t(
                    "Arrêter la VM demande les droits administrateur.",
                    "Stopping the VM requires administrator rights."));
            return;
        }
        run(Lang.t("Arrêt de " + vm.name() + "…", "Stopping " + vm.name() + "…"), () -> engine.stop(vm), this::showAction);
    }

    private void openConsole() {
        VirtualMachine vm = vms.getSelectedValue();
        if (vm == null) {
            return;
        }
        run(Lang.t("Ouverture de la console…", "Opening the console…"), () -> engine.console(vm), this::showAction);
    }

    private void startService() {
        if (!snapshot.admin()) {
            askElevation(Lang.t(
                    "Démarrer Hyper-V demande les droits administrateur.",
                    "Starting Hyper-V requires administrator rights."));
            return;
        }
        run(Lang.t("Démarrage du service Hyper-V…", "Starting the Hyper-V service…"), engine::startService, this::showAction);
    }

    private void elevate() {
        run(Lang.t("Demande des droits administrateur…", "Asking for administrator rights…"), () -> {
            ActionResult result = engine.elevate();
            if (result.ok()) {
                SwingUtilities.invokeLater(() -> System.exit(0));
            }
            return result;
        }, this::showAction);
    }

    private void askElevation(String reason) {
        int answer = JOptionPane.showConfirmDialog(
                null,
                reason + Lang.t("\n\nRelancer Key-V en administrateur ?", "\n\nRestart Key-V as administrator?"),
                "Key-V",
                JOptionPane.OK_CANCEL_OPTION);
        if (answer == JOptionPane.OK_OPTION) {
            elevate();
        }
    }

    private void showSnapshot(Snapshot next) {
        int diskNumber = disks.getSelectedValue() == null ? -1 : disks.getSelectedValue().number();
        String vmName = vms.getSelectedValue() == null ? null : vms.getSelectedValue().name();
        snapshot = next;
        diskModel.clear();
        next.disks().forEach(diskModel::addElement);
        vmModel.clear();
        next.vms().forEach(vmModel::addElement);
        selectDisk(diskNumber);
        selectVm(vmName);
        if (disks.getSelectedIndex() < 0) {
            int bootableIndex = -1;
            for (int i = 0; i < diskModel.size(); i++) {
                if (diskModel.get(i).ventoy()) {
                    disks.setSelectedIndex(i);
                    bootableIndex = -2;
                    break;
                }
                if (bootableIndex < 0 && diskModel.get(i).bootable()) {
                    bootableIndex = i;
                }
            }
            if (disks.getSelectedIndex() < 0 && bootableIndex >= 0) {
                disks.setSelectedIndex(bootableIndex);
            }
            if (disks.getSelectedIndex() < 0 && !diskModel.isEmpty()) {
                disks.setSelectedIndex(0);
            }
        }
        if (vms.getSelectedIndex() < 0 && !vmModel.isEmpty()) {
            vms.setSelectedIndex(0);
        }
        banner.setText(bannerText(next));
        if (next.error() != null) {
            note(next.error());
        } else if (next.disks().isEmpty()) {
            note(Lang.t(
                    "Aucune clé USB détectée. Branche la clé démarrable, puis actualise.",
                    "No USB key detected. Plug in the bootable key, then refresh."));
        } else if ("ready".equals(next.hyperv()) && next.vms().isEmpty()) {
            note(Lang.t(
                    "Hyper-V est prêt. Crée une VM génération 1 ou 2, éteinte, puis reviens ici.",
                    "Hyper-V is ready. Create a generation 1 or 2 VM, turn it off, then come back."));
        }
        updateButtons();
    }

    private void showAction(ActionResult result) {
        for (String line : result.logs()) {
            note(line);
        }
        if (result.error() != null) {
            note(result.error());
            JOptionPane.showMessageDialog(null, result.error(), "Key-V", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }

    private void updateButtons() {
        UsbDisk disk = disks.getSelectedValue();
        VirtualMachine vm = vms.getSelectedValue();
        boolean hypervReady = "ready".equals(snapshot.hyperv());
        boolean pair = disk != null && vm != null;
        boolean owned = pair && vm.owns(disk.number());
        connectButton.setEnabled(!busy && hypervReady && pair && !owned);
        releaseButton.setEnabled(!busy && hypervReady && owned);
        stopButton.setEnabled(!busy && hypervReady && vm != null && !vm.isOff());
        consoleButton.setEnabled(!busy && hypervReady && vm != null);
        refreshButton.setEnabled(!busy);
        adminButton.setEnabled(!busy && !snapshot.admin());
        adminButton.setVisible(!snapshot.admin());
        serviceButton.setEnabled(!busy && "stopped".equals(snapshot.hyperv()));
        serviceButton.setVisible("stopped".equals(snapshot.hyperv()));
        secureBootBox.setEnabled(!busy);
        startBox.setEnabled(!busy);
    }

    private <T> void run(String status, Task<T> task, java.util.function.Consumer<T> done) {
        setBusy(true);
        note(status);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.get();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    done.accept(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    note(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean value) {
        busy = value;
        updateButtons();
    }

    private void selectDisk(int number) {
        for (int i = 0; i < diskModel.size(); i++) {
            if (diskModel.get(i).number() == number) {
                disks.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectVm(String name) {
        if (name == null) {
            return;
        }
        for (int i = 0; i < vmModel.size(); i++) {
            if (vmModel.get(i).name().equals(name)) {
                vms.setSelectedIndex(i);
                return;
            }
        }
    }

    private String diskLabel(UsbDisk disk) {
        String kind = disk.ventoy() ? "Ventoy" : disk.gpt() ? "GPT" : disk.bootable() ? Lang.t("Démarrable", "Bootable") : "USB";
        String letters = disk.letters().isBlank()
                ? Lang.t("sans lettre de lecteur", "no drive letter")
                : Lang.t("lecteur ", "drive ") + disk.letters() + ":";
        String owner = ownerName(disk.number());
        String place = owner == null
                ? statusText(disk.status())
                : Lang.t("branchée sur « " + owner + " »", "attached to \"" + owner + "\"");
        String line1 = escape(disk.name()) + "   ·   " + disk.sizeGb() + Lang.t(" Go", " GB") + "   ·   " + kind;
        String line2 = Lang.t("Disque ", "Disk ") + disk.number() + "   ·   " + escape(letters) + "   ·   " + escape(place);
        return "<html>" + line1 + "<br><font color='#5C7C84'>" + line2 + "</font></html>";
    }

    private String vmLabel(VirtualMachine vm) {
        return "<html>" + escape(vm.name())
                + "<br><font color='#5C7C84'>" + Messages.state(vm.state())
                + "   ·   " + Lang.t("Génération ", "Generation ") + vm.generation()
                + "</font></html>";
    }

    private static String statusText(String status) {
        return switch (status) {
            case "En ligne" -> Lang.t("en ligne", "online");
            case "Hors ligne" -> Lang.t("hors ligne", "offline");
            default -> status;
        };
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String ownerName(int diskNumber) {
        for (VirtualMachine vm : snapshot.vms()) {
            if (vm.owns(diskNumber)) {
                return vm.name();
            }
        }
        return null;
    }

    private static String bannerText(Snapshot snapshot) {
        if (snapshot.error() != null && snapshot.disks().isEmpty() && snapshot.vms().isEmpty()) {
            return snapshot.error();
        }
        if (Messages.homeEdition(snapshot.edition())) {
            return Lang.t(
                    "Ce PC est en Windows Famille. Hyper-V s'installe avec Windows Pro. Key-V donnera ensuite la clé à la VM.",
                    "This PC is Windows Home. Hyper-V comes with Windows Pro. Key-V can then give the key to the VM.");
        }
        if ("missing".equals(snapshot.hyperv())) {
            return Lang.t(
                    "Hyper-V n'est pas installé. Active-le dans les fonctionnalités Windows, puis redémarre.",
                    "Hyper-V is not installed. Turn it on in Windows Features, then restart.");
        }
        if ("stopped".equals(snapshot.hyperv())) {
            return Lang.t("Le service Hyper-V est arrêté.", "The Hyper-V service is stopped.");
        }
        if (!snapshot.admin()) {
            return Lang.t(
                    "Key-V voit les clés, y compris en ligne. La connexion à la VM se fait en administrateur.",
                    "Key-V can see the keys, including online ones. Connecting one to the VM needs an administrator.");
        }
        return Lang.t(
                "Choisis la clé GPT, même en ligne, et la VM génération 1 ou 2 éteinte, puis connecte.",
                "Choose the GPT key, even while it is online, and the generation 1 or 2 VM while it is off, then connect.");
    }

    private void note(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (journal.getDocument().getLength() > 0) {
            journal.append("\n");
        }
        journal.append(line);
        journal.setCaretPosition(journal.getDocument().getLength());
    }

    private static void styleRow(JLabel label, boolean selected, java.awt.Color foreground) {
        label.setOpaque(true);
        label.setFont(Theme.UI);
        label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        label.setBackground(selected ? Theme.SELECTION : Theme.CARD);
        label.setForeground(foreground);
    }

    private static void styleList(JList<?> list) {
        list.setFont(Theme.UI);
        list.setBackground(Theme.CARD);
        list.setForeground(Theme.INK);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.INK);
        list.setFixedCellHeight(52);
    }

    private static void styleChoice(JCheckBox box) {
        box.setOpaque(false);
        box.setFont(Theme.UI);
        box.setForeground(Theme.INK);
    }

    private static JScrollPane cardScroll(java.awt.Component view, boolean alwaysBar) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(new LineBorder(Theme.LINE, 1, true));
        scroll.getViewport().setBackground(Theme.CARD);
        scroll.setBackground(Theme.CARD);
        scroll.setVerticalScrollBarPolicy(alwaysBar
                ? JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        installBar(scroll.getVerticalScrollBar());
        installBar(scroll.getHorizontalScrollBar());
        return scroll;
    }

    private static void installBar(JScrollBar bar) {
        bar.setUI(new SoftScrollBarUI());
        bar.setOpaque(false);
        bar.setUnitIncrement(28);
        if (bar.getOrientation() == JScrollBar.VERTICAL) {
            bar.setPreferredSize(new Dimension(18, 0));
        } else {
            bar.setPreferredSize(new Dimension(0, 18));
        }
    }

    private static void styleDivider(JSplitPane split) {
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics graphics) {
                        Graphics2D g = (Graphics2D) graphics.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(Theme.CANVAS);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        int width = 6;
                        int height = 56;
                        int x = (getWidth() - width) / 2;
                        int y = (getHeight() - height) / 2;
                        g.setColor(new java.awt.Color(0x8EC8E0));
                        g.fillRoundRect(x, y, width, height, 8, 8);
                        g.dispose();
                    }
                };
            }
        });
        split.setDividerSize(18);
    }

    private static void printList(Snapshot snapshot) {
        System.out.println("edition=" + snapshot.edition());
        System.out.println("admin=" + snapshot.admin());
        System.out.println("hyperv=" + snapshot.hyperv());
        if (snapshot.error() != null) {
            System.out.println("error=" + snapshot.error());
        }
        for (UsbDisk disk : snapshot.disks()) {
            System.out.println("usb " + disk.number() + " " + disk.name()
                    + " ventoy=" + disk.ventoy()
                    + " bootable=" + disk.bootable()
                    + " status=" + disk.status());
        }
        for (VirtualMachine vm : snapshot.vms()) {
            System.out.println("vm " + vm.name() + " " + vm.state() + " gen=" + vm.generation());
        }
    }

    @FunctionalInterface
    private interface Task<T> {
        T get() throws IOException, InterruptedException;
    }
}

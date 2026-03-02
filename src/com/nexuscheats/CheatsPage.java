package com.nexuscheats;

import com.nexusui.api.NexusPage;
import javax.swing.JPanel;

public class CheatsPage implements NexusPage {
    private CheatsPanel panel;

    public String getId() { return "nexus_cheats"; }
    public String getTitle() { return "Cheats"; }

    public JPanel createPanel(int port) {
        panel = new CheatsPanel(port);
        return panel;
    }

    public void refresh() {
        if (panel != null) panel.refresh();
    }
}

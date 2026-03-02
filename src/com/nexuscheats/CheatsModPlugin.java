package com.nexuscheats;

import com.fs.starfarer.api.BaseModPlugin;
import com.nexusui.api.NexusPage;
import com.nexusui.api.NexusPageFactory;
import com.nexusui.overlay.NexusFrame;
import org.apache.log4j.Logger;

public class CheatsModPlugin extends BaseModPlugin {
    private static final Logger log = Logger.getLogger(CheatsModPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        log.info("NexusCheats: Loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        NexusFrame.registerPageFactory(new NexusPageFactory() {
            public String getId() { return "nexus_cheats"; }
            public String getTitle() { return "Cheats"; }
            public NexusPage create() { return new CheatsPage(); }
        });
        log.info("NexusCheats: Cheats page registered");

        // Reapply persistent cheats (god mode, OP bonus) from saved data
        CheatsPanel.reapplyPersistentCheats();
        log.info("NexusCheats: Persistent cheats restored");
    }
}

package io.github.zekerzhayard.forgewrapper.installer.util;

import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Install;

import java.io.File;

public abstract class AbstractInstallWrapper {
    public abstract Boolean runProcessor(ProgressCallback monitor, File librariesDir, File minecraft, File root, File installer);
    public abstract Install loadInstallProfile();
}
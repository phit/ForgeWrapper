package io.github.zekerzhayard.forgewrapper.installer.util;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.actions.PostProcessors;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraftforge.installer.json.Util.GSON;

public class InstallV0Wrapper extends AbstractInstallWrapper {
    protected InstallerV0 installer;
    public InstallV0Wrapper(File librariesDir) {
        this.installer = (InstallerV0) loadInstallProfile();
        this.installer.setLibrariesDir(librariesDir);
    }

    @Override
    public Install loadInstallProfile() {
        try (InputStream stream = Util.class.getResourceAsStream("/install_profile.json")) {
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), InstallerV0.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean runProcessor(ProgressCallback monitor, File librariesDir, File minecraft, File root, File installerPath) {
        return new PostProcessors(installer, true, monitor).process(librariesDir, minecraft);
    }

    public static class InstallerV0 extends Install {
        protected File librariesDir;

        public void setLibrariesDir(File librariesDir) {
            this.librariesDir = librariesDir;
        }

        @Override
        public List<Processor> getProcessors(String side) {
            List<Processor> processor = super.getProcessors(side);
            if (processor.size() == 0) {
                checkProcessorFiles(processor, super.getData("client".equals(side)), this.librariesDir);
            }
            return processor;
        }

        private static void checkProcessorFiles(List<Processor> processors, Map<String, String> data, File base) {
            Map<String, File> artifactData = new HashMap<>();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                String value = entry.getValue();
                if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
                    artifactData.put("{" + entry.getKey() + "}", Artifact.from(value.substring(1, value.length() - 1)).getLocalPath(base));
                }
            }

            Map<Processor, Map<String, String>> outputsMap = new HashMap<>();
            label:
            for (Processor processor : processors) {
                Map<String, String> outputs = new HashMap<>();
                if (processor.getOutputs().isEmpty()) {
                    String[] args = processor.getArgs();
                    for (int i = 0; i < args.length; i++) {
                        for (Map.Entry<String, File> entry : artifactData.entrySet()) {
                            if (args[i].contains(entry.getKey())) {
                                // We assume that all files that exist but don't have the sha1 checksum are valid.
                                if (entry.getValue().exists()) {
                                    outputs.put(entry.getKey(), DownloadUtils.getSha1(entry.getValue()));
                                } else {
                                    outputsMap.clear();
                                    break label;
                                }
                            }
                        }
                    }
                    outputsMap.put(processor, outputs);
                }
            }
            for (Map.Entry<Processor, Map<String, String>> entry : outputsMap.entrySet()) {
                setOutputs(entry.getKey(), entry.getValue());
            }
        }

        private static Field outputsField;

        private static void setOutputs(Processor processor, Map<String, String> outputs) {
            try {
                if (outputsField == null) {
                    outputsField = Processor.class.getDeclaredField("outputs");
                    outputsField.setAccessible(true);
                }
                outputsField.set(processor, outputs);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }
}
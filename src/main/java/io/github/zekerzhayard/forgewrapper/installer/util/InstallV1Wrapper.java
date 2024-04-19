package io.github.zekerzhayard.forgewrapper.installer.util;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.actions.PostProcessors;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class InstallV1Wrapper extends AbstractInstallWrapper {
    protected Install installer;

    public InstallV1Wrapper(File librariesDir) {
        Install profile = Util.loadInstallProfile();
        this.installer = new InstallerV1(profile instanceof InstallV1 ? (InstallV1) profile : new InstallV1(profile), librariesDir);
    }

    @Override
    public Install loadInstallProfile() {
        return Util.loadInstallProfile();
    }

    @Override
    public Boolean runProcessor(ProgressCallback monitor, File librariesDir, File minecraft, File root, File installerPath) {
        try {
            Method processMethod = PostProcessors.class.getMethod("process", File.class, File.class, File.class, File.class);
            if (boolean.class.equals(processMethod.getReturnType())) {
                PostProcessors processors = new PostProcessors((InstallV1) installer, true, monitor);
                return (boolean) processMethod.invoke(processors, librariesDir, minecraft, root, installerPath);
            } else {
                return new PostProcessors((InstallV1) installer, true, monitor).process(librariesDir, minecraft, root, installerPath) != null;
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static class InstallerV1 extends InstallV1 {
        protected Map<String, List<Processor>> processors = new HashMap<>();
        protected File librariesDir;

        public InstallerV1(InstallV1 profile, File librariesDir) {
            super(profile);
            this.serverJarPath = profile.getServerJarPath();
            this.librariesDir = librariesDir;
        }

        @Override
        public List<Processor> getProcessors(String side) {
            List<Processor> processor = this.processors.get(side);
            if (processor == null) {
                checkProcessorFiles(processor = super.getProcessors(side), super.getData("client".equals(side)), this.librariesDir);
                this.processors.put(side, processor);
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
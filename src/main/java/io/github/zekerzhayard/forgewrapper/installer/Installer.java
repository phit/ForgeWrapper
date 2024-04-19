package io.github.zekerzhayard.forgewrapper.installer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.zekerzhayard.forgewrapper.installer.util.AbstractInstallWrapper;
import io.github.zekerzhayard.forgewrapper.installer.util.InstallV0Wrapper;
import io.github.zekerzhayard.forgewrapper.installer.util.InstallV1Wrapper;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;

public class Installer {
    private static AbstractInstallWrapper wrapper;
    private static AbstractInstallWrapper getWrapper(File librariesDir) {
        if (wrapper == null) {
            try {
                Class<?> installerClass = Util.class.getMethod("loadInstallProfile").getReturnType();
                if (installerClass.equals(Install.class)) {
                    wrapper = new InstallV0Wrapper(librariesDir);
                } else if (installerClass.equals(InstallV1.class)) {
                    wrapper = new InstallV1Wrapper(librariesDir);
                } else {
                    throw new IllegalArgumentException("Unable to determine the installer version. (" + installerClass + ")");
                }
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return wrapper;
    }

    public static Map<String, Object> getData(File librariesDir) {
        Map<String, Object> data = new HashMap<>();
        getWrapper(librariesDir);
        Version0 version = Version0.loadVersion(wrapper.loadInstallProfile());
        data.put("mainClass", version.getMainClass());
        if (wrapper instanceof InstallV0Wrapper) {
            data.put("jvmArgs", new String[0]);
        } else {
            data.put("jvmArgs", version.getArguments().getJvm());
        }
        data.put("extraLibraries", getExtraLibraries(version));
        return data;
    }

    public static boolean install(File libraryDir, File minecraftJar, File installerJar) throws Throwable {
        ProgressCallback monitor = ProgressCallback.withOutputs(System.out);
        if (System.getProperty("java.net.preferIPv4Stack") == null) {
            System.setProperty("java.net.preferIPv4Stack", "true");
        }
        String vendor = System.getProperty("java.vendor", "missing vendor");
        String javaVersion = System.getProperty("java.version", "missing java version");
        String jvmVersion = System.getProperty("java.vm.version", "missing jvm version");
        monitor.message(String.format("JVM info: %s - %s - %s", vendor, javaVersion, jvmVersion));
        monitor.message("java.net.preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack"));
        monitor.message("Current Time: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));

        return wrapper.runProcessor(monitor, libraryDir, minecraftJar, libraryDir.getParentFile(), installerJar);
    }

    // Some libraries in the version json are not available via direct download,
    // so they are not available in the MultiMC meta json,
    // so wee need to get them manually.
    private static List<String> getExtraLibraries(Version0 version) {
        List<String> paths = new ArrayList<>();
        for (Version.Library library : version.getLibraries()) {
            Version.LibraryDownload artifact = library.getDownloads().getArtifact();
            if (artifact.getUrl().isEmpty()) {
                paths.add(artifact.getPath());
            }
        }
        return paths;
    }

    public static class Version0 extends Version {

        public static Version0 loadVersion(Install profile) {
            try (InputStream stream = Util.class.getResourceAsStream(profile.getJson())) {
                return Util.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Version0.class);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        protected String mainClass;
        protected Version0.Arguments arguments;

        public String getMainClass() {
            return mainClass;
        }

        public Version0.Arguments getArguments() {
            return arguments;
        }

        public static class Arguments {
            protected String[] jvm;

            public String[] getJvm() {
                return jvm;
            }
        }
    }
}

package com.maliopt.gpu;

import com.maliopt.MaliOptMod;
import com.maliopt.MaliOptNative;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GPUDetector {

    private static String      cachedRenderer   = null;
    private static String      cachedVendor     = null;
    private static String      cachedVersion    = null;
    private static Set<String> cachedExtensions = null;
    private static Boolean     isMali           = null;

    private static String safeGet(int param) {
        try {
            String val = GL11.glGetString(param);
            return val != null ? val : "";
        } catch (Exception e) {
            MaliOptMod.LOGGER.warn("[MaliOpt] glGetString({}) falhou: {}", param, e.getMessage());
            return "";
        }
    }

    public static String getRenderer() {
        if (cachedRenderer == null) cachedRenderer = safeGet(GL11.GL_RENDERER);
        return cachedRenderer;
    }

    /** Alias de getRenderer() — usado por ShaderCache e ShaderCapabilities */
    public static String getGPUModel() {
        return getRenderer();
    }

    public static String getVendor() {
        if (cachedVendor == null) cachedVendor = safeGet(GL11.GL_VENDOR);
        return cachedVendor;
    }

    public static String getVersion() {
        if (cachedVersion == null) cachedVersion = safeGet(GL11.GL_VERSION);
        return cachedVersion;
    }

    public static Set<String> getAllExtensions() {
        if (cachedExtensions != null) return cachedExtensions;

        // NOVA PRIORIDADE 0 — Deteção direta do sistema (JNI)
        Set<String> systemExts = getSystemGLESExtensionsSafe();
        if (!systemExts.isEmpty()) {
            MaliOptMod.LOGGER.info("[MaliOpt] Extensões via driver nativo do sistema: {}", systemExts.size());
            cachedExtensions = systemExts;
            return cachedExtensions;
        }

        // PRIORIDADE 1 — MobileGlues backend access
        if (MobileGluesDetector.isActive() && MobileGluesDetector.hasBackendAccess()) {
            Set<String> backendExts = MobileGluesDetector.getBackendExtensions();
            if (!backendExts.isEmpty()) {
                MaliOptMod.LOGGER.info("[MaliOpt] Extensões via MobileGlues backend: {}", backendExts.size());
                cachedExtensions = backendExts;
                return cachedExtensions;
            }
        }

        // PRIORIDADE 2 — glGetStringi loop
        Set<String> exts = new HashSet<>();
        try {
            int count = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    String ext = GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
                    if (ext != null && !ext.isEmpty()) exts.add(ext);
                }
                MaliOptMod.LOGGER.info("[MaliOpt] glGetStringi: {} extensões encontradas", exts.size());
            }
        } catch (Exception e) {
            MaliOptMod.LOGGER.warn("[MaliOpt] glGetStringi falhou: {}", e.getMessage());
        }

        // PRIORIDADE 3 — Fallback string plana
        if (exts.isEmpty()) {
            String flat = safeGet(GL11.GL_EXTENSIONS);
            if (!flat.isEmpty()) {
                Collections.addAll(exts, flat.split(" "));
                MaliOptMod.LOGGER.info("[MaliOpt] Fallback glGetString: {} extensões", exts.size());
            }
        }

        cachedExtensions = Collections.unmodifiableSet(exts);
        return cachedExtensions;
    }

    /**
     * Tenta obter as extensões via novo método JNI (driver nativo).
     * Se falhar ou retornar null, devolve um Set vazio.
     */
    private static Set<String> getSystemGLESExtensionsSafe() {
        try {
            if (!MaliOptMod.isNativePluginLoaded()) {
                return Collections.emptySet();
            }
            String[] exts = MaliOptNative.getSystemGLESExtensions();
            if (exts != null && exts.length > 0) {
                Set<String> set = new HashSet<>(Arrays.asList(exts));
                set.remove(""); // Remove possíveis strings vazias
                return Collections.unmodifiableSet(set);
            }
        } catch (Exception e) {
            MaliOptMod.LOGGER.warn("[MaliOpt] getSystemGLESExtensions falhou: {}", e.getMessage());
        }
        return Collections.emptySet();
    }

    public static boolean hasExtension(String ext) {
        return getAllExtensions().contains(ext);
    }

    public static boolean isMaliGPU() {
        if (isMali == null) {
            String r = getRenderer().toLowerCase();
            String v = getVendor().toLowerCase();
            isMali = r.contains("mali") || v.contains("arm");
        }
        return isMali;
    }

    public static boolean isBifrost() {
        String r = getRenderer();
        return r.contains("G52") || r.contains("G57")
            || r.contains("G68") || r.contains("G76")
            || r.contains("G77");
    }

    public static boolean isValhall() {
        String r = getRenderer();
        return r.contains("G310") || r.contains("G510")
            || r.contains("G610") || r.contains("G710")
            || r.contains("G715");
    }

    public static void resetCache() {
        cachedRenderer   = null;
        cachedVendor     = null;
        cachedVersion    = null;
        cachedExtensions = null;
        isMali           = null;
    }
}

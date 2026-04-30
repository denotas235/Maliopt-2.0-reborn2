package com.maliopt;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaliOptMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MaliOpt");
    private static boolean nativePluginLoaded = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[MaliOpt] Inicializando...");
        loadNativePlugin();
        if (nativePluginLoaded) {
            LOGGER.info("[MaliOpt] Plugin nativo carregado com sucesso.");
            // Força a deteção para efeitos de teste
            var exts = com.maliopt.gpu.GPUDetector.getAllExtensions();
            LOGGER.info("[MaliOpt] Extensões detectadas: {}", exts.size());
        } else {
            LOGGER.warn("[MaliOpt] Plugin nativo não disponível. A usar fallback.");
        }
    }

    private static void loadNativePlugin() {
        try {
            System.loadLibrary("maliopt");
            nativePluginLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            LOGGER.error("[MaliOpt] Falha ao carregar libmaliopt.so: " + e.getMessage());
        }
    }

    public static boolean isNativePluginLoaded() {
        return nativePluginLoaded;
    }
}

package com.maliopt.util;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gl.Framebuffer;

/**
 * Utilitário para aceder aos IDs OpenGL do Framebuffer do Minecraft em 1.21.11+.
 * A classe GlTexture (subclasse de GpuTexture) expõe o ID via reflexão.
 */
public final class FramebufferUtil {

    private FramebufferUtil() {}

    /**
     * Obtém o ID OpenGL da textura de cor do Framebuffer.
     * Retorna 0 se não for possível obter.
     */
    public static int getColorTextureId(Framebuffer fb) {
        GpuTexture tex = com.maliopt.util.FramebufferUtil.getColorTextureId(fb);
        if (tex == null) return 0;
        return getGlId(tex);
    }

    /**
     * Obtém o ID OpenGL do FBO do Framebuffer via reflexão no GlFramebuffer interno.
     * Em 1.21.11 o FBO já não é exposto directamente — usamos reflexão.
     */
    public static int getFboId(Framebuffer fb) {
        try {
            java.lang.reflect.Field[] fields = fb.getClass().getSuperclass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                f.setAccessible(true);
                Object val = f.get(fb);
                if (val != null && val.getClass().getSimpleName().equals("GlFramebuffer")) {
                    // GlFramebuffer tem um campo int com o ID
                    java.lang.reflect.Field[] fbFields = val.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field ff : fbFields) {
                        if (ff.getType() == int.class) {
                            ff.setAccessible(true);
                            return ff.getInt(val);
                        }
                    }
                }
            }
            // Fallback: procurar em todos os campos da classe concreta
            for (java.lang.reflect.Field f : fb.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    int val = f.getInt(fb);
                    if (val > 0) return val;
                }
            }
        } catch (Exception e) {
            // silent fail
        }
        return 0;
    }

    private static int getGlId(GpuTexture tex) {
        try {
            // GlTexture é a subclasse concreta — tem um campo int com o ID GL
            java.lang.reflect.Field[] fields = tex.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    int val = f.getInt(tex);
                    if (val > 0) return val;
                }
            }
            // Tentar na superclasse
            fields = tex.getClass().getSuperclass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    int val = f.getInt(tex);
                    if (val > 0) return val;
                }
            }
        } catch (Exception e) {
            // silent fail
        }
        return 0;
    }
}

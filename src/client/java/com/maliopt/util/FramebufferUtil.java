package com.maliopt.util;

import net.minecraft.client.gl.Framebuffer;

public final class FramebufferUtil {

    private FramebufferUtil() {}

    public static int getColorTextureId(Framebuffer fb) {
        // 1.21.11 — método público getColorAttachmentId()
        try {
            java.lang.reflect.Method m = fb.getClass().getMethod("getColorAttachmentId");
            return (int) m.invoke(fb);
        } catch (Exception ignored) {}
        // Fallback: procurar campo GpuTexture e extrair ID via reflexão
        try {
            for (java.lang.reflect.Field f : fb.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(fb);
                if (val != null && val.getClass().getSimpleName().contains("Texture")) {
                    for (java.lang.reflect.Field ff : val.getClass().getDeclaredFields()) {
                        if (ff.getType() == int.class) {
                            ff.setAccessible(true);
                            int id = ff.getInt(val);
                            if (id > 0) return id;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static int getFboId(Framebuffer fb) {
        // 1.21.11 — método público getFramebufferId()
        try {
            java.lang.reflect.Method m = fb.getClass().getMethod("getFramebufferId");
            return (int) m.invoke(fb);
        } catch (Exception ignored) {}
        // Fallback: procurar campo GlFramebuffer
        try {
            for (java.lang.reflect.Field f : fb.getClass().getSuperclass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(fb);
                if (val != null && val.getClass().getSimpleName().equals("GlFramebuffer")) {
                    for (java.lang.reflect.Field ff : val.getClass().getDeclaredFields()) {
                        if (ff.getType() == int.class) {
                            ff.setAccessible(true);
                            return ff.getInt(val);
                        }
                    }
                }
            }
            // Último fallback — primeiro int > 0 que não seja dimensão
            for (java.lang.reflect.Field f : fb.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    int val = f.getInt(fb);
                    if (val > 0 && val != fb.textureWidth && val != fb.textureHeight) return val;
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}

package com.maliopt.pipeline;

import com.maliopt.MaliOptMod;
import com.maliopt.config.MaliOptVisualConfig;
import com.maliopt.shader.ShaderExecutionLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.*;

public final class ShadowPass {

    private static int shadowFbo      = 0;
    private static int shadowDepthTex = 0;
    private static int progShadow     = 0;
    private static int progApply      = 0;
    private static int quadVao        = 0;
    private static int lastRes        = 0;
    private static boolean ready      = false;

    private static int uLightMatrix = -1;
    private static int uShadowMap   = -1;
    private static int uScene       = -1;
    private static int uPCFEnabled  = -1;
    private static int uShadowBias  = -1;

    private static final String VERT_SHADOW =
        "#version 310 es\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "uniform mat4 uLightSpaceMatrix;\n" +
        "void main() { gl_Position = uLightSpaceMatrix * vec4(aPos, 1.0); }\n";

    private static final String FRAG_SHADOW =
        "#version 310 es\n" +
        "precision mediump float;\n" +
        "out vec4 fragColor;\n" +
        "void main() { fragColor = vec4(1.0); }\n";

    private static final String VERT_APPLY =
        "#version 310 es\n" +
        "out vec2 vUv;\n" +
        "void main() {\n" +
        "    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n" +
        "    vUv = uv;\n" +
        "    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);\n" +
        "}\n";

    private static final String FRAG_APPLY =
        "#version 310 es\n" +
        "precision mediump float;\n" +
        "precision lowp sampler2DShadow;\n" +
        "uniform sampler2D uScene;\n" +
        "uniform sampler2DShadow uShadowMap;\n" +
        "uniform float uShadowBias;\n" +
        "uniform int   uPCFEnabled;\n" +
        "in vec2 vUv;\n" +
        "out vec4 fragColor;\n" +
        "float sampleShadow(vec3 p) {\n" +
        "    if (uPCFEnabled == 0) return texture(uShadowMap, p);\n" +
        "    vec2 ts = vec2(1.0) / vec2(textureSize(uShadowMap, 0));\n" +
        "    float s = 0.0;\n" +
        "    for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++)\n" +
        "        s += texture(uShadowMap, p + vec3(vec2(x,y)*ts, 0.0));\n" +
        "    return s / 9.0;\n" +
        "}\n" +
        "void main() {\n" +
        "    vec4 sc = texture(uScene, vUv);\n" +
        "    float sf = sampleShadow(vec3(vUv, 0.5 - uShadowBias));\n" +
        "    fragColor = vec4(sc.rgb * mix(0.60, 1.0, sf), sc.a);\n" +
        "}\n";

    public static void init() {
        MaliOptVisualConfig cfg = MaliOptVisualConfig.get();
        try {
            progShadow = buildProgram(VERT_SHADOW, FRAG_SHADOW, "Shadow_Depth");
            progApply  = buildProgram(VERT_APPLY,  FRAG_APPLY,  "Shadow_Apply");
            if (progShadow == 0 || progApply == 0) {
                MaliOptMod.LOGGER.error("[MaliOpt] ShadowPass: compilação falhou — desativado");
                return;
            }
            cacheUniforms();
            quadVao = GL30.glGenVertexArrays();
            rebuildShadowFbo(cfg.shadowResolution);
        } catch (Exception e) {
            MaliOptMod.LOGGER.error("[MaliOpt] ShadowPass.init() excepção: {}", e.getMessage());
        }
    }

    private static void rebuildShadowFbo(int res) {
        if (shadowFbo != 0)      { GL30.glDeleteFramebuffers(shadowFbo);  shadowFbo = 0; }
        if (shadowDepthTex != 0) { GL11.glDeleteTextures(shadowDepthTex); shadowDepthTex = 0; }
        shadowDepthTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowDepthTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT24,
            res, res, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        shadowFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, shadowDepthTex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            MaliOptMod.LOGGER.error("[MaliOpt] ShadowPass FBO incompleto — desativado");
            GL30.glDeleteFramebuffers(shadowFbo); GL11.glDeleteTextures(shadowDepthTex);
            shadowFbo = 0; shadowDepthTex = 0; ready = false; return;
        }
        lastRes = res; ready = true;
        MaliOptMod.LOGGER.info("[MaliOpt] ✅ ShadowPass FBO {}x{} criado", res, res);
    }

    public static void render(MinecraftClient mc) {
        if (!ready || mc.world == null) return;
        MaliOptVisualConfig cfg = MaliOptVisualConfig.get();
        if (!cfg.shadowsEnabled) return;
        if (cfg.shadowResolution != lastRes) { rebuildShadowFbo(cfg.shadowResolution); if (!ready) return; }

        Framebuffer fb = mc.getFramebuffer();
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (w <= 0 || h <= 0) return;

        int prevFbo     = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean depth   = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
        GL11.glViewport(0, 0, lastRes, lastRes);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, com.maliopt.util.FramebufferUtil.getFboId(fb));
        GL11.glViewport(0, 0, w, h);
        GL20.glUseProgram(progApply);
        GL20.glUniform1f(uShadowBias, 0.005f);
        GL20.glUniform1i(uPCFEnabled, cfg.shadowSoftEnabled ? 1 : 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, com.maliopt.util.FramebufferUtil.getColorTextureId(fb));
        GL20.glUniform1i(uScene, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowDepthTex);
        GL20.glUniform1i(uShadowMap, 1);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(quadVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);

        int[] attachments = { GL30.GL_DEPTH_ATTACHMENT };
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
        GL43.glInvalidateFramebuffer(GL43.GL_FRAMEBUFFER, attachments);

        GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL20.glUseProgram(prevProgram);
        if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public static void cleanup() {
        if (progShadow     != 0) { GL20.glDeleteProgram(progShadow);       progShadow     = 0; }
        if (progApply      != 0) { GL20.glDeleteProgram(progApply);        progApply      = 0; }
        if (quadVao        != 0) { GL30.glDeleteVertexArrays(quadVao);     quadVao        = 0; }
        if (shadowFbo      != 0) { GL30.glDeleteFramebuffers(shadowFbo);   shadowFbo      = 0; }
        if (shadowDepthTex != 0) { GL11.glDeleteTextures(shadowDepthTex);  shadowDepthTex = 0; }
        ready = false;
    }

    public static boolean isReady() { return ready; }

    private static void cacheUniforms() {
        GL20.glUseProgram(progApply);
        uScene      = GL20.glGetUniformLocation(progApply, "uScene");
        uShadowMap  = GL20.glGetUniformLocation(progApply, "uShadowMap");
        uShadowBias = GL20.glGetUniformLocation(progApply, "uShadowBias");
        uPCFEnabled = GL20.glGetUniformLocation(progApply, "uPCFEnabled");
        GL20.glUseProgram(progShadow);
        uLightMatrix = GL20.glGetUniformLocation(progShadow, "uLightSpaceMatrix");
        GL20.glUseProgram(0);
    }

    private static int buildProgram(String vert, String frag, String name) {
        int v = ShaderExecutionLayer.compile(GL20.GL_VERTEX_SHADER,   vert, name + "_vert");
        int f = ShaderExecutionLayer.compile(GL20.GL_FRAGMENT_SHADER, frag, name + "_frag");
        if (v == 0 || f == 0) { if (v != 0) GL20.glDeleteShader(v); if (f != 0) GL20.glDeleteShader(f); return 0; }
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, v); GL20.glAttachShader(prog, f);
        GL20.glLinkProgram(prog);
        GL20.glDeleteShader(v); GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            MaliOptMod.LOGGER.error("[MaliOpt] {} link falhou: {}", name, GL20.glGetProgramInfoLog(prog));
            GL20.glDeleteProgram(prog); return 0;
        }
        return prog;
    }
}

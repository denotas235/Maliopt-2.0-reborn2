#include <jni.h>
// Outros includes existentes...
// (supomos que o ficheiro já existe; na dúvida, vamos verificar)

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

JNIEXPORT jobjectArray JNICALL
Java_com_maliopt_MaliOptNative_getSystemGLESExtensions(JNIEnv* env, jclass clazz) {
    // Carrega libEGL e libGLESv2 do sistema
    void* egl_handle = dlopen("libEGL.so", RTLD_NOW | RTLD_LOCAL);
    if (!egl_handle) return NULL;
    void* gles_handle = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
    if (!gles_handle) {
        dlclose(egl_handle);
        return NULL;
    }

    // Ponteiros de função
    typedef EGLDisplay (*PFN_eglGetDisplay)(EGLNativeDisplayType display_id);
    typedef EGLBoolean (*PFN_eglInitialize)(EGLDisplay dpy, EGLint* major, EGLint* minor);
    typedef EGLBoolean (*PFN_eglChooseConfig)(EGLDisplay dpy, const EGLint* attribs, EGLConfig* configs, EGLint config_size, EGLint* num_config);
    typedef EGLSurface (*PFN_eglCreatePbufferSurface)(EGLDisplay dpy, EGLConfig config, const EGLint* attrib_list);
    typedef EGLContext (*PFN_eglCreateContext)(EGLDisplay dpy, EGLConfig config, EGLContext share, const EGLint* attrib_list);
    typedef EGLBoolean (*PFN_eglMakeCurrent)(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
    typedef EGLBoolean (*PFN_eglDestroySurface)(EGLDisplay dpy, EGLSurface surface);
    typedef EGLBoolean (*PFN_eglDestroyContext)(EGLDisplay dpy, EGLContext ctx);
    typedef EGLBoolean (*PFN_eglTerminate)(EGLDisplay dpy);
    typedef const GLubyte* (*PFN_glGetString)(GLenum name);
    #define GL_EXTENSIONS 0x1F03

    PFN_eglGetDisplay eglGetDisplay = (PFN_eglGetDisplay)dlsym(egl_handle, "eglGetDisplay");
    PFN_eglInitialize eglInitialize = (PFN_eglInitialize)dlsym(egl_handle, "eglInitialize");
    PFN_eglChooseConfig eglChooseConfig = (PFN_eglChooseConfig)dlsym(egl_handle, "eglChooseConfig");
    PFN_eglCreatePbufferSurface eglCreatePbufferSurface = (PFN_eglCreatePbufferSurface)dlsym(egl_handle, "eglCreatePbufferSurface");
    PFN_eglCreateContext eglCreateContext = (PFN_eglCreateContext)dlsym(egl_handle, "eglCreateContext");
    PFN_eglMakeCurrent eglMakeCurrent = (PFN_eglMakeCurrent)dlsym(egl_handle, "eglMakeCurrent");
    PFN_eglDestroySurface eglDestroySurface = (PFN_eglDestroySurface)dlsym(egl_handle, "eglDestroySurface");
    PFN_eglDestroyContext eglDestroyContext = (PFN_eglDestroyContext)dlsym(egl_handle, "eglDestroyContext");
    PFN_eglTerminate eglTerminate = (PFN_eglTerminate)dlsym(egl_handle, "eglTerminate");
    PFN_glGetString glGetString = (PFN_glGetString)dlsym(gles_handle, "glGetString");

    if (!eglGetDisplay || !eglInitialize || !eglChooseConfig || !eglCreatePbufferSurface ||
        !eglCreateContext || !eglMakeCurrent || !eglDestroySurface || !eglDestroyContext ||
        !eglTerminate || !glGetString) {
        dlclose(gles_handle);
        dlclose(egl_handle);
        return NULL;
    }

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (dpy == EGL_NO_DISPLAY) {
        dlclose(gles_handle); dlclose(egl_handle);
        return NULL;
    }

    EGLint major, minor;
    if (!eglInitialize(dpy, &major, &minor)) {
        dlclose(gles_handle); dlclose(egl_handle);
        return NULL;
    }

    const EGLint configAttribs[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                                      EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                                      EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                                      EGL_NONE };
    EGLConfig cfg;
    EGLint n;
    if (!eglChooseConfig(dpy, configAttribs, &cfg, 1, &n) || n == 0) {
        eglTerminate(dpy);
        dlclose(gles_handle); dlclose(egl_handle);
        return NULL;
    }

    const EGLint pbAttr[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    EGLSurface surf = eglCreatePbufferSurface(dpy, cfg, pbAttr);
    if (surf == EGL_NO_SURFACE) {
        eglTerminate(dpy);
        dlclose(gles_handle); dlclose(egl_handle);
        return NULL;
    }

    const EGLint ctxAttr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    if (ctx == EGL_NO_CONTEXT) {
        eglDestroySurface(dpy, surf);
        eglTerminate(dpy);
        dlclose(gles_handle); dlclose(egl_handle);
        return NULL;
    }

    if (!eglMakeCurrent(dpy, surf, surf, ctx)) {
        eglDestroyContext(dpy, ctx);
        eglDestroySurface(dpy, surf);
        eglTerminate(dpy);
        dlclose(gles_handle); dlclose(egl_handle);
        return NULL;
    }

    const GLubyte* extStr = glGetString(GL_EXTENSIONS);
    jobjectArray result = NULL;
    jclass strClass = (*env)->FindClass(env, "java/lang/String");

    if (extStr) {
        // Copia porque strtok modifica a string
        char* copy = strdup((const char*)extStr);
        int count = 0;
        char* tok = strtok(copy, " ");
        while (tok) { count++; tok = strtok(NULL, " "); }
        free(copy);

        result = (*env)->NewObjectArray(env, count, strClass, NULL);
        copy = strdup((const char*)extStr);
        tok = strtok(copy, " ");
        int i = 0;
        while (tok) {
            jstring s = (*env)->NewStringUTF(env, tok);
            (*env)->SetObjectArrayElement(env, result, i++, s);
            (*env)->DeleteLocalRef(env, s);
            tok = strtok(NULL, " ");
        }
        free(copy);
    } else {
        result = (*env)->NewObjectArray(env, 0, strClass, NULL);
    }

    // Cleanup
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(dpy, ctx);
    eglDestroySurface(dpy, surf);
    eglTerminate(dpy);
    dlclose(gles_handle);
    dlclose(egl_handle);
    return result;
}

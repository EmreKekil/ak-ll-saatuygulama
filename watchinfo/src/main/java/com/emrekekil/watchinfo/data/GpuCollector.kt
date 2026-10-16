package com.emrekekil.watchinfo.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import com.emrekekil.watchinfo.R
import com.emrekekil.watchinfo.model.InfoGroup
import com.emrekekil.watchinfo.model.infoGroups

object GpuCollector {

    private class GlInfo(
        val vendor: String?,
        val renderer: String?,
        val version: String?,
        val shadingLanguage: String?,
        val extensionCount: Int,
        val maxTextureSize: Int,
        val eglVendor: String?,
        val eglVersion: String?,
    )

    // GPU bilgisi değişmez; EGL bağlamı kurmak pahalı olduğu için bir kez okunur.
    private val glInfo: GlInfo? by lazy { queryGl() }

    fun summary(): String? = glInfo?.renderer

    fun collect(context: Context): List<InfoGroup> {
        val gl = glInfo
        val pm = context.packageManager
        val features = pm.systemAvailableFeatures
        val vulkanVersion = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version
        val vulkanLevel = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }?.version
        val glEsCode = context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion

        return infoGroups {
            group(context.getString(R.string.group_gpu)) {
                item(context.getString(R.string.label_gpu_renderer), gl?.renderer)
                item(context.getString(R.string.label_gpu_vendor), gl?.vendor)
                item(context.getString(R.string.label_gpu_driver), gl?.version)
                item(context.getString(R.string.label_max_texture), gl?.maxTextureSize?.takeIf { it > 0 }?.let { "$it × $it" })
            }
            group(context.getString(R.string.group_graphics_api)) {
                item(context.getString(R.string.label_opengl_es), Format.glEsVersion(glEsCode))
                item(context.getString(R.string.label_glsl), gl?.shadingLanguage)
                item(context.getString(R.string.label_gl_extensions), gl?.extensionCount?.takeIf { it > 0 }?.toString())
                item(
                    context.getString(R.string.label_vulkan),
                    vulkanVersion?.let { Format.vulkanVersion(it) } ?: context.getString(R.string.value_not_supported),
                )
                item(context.getString(R.string.label_vulkan_level), vulkanLevel?.toString())
                item(
                    context.getString(R.string.label_vulkan_compute),
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE).takeIf { vulkanVersion != null }
                        ?.let { context.supported(it) },
                )
                item(context.getString(R.string.label_egl), listOfNotNull(gl?.eglVersion, gl?.eglVendor).joinToString(" · "))
            }
        }
    }

    /**
     * Ekrana bağlı olmayan 1×1 piksellik bir EGL yüzeyi oluşturup GL sürücüsünü sorgular.
     * Varsayılan EGL ekranı arayüz çizimiyle paylaşıldığı için eglTerminate çağrılmaz.
     */
    @Synchronized
    private fun queryGl(): GlInfo? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            return null
        }
        val config = configs[0] ?: return null
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )

        return try {
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) {
                return null
            }
            val maxTexture = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            GlInfo(
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                version = GLES20.glGetString(GLES20.GL_VERSION),
                shadingLanguage = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION),
                extensionCount = GLES20.glGetString(GLES20.GL_EXTENSIONS)
                    ?.split(' ')?.count { it.isNotBlank() } ?: 0,
                maxTextureSize = maxTexture[0],
                eglVendor = EGL14.eglQueryString(display, EGL14.EGL_VENDOR),
                eglVersion = EGL14.eglQueryString(display, EGL14.EGL_VERSION)?.let { "EGL $it" },
            )
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }
}

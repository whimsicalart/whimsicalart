package com.whimsicalart.feature.filters.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FrameBufferObject {
    private var fboId = 0
    private var textureId = 0
    private var depthBufferId = 0
    private var width = 0
    private var height = 0

    fun create(width: Int, height: Int) {
        this.width = width
        this.height = height

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        fboId = fboIds[0]

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val depthBufferIds = IntArray(1)
        GLES30.glGenRenderbuffers(1, depthBufferIds, 0)
        depthBufferId = depthBufferIds[0]

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthBufferId)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, width, height)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textureId, 0
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER, depthBufferId
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete: $status")
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)
    }

    fun unbind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun getTextureId(): Int = textureId

    fun delete() {
        val fboIds = intArrayOf(fboId)
        GLES30.glDeleteFramebuffers(1, fboIds, 0)

        val textureIds = intArrayOf(textureId)
        GLES30.glDeleteTextures(1, textureIds, 0)

        val depthBufferIds = intArrayOf(depthBufferId)
        GLES30.glDeleteRenderbuffers(1, depthBufferIds, 0)
    }

    fun getWidth(): Int = width
    fun getHeight(): Int = height
}

package com.gtnewhorizons.angelica.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLRedirectsTest {

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @CsvSource({
        "GL11, glPushMatrix, glPushMatrix",
        "GL11, glPopMatrix, glPopMatrix",
        "GL11, glTranslatef, glTranslatef",
        "GL11, glRotatef, glRotatef",
        "GL11, glScalef, glScalef",
        "GL11, glEnable, glEnable",
        "GL11, glDisable, glDisable",
        "GL11, glBegin, glBegin",
        "GL11, glEnd, glEnd",
        "GL11, glVertex3f, glVertex3f",
        "GL11, glColor4f, glColor4f",
        "GL11, glNormal3f, glNormal3f",
        "GL11, glTexCoord2f, glTexCoord2f",
        "GL11, glBindTexture, glBindTexture",
        "GL11, glBlendFunc, glBlendFunc",
        "GL11, glDepthMask, glDepthMask",
        "GL11, glDepthFunc, glDepthFunc",
        "GL11, glAlphaFunc, glAlphaFunc",
        "GL11, glMatrixMode, glMatrixMode",
        "GL11, glLoadIdentity, glLoadIdentity",
        "GL11, glShadeModel, glShadeModel",
        "GL11, glDrawArrays, glDrawArrays",
        "GL11, glDrawElements, glDrawElements",
        "GL11, glNewList, glNewList",
        "GL11, glEndList, glEndList",
        "GL11, glCallList, glCallList",
        "GL11, glViewport, glViewport",
        "GL12, glTexImage3D, glTexImage3D",
        "GL13, glActiveTexture, glActiveTexture",
        "GL13, glMultiTexCoord2f, glMultiTexCoord2f",
        "GL14, glBlendFuncSeparate, tryBlendFuncSeparate",
        "GL14, glBlendColor, glBlendColor",
        "GL15, glGenBuffers, glGenBuffers",
        "GL15, glBindBuffer, glBindBuffer",
        "GL15, glBufferData, glBufferData",
        "GL15, glDeleteBuffers, glDeleteBuffers",
        "GL20, glUseProgram, glUseProgram",
        "GL20, glCreateShader, glCreateShader",
        "GL20, glShaderSource, glShaderSource",
        "GL20, glLinkProgram, glLinkProgram",
        "GL20, glUniform1f, glUniform1f",
        "GL20, glUniform4f, glUniform4f",
        "GL20, glGetUniformLocation, glGetUniformLocation",
        "GL20, glVertexAttribPointer, glVertexAttribPointer",
        "GL30, glGenFramebuffers, glGenFramebuffers",
        "GL30, glBindFramebuffer, glBindFramebuffer",
        "GL30, glGenVertexArrays, glGenVertexArrays",
        "GL30, glBindVertexArray, glBindVertexArray",
        "GL30, glGenerateMipmap, glGenerateMipmap",
        "GL31, glDrawElementsInstanced, glDrawElementsInstanced",
        "GL33, glGenSamplers, glGenSamplers",
        "GL33, glBindSampler, glBindSampler",
        "GL42, glTexStorage2D, glTexStorage2D",
        "GL43, glDispatchCompute, glDispatchCompute",
        "GL44, glBufferStorage, glBufferStorage",
    })
    void glVersionClassMethodRedirects(String className, String method, String expected) {
        assertEquals(expected, GLRedirects.getTargetMethodName(className + "." + method));
    }

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @CsvSource({
        "GL11C, glDrawArrays, glDrawArrays",
        "GL20C, glUseProgram, glUseProgram",
        "GL30C, glBindFramebuffer, glBindFramebuffer",
        "GL33C, glBindSampler, glBindSampler",
    })
    void cVariantsUseGLPrefixMap(String className, String method, String expected) {
        assertEquals(expected, GLRedirects.getTargetMethodName(className + "." + method));
    }

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @CsvSource({
        "EXTBlendFuncSeparate, glBlendFuncSeparateEXT, tryBlendFuncSeparate",
        "ARBMultitexture, glActiveTextureARB, glActiveTextureARB",
        "ARBShaderObjects, glUseProgramObjectARB, glUseProgram",
        "ARBShaderObjects, glCreateShaderObjectARB, glCreateShader",
        "ARBShaderObjects, glCompileShaderARB, glCompileShader",
        "ARBShaderObjects, glAttachObjectARB, glAttachShader",
        "ARBShaderObjects, glUniform1fARB, glUniform1f",
        "ARBShaderObjects, glUniformMatrix4ARB, glUniformMatrix4",
        "ARBShaderObjects, glGetUniformLocationARB, glGetUniformLocation",
        "ARBVertexArrayObject, glBindVertexArray, glBindVertexArray",
        "ARBInstancedArrays, glVertexAttribDivisorARB, glVertexAttribDivisorARB",
        "APPLEVertexArrayObject, glBindVertexArrayAPPLE, glBindVertexArray",
        "UniversalVAO, bindVertexArray, glBindVertexArray",
        "UniversalVAO, deleteVertexArrays, glDeleteVertexArrays",
        "OpenGlHelper, glBlendFunc, tryBlendFuncSeparate",
        "OpenGlHelper, setActiveTexture, setActiveTexture",
        "OpenGlHelper, setLightmapTextureCoords, setLightmapTextureCoords",
        "OpenGlHelper, isFramebufferEnabled, isFramebufferEnabled",
        "GLU, gluPerspective, gluPerspective",
        "GLU, gluLookAt, gluLookAt",
        "GLU, gluOrtho2D, gluOrtho2D",
        "Project, gluPerspective, gluPerspective",
        "Project, gluPickMatrix, gluPickMatrix",
    })
    void namedClassMethodRedirects(String className, String method, String expected) {
        assertEquals(expected, GLRedirects.getTargetMethodName(className + "." + method));
    }

    @ParameterizedTest(name = "{0} -> null")
    @CsvSource({
        "GL11.GL_TEXTURE_2D",
        "GL11.GL_BLEND",
        "GL20.GL_FRAGMENT_SHADER",
        "Foo.bar",
        "String.valueOf",
        "ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB",
    })
    void nonRedirectedReturnsNull(String classAndMethod) {
        assertNull(GLRedirects.getTargetMethodName(classAndMethod));
    }

    @Test
    void nullDotReturnsNull() {
        assertNull(GLRedirects.getTargetMethodName("noDotHere"));
    }

    @ParameterizedTest(name = "pattern matches {0}")
    @CsvSource({
        "GL11.glPushMatrix",
        "GL20C.glUseProgram",
        "GL44.glBufferStorage",
        "ARBShaderObjects.glUseProgramObjectARB",
        "OpenGlHelper.setActiveTexture",
        "GLU.gluPerspective",
        "UniversalVAO.bindVertexArray",
    })
    void patternMatchesRedirectedClasses(String input) {
        assertTrue(GLRedirects.getMethodRedirectPattern().matcher(input).find(),
            "Pattern should match: " + input);
    }

    @ParameterizedTest(name = "pattern does not match {0}")
    @CsvSource({
        "String.valueOf",
        "Math.abs",
        "Tessellator.instance",
        "System.currentTimeMillis",
    })
    void patternDoesNotMatchUnrelatedClasses(String input) {
        assertFalse(GLRedirects.getMethodRedirectPattern().matcher(input).find(),
            "Pattern should not match: " + input);
    }

    @Test
    void endToEndScriptTransformation() {
        String script = "GL11.glPushMatrix();\nGL11.glTranslatef(1,2,3);\nGL11.glEnable(GL11.GL_BLEND);";

        String result = GLRedirects.getMethodRedirectPattern().matcher(script).replaceAll(mr -> {
            String target = GLRedirects.getTargetMethodName(mr.group(0));
            return target != null ? "GLStateManager." + target : mr.group(0);
        });

        assertTrue(result.contains("GLStateManager.glPushMatrix()"), "glPushMatrix not redirected: " + result);
        assertTrue(result.contains("GLStateManager.glTranslatef(1,2,3)"), "glTranslatef not redirected: " + result);
        assertTrue(result.contains("GLStateManager.glEnable(GL11.GL_BLEND)"), "glEnable not redirected or GL_BLEND mangled: " + result);
    }

    @Test
    void patternIsNotNull() {
        assertNotNull(GLRedirects.getMethodRedirectPattern());
    }
}

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File

private const val LWJGL3_AWARE_DESC = "Lme/eigenraven/lwjgl3ify/api/Lwjgl3Aware;"

/**
 * Stamps @Lwjgl3Aware onto every class under [classesDir] - classes that already carry the annotation are left untouched.
 */
fun injectLwjgl3Aware(classesDir: File) {
    if (!classesDir.isDirectory) return

    classesDir.walkTopDown()
        .filter { it.isFile && it.extension == "class" && it.name != "module-info.class" }
        .forEach { classFile ->
            val reader = ClassReader(classFile.readBytes())
            if (hasLwjgl3Aware(reader)) return@forEach

            val writer = ClassWriter(reader, 0)
            reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visit(
                    version: Int,
                    access: Int,
                    name: String?,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    super.visit(version, access, name, signature, superName, interfaces)
                    super.visitAnnotation(LWJGL3_AWARE_DESC, true)?.visitEnd()
                }
            }, 0)
            classFile.writeBytes(writer.toByteArray())
        }
}

private fun hasLwjgl3Aware(reader: ClassReader): Boolean {
    var found = false
    reader.accept(object : ClassVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            if (descriptor == LWJGL3_AWARE_DESC) found = true
            return null
        }
    }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return found
}

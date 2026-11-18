package com.gtnewhorizons.angelica.experimental.surround.ap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.tools.obfuscation.mirror.AnnotationHandle;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("com.gtnewhorizons.angelica.experimental.surround.Surround")
public class SurroundProcessor extends AbstractProcessor {

    record Registration(TypeElement mixin, ExecutableElement method, AnnotationMirror surround) {
    }

    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            if (!this.registrations.isEmpty()) {
                register();
            }
            return false;
        }
        for (TypeElement annotation : annotations) {
            for (Element annotated : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (annotated.getEnclosingElement() instanceof TypeElement mixin && AnnotationHandle.of(mixin, Mixin.class).exists()) {
                    for (AnnotationMirror mirror : annotated.getAnnotationMirrors()) {
                        if (mirror.getAnnotationType().asElement().equals(annotation)) {
                            this.registrations.add(new Registration(mixin, (ExecutableElement) annotated, mirror));
                            break;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void register() {
        final ProcessingEnvironment env = this.processingEnv;
        try {
            // pinned by UniMixins 0.3.1; recheck on bump
            final Class<?> annotatedMixins = Class.forName("org.spongepowered.tools.obfuscation.AnnotatedMixins", false, AnnotationHandle.class.getClassLoader());
            final Method forEnvironment = annotatedMixins.getDeclaredMethod("getMixinsForEnvironment", ProcessingEnvironment.class);
            final Method registerInjector = annotatedMixins.getDeclaredMethod("registerInjector", TypeElement.class, ExecutableElement.class, AnnotationHandle.class);
            final Method writeReferences = annotatedMixins.getDeclaredMethod("writeReferences");
            forEnvironment.setAccessible(true);
            registerInjector.setAccessible(true);
            writeReferences.setAccessible(true);
            final Object mixins = forEnvironment.invoke(null, env);
            for (Registration registration : this.registrations) {
                registerInjector.invoke(mixins, registration.mixin(), registration.method(), AnnotationHandle.of(registration.surround()));
            }
            writeReferences.invoke(mixins);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, "@Surround: refmap entries were not written: " + e + (e.getCause() == null ? "" : " caused by " + e.getCause()));
        }
    }
}

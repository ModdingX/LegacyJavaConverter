package org.moddingx.ljc.util;

import org.moddingx.ljc.LanguageLevel;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

import java.util.function.BiFunction;

public class MultipleRoundVisitor extends ClassNode {

    private final int api;
    private final BiFunction<Integer, ClassVisitor, ChangeAwareClassVisitor> round;
    private final ClassVisitor parent;

    public MultipleRoundVisitor(int api, BiFunction<Integer, ClassVisitor, ChangeAwareClassVisitor> round, ClassVisitor parent) {
        super(api);
        this.api = api;
        this.round = round;
        this.parent = parent;
    }
    
    @Override
    public void visitEnd() {
        super.visitEnd();
        boolean needsNextRound = true;
        ClassNode current = this;
        while (needsNextRound) {
            ClassNode newNode = new ClassNode();
            ChangeAwareClassVisitor visitor = this.round.apply(this.api, newNode);
            current.accept(visitor);
            current = newNode;
            needsNextRound = visitor.hasChanged();
        }
        current.accept(this.parent);
    }
    
    public static LanguageLevel.ConverterFactory of(BiFunction<Integer, ClassVisitor, ChangeAwareClassVisitor> round) {
        return (api, parent) -> new MultipleRoundVisitor(api, round, parent);
    }
}

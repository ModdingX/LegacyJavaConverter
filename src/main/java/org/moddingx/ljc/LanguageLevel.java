package org.moddingx.ljc;

import org.moddingx.ljc.convert.j11.ExplicitConstants;
import org.moddingx.ljc.convert.j11.NestHostToPackage;
import org.moddingx.ljc.convert.j16.RecordToClass;
import org.moddingx.ljc.convert.j17.AlwaysStrictFP;
import org.moddingx.ljc.convert.j17.UnsealClasses;
import org.moddingx.ljc.convert.j9.DynamicStringConcat;
import org.moddingx.ljc.convert.j9.ModuleRemover;
import org.moddingx.ljc.util.MultipleRoundVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.util.function.Function;

public enum LanguageLevel {
    // JAVA_8 downgrades to java 8
    JAVA_8(8, Opcodes.ASM5, Opcodes.V1_8, '8'),
    JAVA_9(9, Opcodes.ASM6, Opcodes.V9, '9', ModuleRemover::new, DynamicStringConcat::new),
    JAVA_10(10, Opcodes.ASM6, Opcodes.V10, 'A'),
    JAVA_11(11, Opcodes.ASM7, Opcodes.V11, 'B', MultipleRoundVisitor.of(ExplicitConstants::new), NestHostToPackage::new),
    JAVA_12(12, Opcodes.ASM7, Opcodes.V12, 'C'),
    JAVA_13(13, Opcodes.ASM7, Opcodes.V13, 'D'),
    JAVA_14(14, Opcodes.ASM7, Opcodes.V14, 'E'),
    JAVA_15(15, Opcodes.ASM7, Opcodes.V15, 'F'),
    JAVA_16(16, Opcodes.ASM8, Opcodes.V16, 'G', RecordToClass::new),
    JAVA_17(17, Opcodes.ASM9, Opcodes.V17, 'H', AlwaysStrictFP::new, UnsealClasses::new),
    JAVA_18(18, Opcodes.ASM9, Opcodes.V18, 'I'),
    JAVA_19(19, Opcodes.ASM9, Opcodes.V19, 'J');
    
    private static final int TARGET_ASM = Opcodes.ASM9;
    
    public final int version;
    public final int asm;
    public final int jvm;
    public final char symbol;
    private final Function<ClassVisitor, ClassVisitor> converter;

    LanguageLevel(int version, int asm, int jvm, char symbol, ConverterFactory... converters) {
        this.version = version;
        this.asm = asm;
        this.jvm = jvm;
        this.symbol = symbol;
        
        this.converter = parent -> {
            // First added class visitor comes last
            ClassVisitor current = parent;
            for (int i = converters.length - 1; i >= 0; i--) {
                current = converters[i].create(TARGET_ASM, current);
            }
            return current;
        };
    }
    
    public ClassVisitor create(ClassVisitor parent) {
        return this.converter.apply(parent);
    }
    
    public static LanguageLevel of(int ver) {
        for (LanguageLevel level : LanguageLevel.values()) {
            if (level.version == ver) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unsupported java version: " + ver);
    }
    
    @FunctionalInterface
    public interface ConverterFactory {

        ClassVisitor create(int api, ClassVisitor parent);
    }
}

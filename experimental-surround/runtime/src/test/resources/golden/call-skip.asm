// class version 52.0 (52)
// access flags 0x21
public class com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget {


  // access flags 0x1
  public valueSkip(I)I
    TRYCATCHBLOCK L0 L1 L2 null
   L3
    ALOAD 0
    GETFIELD com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.callee : Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;
    ILOAD 1
    ISTORE 3
    ASTORE 2
   L4
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/targets/Trace.nextToken ()J
    LSTORE 4
   L5
    ILOAD 3
    IFGE L6
    ICONST_1
    GOTO L7
   L6
   FRAME APPEND [com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee I J]
    ICONST_0
   L7
   FRAME SAME1 I
    ISTORE 6
   L8
    ILOAD 3
    LLOAD 4
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.md$jvmdowngrader$concat$surround$enterValueSkip$1$50 (IJ)Ljava/lang/String;
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/targets/Trace.add (Ljava/lang/String;)V
   L9
    ILOAD 6
    IFEQ L10
   L0
    ALOAD 0
    ALOAD 2
    ILOAD 3
    LLOAD 4
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$skippedValueSkip (Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;IJ)I
    GOTO L1
   L10
   FRAME APPEND [I]
    ALOAD 2
    ILOAD 3
    INVOKEVIRTUAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee.twice (I)I
   L1
   FRAME SAME1 I
    ALOAD 0
    LLOAD 4
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$exitValueSkip (J)V
    GOTO L11
   L2
   FRAME SAME1 java/lang/Throwable
    ASTORE 7
    ALOAD 0
    LLOAD 4
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$exitValueSkip (J)V
    ALOAD 7
    ATHROW
   L11
   FRAME SAME1 I
    IRETURN
   L12
    LOCALVARIABLE this Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget; L3 L12 0
    LOCALVARIABLE a I L3 L12 1
    MAXSTACK = 5
    MAXLOCALS = 8
}

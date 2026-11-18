// class version 52.0 (52)
// access flags 0x21
public class com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget {


  // access flags 0x1
  public valueBelow(I)I
    TRYCATCHBLOCK L0 L1 L2 java/lang/IllegalStateException
    TRYCATCHBLOCK L3 L4 L5 null
    TRYCATCHBLOCK L0 L1 L5 null
   L6
    ILOAD 1
    ALOAD 0
    GETFIELD com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.callee : Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;
    ILOAD 1
    ISTORE 3
    ASTORE 2
    ISTORE 4
   L7
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/targets/Trace.nextToken ()J
    LSTORE 5
   L8
    ILOAD 3
    LLOAD 5
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.md$jvmdowngrader$concat$surround$enterValueBelow$1$3f (IJ)Ljava/lang/String;
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/targets/Trace.add (Ljava/lang/String;)V
   L9
    ILOAD 4
    ALOAD 2
    ILOAD 3
   L0
    INVOKEVIRTUAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee.explode (I)I
   L1
    ALOAD 0
    ALOAD 2
    ILOAD 3
    LLOAD 5
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$exitValueBelow (Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;IJ)V
    GOTO L10
   L2
   FRAME FULL [com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget I com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee I I J] [java/lang/IllegalStateException]
    ASTORE 7
    ILOAD 4
   L3
    ALOAD 0
    ALOAD 7
    ALOAD 2
    ILOAD 3
    LLOAD 5
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$handleValueBelow (Ljava/lang/IllegalStateException;Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;IJ)I
   L4
    ALOAD 0
    ALOAD 2
    ILOAD 3
    LLOAD 5
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$exitValueBelow (Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;IJ)V
    GOTO L10
   L5
   FRAME SAME1 java/lang/Throwable
    ASTORE 7
    ALOAD 0
    ALOAD 2
    ILOAD 3
    LLOAD 5
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget.surround$exitValueBelow (Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;IJ)V
    ALOAD 7
    ATHROW
   L10
   FRAME FULL [com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget I com/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee I I J] [I I]
    BIPUSH 10
    IMUL
    IADD
    IRETURN
   L11
    LOCALVARIABLE this Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget; L6 L11 0
    LOCALVARIABLE a I L6 L11 1
    MAXSTACK = 7
    MAXLOCALS = 8
}

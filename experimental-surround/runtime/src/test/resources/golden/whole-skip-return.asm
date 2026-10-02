// class version 52.0 (52)
// access flags 0x21
public class com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget {


  // access flags 0x1
  public skipAndReturn(Ljava/lang/String;)Ljava/lang/String;
    TRYCATCHBLOCK L0 L1 L2 java/lang/Throwable
    TRYCATCHBLOCK L3 L4 L5 null
    TRYCATCHBLOCK L1 L6 L5 null
    TRYCATCHBLOCK L0 L1 L5 null
   L7
    ALOAD 1
    INVOKEVIRTUAL java/lang/String.isEmpty ()Z
    ISTORE 2
   L8
    ALOAD 1
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.md$jvmdowngrader$concat$surround$enterSkipAndReturn$1 (Ljava/lang/String;)Ljava/lang/String;
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/targets/Trace.add (Ljava/lang/String;)V
   L9
    ILOAD 2
    IFEQ L10
   L0
    ALOAD 0
    ALOAD 1
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.surround$skippedSkipAndReturn (Ljava/lang/String;)Ljava/lang/String;
    GOTO L6
   L10
   FRAME APPEND [I]
    ALOAD 0
    ALOAD 1
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.skipAndReturn$surround (Ljava/lang/String;)Ljava/lang/String;
   L1
    ASTORE 4
    ALOAD 0
    ALOAD 4
    ALOAD 1
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.surround$returnSkipAndReturn (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
   L6
   FRAME SAME1 java/lang/String
    ALOAD 0
    ALOAD 1
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.surround$exitSkipAndReturn (Ljava/lang/String;)V
    GOTO L11
   L2
   FRAME SAME1 java/lang/Throwable
    ASTORE 3
   L3
    ALOAD 0
    ALOAD 3
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.surround$caughtSkipAndReturn (Ljava/lang/Throwable;)V
   L4
    ALOAD 0
    ALOAD 1
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.surround$exitSkipAndReturn (Ljava/lang/String;)V
    ALOAD 3
    ATHROW
   L5
   FRAME SAME1 java/lang/Throwable
    ASTORE 3
    ALOAD 0
    ALOAD 1
    INVOKESPECIAL com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.surround$exitSkipAndReturn (Ljava/lang/String;)V
    ALOAD 3
    ATHROW
   L11
   FRAME SAME1 java/lang/String
    ARETURN
   L12
    LOCALVARIABLE this Lcom/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget; L7 L12 0
    LOCALVARIABLE a Ljava/lang/String; L7 L12 1
    LOCALVARIABLE skip Z L8 L9 2
    LOCALVARIABLE surround$error Ljava/lang/Throwable; L2 L12 3
    LOCALVARIABLE surround$result Ljava/lang/String; L1 L6 4
    MAXSTACK = 3
    MAXLOCALS = 5

  // access flags 0x2
  private skipAndReturn$surround(Ljava/lang/String;)Ljava/lang/String;
   L0
    ALOAD 1
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.jvmdowngrader$concat$skipAndReturn$1 (Ljava/lang/String;)Ljava/lang/String;
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/targets/Trace.add (Ljava/lang/String;)V
   L1
    ALOAD 1
    INVOKESTATIC com/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget.jvmdowngrader$concat$skipAndReturn$2 (Ljava/lang/String;)Ljava/lang/String;
    ARETURN
   L2
    LOCALVARIABLE this Lcom/gtnewhorizons/angelica/experimental/surround/integration/WholeTarget; L0 L2 0
    LOCALVARIABLE a Ljava/lang/String; L0 L2 1
    MAXSTACK = 1
    MAXLOCALS = 2
}

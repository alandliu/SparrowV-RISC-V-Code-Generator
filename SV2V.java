import java.io.InputStream;

import IR.SparrowParser;
import IR.visitor.SparrowVConstructor;
import IR.syntaxtree.Node;
import IR.registers.Registers;

import sparrowv.Program;
import sparrowv.visitor.RiscVTranslateVisitor;

public class SV2V {
    public static void main(String[] args) throws Exception {
        Registers.SetRiscVregs();
        InputStream in = System.in;
        new SparrowParser(in);
        Node root = SparrowParser.Program();
        SparrowVConstructor constructor = new SparrowVConstructor();
        root.accept(constructor);
        Program program = constructor.getProgram();

        RiscVTranslateVisitor rvt = new RiscVTranslateVisitor();
        String riscv_translation = rvt.visit(program);
        System.out.println(riscv_translation);
        // System.err.println(program.toString());
    }
}

// compile
// javac $(find . -name "*.java")

// run translation
// java SV2V < test.sparrowv > test.riscv 2> err.txt

// run RISCV
// java -jar ../../../misc/venus.jar < test.riscv
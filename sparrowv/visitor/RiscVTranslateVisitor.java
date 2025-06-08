package sparrowv.visitor;

import sparrowv.*;

import java.util.List;

import IR.token.*;
import sparrowv.visitor.FrameDataVisitor;

public class RiscVTranslateVisitor implements RetVisitor<String> {

    // program generation constants
    String PROGRAM_HEADER = "  .equiv @sbrk, 9\r\n" + //
                "  .equiv @print_string, 4\r\n" + //
                "  .equiv @print_char, 11\r\n" + //
                "  .equiv @print_int, 1\r\n" + //
                "  .equiv @exit 10\r\n" + //
                "  .equiv @exit2, 17\r\n\r\n\r\n";
    
    String TEXT_SEGMENT = ".text\r\n" + //
                "\r\n" + //
                ".globl main\r\n" + //
                "  jal Main\r\n" + //
                "  li a0, @exit\r\n" + //
                "  ecall\r\n\r\n\r\n";

    String STD_DEF_PRINT = ".globl print\r\n" + //
                "print:\r\n" + //
                "  mv a1, a0\r\n" + //
                "  li a0, @print_int\r\n" + //
                "  ecall\r\n" + //
                "  li a1, 10\r\n" + //
                "  li a0, @print_char\r\n" + //
                "  ecall\r\n" + //
                "  jr ra\r\n\r\n";
    
    String STD_DEF_ERROR = ".globl error\r\n" + //
                "error:\r\n" + //
                "  mv a1, a0\r\n" + //
                "  li a0, @print_string\r\n" + //
                "  ecall\r\n" + //
                "  li a1, 10\r\n" + //
                "  li a0, @print_char\r\n" + //
                "  ecall\r\n" + //
                "  li a0, @exit\r\n" + //
                "  ecall\r\n" + //
                "abort_17:\r\n" + //
                "  j abort_17\r\n\r\n\r\n";

    String STD_DEF_ALLOC = ".globl alloc\r\n" + //
                "alloc:\r\n" + //
                "  mv a1, a0\r\n" + //
                "  li a0, @sbrk\r\n" + //
                "  ecall\r\n" + //
                "  jr ra\r\n\r\n\r\n";

    String DATA_SEG = ".data\r\n" + //
                "\r\n" + //
                ".globl msg_0\r\n" + //
                "msg_0:\r\n" + //
                "  .asciiz \"null pointer\"\r\n" + //
                "  .align 2\r\n" + //
                "\r\n" + //
                "\r\n" + //
                ".globl msg_array_oob\r\n" + //
                "msg_array_oob:\r\n" + //
                "  .asciiz \"array index out of bounds\"\r\n" + //
                "  .align 2\r\n\r\n";

    String MAIN_ID = "Main";

    // function generation constants
    String GLOBAL_ID = ".globl";

    String SAVE_CALLER_FRAME = "  sw fp, -8(sp)\r\n" + //
                                "  mv fp, sp\r\n";

    String ALLOCATE_FRAME_SIZE = "  sub sp, sp, t6\r\n";
    String STORE_RETURN_ADDR = "  sw ra, -4(fp)\r\n";

    String RESTORE_CALLER_RA = "  lw ra, -4(fp)\r\n";
    String RESTORE_CALLER_FP = "  lw fp, -8(fp)\r\n";
    String JUMP_TO_RETURN_ADDR = "  jr ra\r\n";

    String NULL_POINTER_MSG = "\"null pointer\"";
    String ARR_OOB_MSG = "\"array index out of bounds\"";

    // translation data structures
    FrameDataVisitor frame_data_manager;
    String current_function_id;
    int label_num;

    public RiscVTranslateVisitor() {
        frame_data_manager = new FrameDataVisitor();
        current_function_id = "";
        label_num = 0;
    }


    /*   List<FunctionDecl> funDecls; */
    public String visit(Program n) {
        String instr_seg = "";
        List<FunctionDecl> function_declarations = n.funDecls;
        
        // initialize ALL frame data
        frame_data_manager.generate_frame_data(n);
        
        instr_seg += PROGRAM_HEADER;
        instr_seg += TEXT_SEGMENT;

        for (FunctionDecl fd : function_declarations) {
            instr_seg += fd.accept(this);
        }
        instr_seg += STD_DEF_PRINT;
        instr_seg += STD_DEF_ERROR;
        instr_seg += STD_DEF_ALLOC;
        instr_seg += DATA_SEG;
        return instr_seg;
    }

    /*   Program parent;
    *   FunctionName functionName;
    *   List<Identifier> formalParameters;
    *   Block block; */
    public String visit(FunctionDecl n) {
        String instr_seg = "";
        String function_name = n.functionName.toString();
        Block block = n.block;

        if (function_name.toLowerCase().equals("main")) function_name = MAIN_ID;
        current_function_id = function_name;
        instr_seg += GLOBAL_ID + " " + function_name + "\n";
        instr_seg += function_name + ":\n";

        instr_seg += generate_new_frame(function_name);

        instr_seg += block.accept(this);
        instr_seg += "\r\n\r\n";
        return instr_seg;
    }

    public String generate_new_frame(String func_id) {
        String new_frame_seg = "";
        int new_frame_size = frame_data_manager.func_frame_size.get(func_id);

        new_frame_seg += SAVE_CALLER_FRAME;
        new_frame_seg += "  li t6, " + new_frame_size + "\r\n";
        new_frame_seg += ALLOCATE_FRAME_SIZE;
        new_frame_seg += STORE_RETURN_ADDR;

        return new_frame_seg;
    }

    /*   FunctionDecl parent;
    *   List<Instruction> instructions;
    *   Identifier return_id; */
    public String visit(Block n) {
        String instr_seg = "";
        List<Instruction> instructions = n.instructions;
        String return_id = n.return_id.toString();

        for (Instruction instr : instructions) {
            instr_seg += instr.accept(this);
        }
        instr_seg += restore_caller_frame(current_function_id, return_id);

        return instr_seg;
    }

    public String restore_caller_frame(String func_id, String return_id) {
        String restore_frame_seg = "";
        int offset = 0;
        int func_frame_size = frame_data_manager.func_frame_size.get(func_id);
        int func_arg_size = frame_data_manager.get_func_arg_size(func_id);

        if (frame_data_manager.is_parameter_var(func_id, return_id)) {
            offset = frame_data_manager.func_argument_offsets.get(func_id).get(return_id);
            restore_frame_seg += "  lw a0, " + offset + "(sp)\r\n";
        } else {
            offset = frame_data_manager.func_local_variable_offsets.get(func_id).get(return_id);
            restore_frame_seg += "  lw a0, -" + offset + "(fp)\r\n";
        }
        restore_frame_seg += RESTORE_CALLER_RA;
        restore_frame_seg += RESTORE_CALLER_FP;
        restore_frame_seg += "  addi sp, sp, " + func_frame_size + "\r\n";
        restore_frame_seg += "  addi sp, sp, " + func_arg_size + "\r\n";
        restore_frame_seg += JUMP_TO_RETURN_ADDR;
        
        return restore_frame_seg;
    }

    /*   Label label; */
    public String visit(LabelInstr n) {
        String label = n.label.toString();
        String instr_seg = "";

        instr_seg += current_function_id + label  + ":\r\n";

        return instr_seg;
    }

    /*   Register lhs;
    *   int rhs; */
    public String visit(Move_Reg_Integer n) {
        String lhs_register = n.lhs.toString();
        String instr_seg = "";
        int rhs = n.rhs;

        instr_seg += "  li " + lhs_register + ", " + rhs + "\r\n";

        return instr_seg;
    }

    /*   Register lhs;
    *   FunctionName rhs; */
    public String visit(Move_Reg_FuncName n) {
        String lhs_register = n.lhs.toString();
        String func_name = n.rhs.toString();
        String instr_seg = "";

        instr_seg += "  la " + lhs_register + ", " + func_name + "\r\n";

        return instr_seg;
    }

    /*   Register lhs;
    *   Register arg1;
    *   Register arg2; */
    public String visit(Add n) {
        String lhs_register = n.lhs.toString();
        String arg1_register = n.arg1.toString();
        String arg2_register = n.arg2.toString();
        String instr_seg = "";

        instr_seg += "  add " + lhs_register + ", " + arg1_register + ", " + arg2_register + "\r\n";
        
        return instr_seg;
    }

    /*   Register lhs;
    *   Register arg1;
    *   Register arg2; */
    public String visit(Subtract n) {
        String lhs_register = n.lhs.toString();
        String arg1_register = n.arg1.toString();
        String arg2_register = n.arg2.toString();
        String instr_seg = "";

        instr_seg += "  sub " + lhs_register + ", " + arg1_register + ", " + arg2_register + "\r\n";
        
        return instr_seg;
    }

    /*   Register lhs;
    *   Register arg1;
    *   Register arg2; */
    public String visit(Multiply n) {
        String lhs_register = n.lhs.toString();
        String arg1_register = n.arg1.toString();
        String arg2_register = n.arg2.toString();
        String instr_seg = "";

        instr_seg += "  mul " + lhs_register + ", " + arg1_register + ", " + arg2_register + "\r\n";
        
        return instr_seg;
    }

    /*   Register lhs;
    *   Register arg1;
    *   Register arg2; */
    public String visit(LessThan n) {
        String lhs_register = n.lhs.toString();
        String arg1_register = n.arg1.toString();
        String arg2_register = n.arg2.toString();
        String instr_seg = "";

        instr_seg += "  slt " + lhs_register + ", " + arg1_register + ", " + arg2_register + "\r\n";
        
        return instr_seg;
    }

    /*   Register lhs;
    *   Register base;
    *   int offset; */
    public String visit(Load n) {
        String lhs_register = n.lhs.toString();
        String base_register = n.base.toString();
        String instr_seg = "";
        int offset = n.offset;

        instr_seg += "  lw " + lhs_register + ", " + offset + "(" + base_register + ")\r\n";

        return instr_seg;
    }

    /*   Register base;
    *   int offset;
    *   Register rhs; */
    public String visit(Store n) {
        String base_register = n.base.toString();
        String rhs_register = n.rhs.toString();
        String instr_seg = "";
        int offset = n.offset;

        instr_seg += "  sw " + rhs_register + ", " + offset + "(" + base_register + ")\r\n";

        return instr_seg;
    }

    /*   Register lhs;
    *   Register rhs; */
    public String visit(Move_Reg_Reg n) {
        String lhs_register = n.lhs.toString();
        String rhs_register = n.rhs.toString();
        String instr_seg = "";

        instr_seg += "  mv " + lhs_register + ", " + rhs_register + "\r\n";

        return instr_seg;
    }

    /*   Identifier lhs;
    *   Register rhs; */
    public String visit(Move_Id_Reg n) {
        String lhs_id = n.lhs.toString();
        String rhs_register = n.rhs.toString();
        String instr_seg = "";
        int lhs_id_offset = frame_data_manager.get_offset(current_function_id, lhs_id);

        instr_seg += "  sw " + rhs_register + ", " + lhs_id_offset + "(fp)\r\n";

        return instr_seg;
    }

    /*   Register lhs;
    *   Identifier rhs; */
    public String visit(Move_Reg_Id n) {
        String lhs_register = n.lhs.toString();
        String rhs_id = n.rhs.toString();
        String instr_seg = "";
        int rhs_id_offset = frame_data_manager.get_offset(current_function_id, rhs_id);

        instr_seg += "  lw " + lhs_register + ", " + rhs_id_offset + "(fp)\r\n";

        return instr_seg;
    }

    /*   Register lhs;
    *   Register size; */
    public String visit(Alloc n) {
        String lhs_register = n.lhs.toString();
        String size_register = n.size.toString();
        String instr_seg = "";

        instr_seg += "  mv a0, " + size_register + "\r\n";
        instr_seg += "  jal alloc\r\n";
        instr_seg += "  mv " + lhs_register + ", a0\r\n";

        return instr_seg;
    }

    /*   Register content; */
    public String visit(Print n) {
        String content_register = n.content.toString();
        String instr_seg = "";

        instr_seg += "  mv a0, " + content_register + "\r\n";
        instr_seg += "  jal print\r\n";

        return instr_seg;
    }

    /*   String msg; */
    public String visit(ErrorMessage n) {
        String msg = n.msg;
        String instr_seg = "";

        if (msg.equals(NULL_POINTER_MSG)) {
            instr_seg += "  la a0, msg_0\r\n";
        } else {
            instr_seg += "  la a0, msg_array_oob\r\n";
        }
        instr_seg += "  jal error\r\n";

        return instr_seg;
    }

    /*   Label label; */
    public String visit(Goto n) {
        String label = n.label.toString();
        String instr_seg = "";

        instr_seg += "  j " + current_function_id + label + "\r\n";

        return instr_seg;
    }

    /*   Register condition;
    *   Label label; */
    public String visit(IfGoto n) {
        String condition_register = n.condition.toString();
        String label = n.label.toString();
        String label_nlj = current_function_id + label + "_no_long_jump" + label_num;
        String instr_seg = "";

        instr_seg += "  bnez " + condition_register + ", " + label_nlj + "\r\n";
        instr_seg += "  jal " + current_function_id + label + "\r\n";
        instr_seg += label_nlj + ":\r\n";

        label_num++;

        return instr_seg;
    }

    /*   Register lhs;
    *   Register callee;
    *   List<Identifier> args; */
    public String visit(Call n) {
        String lhs_register = n.lhs.toString();
        String callee_register = n.callee.toString();
        String instr_seg = "";
        List<Identifier> arguments = n.args;
        int stack_arg_size = arguments.size() * 4;

        instr_seg += "  li t6, " + stack_arg_size + "\r\n";
        instr_seg += "  sub sp, sp, t6\r\n";
        for (int i = 0; i < arguments.size(); i++) {
            Identifier arg = arguments.get(i);
            String arg_id = arg.toString();
            int arg_local_offset = frame_data_manager.get_offset(current_function_id, arg_id);
            int arg_func_offset = i * 4;

            instr_seg += "  lw t6, " + arg_local_offset + "(fp)\r\n";
            instr_seg += "  sw t6, " + arg_func_offset + "(sp)\r\n";
        }
        instr_seg += "  jalr " + callee_register + "\r\n";
        instr_seg += "  mv " + lhs_register + ", a0\r\n";

        return instr_seg;
    }
}

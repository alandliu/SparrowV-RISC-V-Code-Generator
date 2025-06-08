package sparrowv.visitor;

import sparrowv.visitor.DepthFirst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import sparrowv.*;
import IR.token.*;

public class FrameDataVisitor extends DepthFirst {
    public HashMap<String, HashMap<String, Integer>> func_local_variable_offsets;   // get stack offsets of a local variable
    public HashMap<String, HashMap<String, Integer>> func_argument_offsets;         // get stack offsets of an argument
    public HashMap<String, Integer> func_frame_size;                                // function frame size to be allocated 

    String cur_func_id;
    int cur_offset;
    
    public HashSet<String> REGISTERS;
    int START_OFFSET = 12;  // -4 reserved for return address (ra)
                            // -8 reserved for old fp

    String MAIN_ID = "Main";

    public FrameDataVisitor() {
        func_local_variable_offsets = new HashMap<>();
        func_argument_offsets = new HashMap<>();
        func_frame_size = new HashMap<>();

        cur_func_id = "";
        cur_offset = START_OFFSET;

        // initialize all registers
        REGISTERS = new HashSet<>();
        for (int i = 2; i <= 7; i++) REGISTERS.add("a" + i);
        for (int i = 1; i <= 11; i++) REGISTERS.add("s" + i);
        for (int i = 0; i <= 5; i++) REGISTERS.add("t" + i);
    }

    public void generate_frame_data(Program program) {
        program.accept(this);
        print_frame_data_offsets();
    }

    // helpers
    boolean is_register(String var_id) { return REGISTERS.contains(var_id); }
    boolean is_parameter_var(String func_id, String var_id) { return func_argument_offsets.get(func_id).containsKey(var_id); }
    boolean is_local_var(String func_id, String var_id) { return !is_parameter_var(func_id, var_id); }
    int get_func_arg_size(String func_id) { return func_argument_offsets.get(func_id).size() * 4; }
    void increment_offset() { cur_offset += 4; }
    void add_func_field_offset(String func_id, String field_name) {
        // if already an argument, ignore it
        if (func_argument_offsets.get(func_id).containsKey(field_name)) return;

        // add var offset
        if (!func_local_variable_offsets.containsKey(func_id)) func_local_variable_offsets.put(func_id, new HashMap<>());
        if (!func_local_variable_offsets.get(func_id).containsKey(field_name)) {
            func_local_variable_offsets.get(func_id).put(field_name, cur_offset);
            func_frame_size.put(func_id, cur_offset);
            increment_offset();
        }
    }
    void add_func_arg_offset(String func_id, String arg_name, int parameter_index) {
        if (!func_argument_offsets.containsKey(func_id)) func_argument_offsets.put(func_id, new HashMap<>());
        func_argument_offsets.get(func_id).put(arg_name, parameter_index * 4);
    }
    int get_offset(String func_id, String var_id) {
        // if argument, return arg offset
        if (is_parameter_var(func_id, var_id)) {
            return func_argument_offsets.get(func_id).get(var_id);
        }
        return -1 * func_local_variable_offsets.get(func_id).get(var_id);
    }


    // debug
    void print_frame_data_offsets() {
        System.err.println("Function local variables and arguments:");
        for (String func_name : func_local_variable_offsets.keySet()) {
            System.err.println("Function: " + func_name + " has size of " + func_frame_size.get(func_name) + " bytes.");
            System.err.println("Args total size of " + get_func_arg_size(func_name) + " bytes.");
            HashMap<String, Integer> local_var_map = func_local_variable_offsets.get(func_name);
            HashMap<String, Integer> local_arg_map = func_argument_offsets.get(func_name);
            System.err.println("ARGS:");
            for (String arg_name : local_arg_map.keySet()) {
                System.err.println("\t" + arg_name + " : " + local_arg_map.get(arg_name));
            }
            System.err.println("FIELDS:");
            for (String var_name : local_var_map.keySet()) {
                System.err.println("\t" + var_name + " : " + local_var_map.get(var_name));
            }
            System.err.println("\n");
        }
    }


    /*   List<FunctionDecl> funDecls; */
    public void visit(Program n) {
        List<FunctionDecl> function_declarations = n.funDecls;

        for (FunctionDecl fd : function_declarations) {
            fd.accept(this);
        }
    }

    /*   Program parent;
    *   FunctionName functionName;
    *   List<Identifier> formalParameters;
    *   Block block; */
    public void visit(FunctionDecl n) {
        String function_name = n.functionName.toString();
        if (function_name.toLowerCase().equals("main")) function_name = MAIN_ID;
        List<Identifier> formal_parameters = n.formalParameters;
        func_local_variable_offsets.put(function_name, new HashMap<>());
        func_argument_offsets.put(function_name, new HashMap<>());

        cur_func_id = function_name;
        cur_offset = START_OFFSET;
        for (int i = 0; i < formal_parameters.size(); i++) {
            String param_name = formal_parameters.get(i).toString();

            add_func_arg_offset(function_name, param_name, i);
        }
        n.block.accept(this);
    }

    /*   FunctionDecl parent;
    *   List<Instruction> instructions;
    *   Identifier return_id; */
    public void visit(Block n) {
        List<Instruction> instructions = n.instructions;
        String return_id = n.return_id.toString();

        for (Instruction i: instructions) {
            i.accept(this);
        }
        add_func_field_offset(cur_func_id, return_id);
    }

    /*   Identifier lhs;
    *   Register rhs; */
    public void visit(Move_Id_Reg n) {
        String lhs_id = n.lhs.toString();

        add_func_field_offset(cur_func_id, lhs_id);
    }

    /*   Register lhs;
    *   Identifier rhs; */
    public void visit(Move_Reg_Id n) {
        String rhs_id = n.rhs.toString();

        add_func_field_offset(cur_func_id, rhs_id);
    }

    /*   Register lhs;
    *   Register callee;
    *   List<Identifier> args; */
    public void visit(Call n) {
        // parameters?
        // it is likely that they have been recorded already prior, but let's keep the function just in case
        return;
    }
}

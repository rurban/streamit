package at.dms.kjc.raw;

import at.dms.kjc.common.RawSimulatorPrint;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.util.Utils;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.io.*;


public class MakefileGenerator 
{
    public static final String MAKEFILE_NAME = "Makefile.streamit";
    //true if we want to use hardware icaching in the raw simulator
    public static final boolean ISCA_CONFIG = false;

    public static void createMakefile() 
    {
        
        try {
            //FileWriter fw = new FileWriter("Makefile");
            FileWriter fw = new FileWriter(MAKEFILE_NAME);
            //create a set of all the tiles with code
            HashSet<Object> tiles = new HashSet<Object>();
            tiles.addAll(TileCode.realTiles);
            tiles.addAll(TileCode.tiles);
        
            //remove the tiles assigned to FileReaders
            //do not generate switchcode for Tiles assigned to file readers
            //they are just dummy tiles
            Iterator<Object> frs = FileVisitor.fileNodes.iterator();
            while (frs.hasNext()) {
                tiles.remove(Layout.getTile((FlatNode)frs.next()));
            }

            //remove joiners from the hashset if we are in decoupled mode, 
            //we do not want to simulate joiners
            if (KjcOptions.decoupled || IMEMEstimation.TESTING_IMEM) 
                removeJoiners(tiles);

            Iterator<Object> tilesIterator = tiles.iterator();
        
            fw.write("#-*-Makefile-*-\n\n");
            /*
              if (KjcOptions.outputs < 0 &&
              ! (KjcOptions.numbers > 0))
              fw.write("LIMIT = TRUE\n"); // need to define limit for SIMCYCLES to matter
            */
        
            //if (!IMEMEstimation.TESTING_IMEM) {
            if (ISCA_CONFIG) {
                //fw.write("ATTRIBUTES = IMEM_EXTRA_LARGE\n");
                fw.write("BTL-DEVICES += -dram_freq 100\n");
                fw.write("ATTRIBUTES += HWIC\n");
                //add some other stuff
                fw.write("MEMORY_LAYOUT=LEFT_RIGHT_SIDES\n");
                fw.write("BTL-DEVICES += -enable_all_sides_for_dram -dram lhs\n");
            } 
            else {
                fw.write("ATTRIBUTES = IMEM_EXTRA_LARGE\n");
            }
                
            if (KjcOptions.hwic) {
               fw.write("ATTRIBUTES += HWIC\n");
            }
            
            //enable magic instructions for printing...            
            fw.write("EXTRA_BTL_ARGS += -magic_instruction\n ");
                    
            //fw.write("SIM-CYCLES = 500000\n\n");
            fw.write("\n");
            //if we are using the magic network, tell btl
            if (KjcOptions.magic_net)
                fw.write("EXTRA_BTL_ARGS += " +
                         "-magic_crossbar C1H1\n");
            fw.write("include $(TOPDIR)/Makefile.include\n\n");
            fw.write("RGCCFLAGS += -O3\n\n");
            fw.write("BTL-MACHINE-FILE = fileio.bc\n\n");
            if (FileVisitor.foundReader || FileVisitor.foundWriter)
                createBCFile(true, tiles);
            else
                createBCFile(false, tiles);
            if (RawBackend.rawRows > 4) {
                fw.write("TILE_PATTERN = 8x8\n\n");
            }
            //fix for snake boot race condition
            fw.write("MULTI_SNAKEBOOT = 0\n\n");
        
            fw.write("TILES = ");
            while (tilesIterator.hasNext()) {
                int tile = 
                    Layout.getTileNumber((Coordinate)tilesIterator.next());

                if (tile < 10)
                    fw.write("0" + tile + " ");
                else 
                    fw.write(tile + " ");
            }
        
            fw.write("\n\n");
        
            tilesIterator = tiles.iterator();
            while(tilesIterator.hasNext()) {
                int tile = 
                    Layout.getTileNumber((Coordinate)tilesIterator.next());
        
                if (tile < 10) 
                    fw.write("OBJECT_FILES_0");
                else
                    fw.write("OBJECT_FILES_");

                fw.write(tile + " = " +
                         "tile" + tile + ".o ");
                //if we are using the magic net, we do not create 
                //the switch assembly files, same if we are running decoupledxx
                if (!(KjcOptions.magic_net || KjcOptions.decoupled || IMEMEstimation.TESTING_IMEM)) 
                    fw.write("sw" + tile + ".o");
                fw.write("\n");
            }
        
            //use sam's gcc and set the parameters of the tile
//            if (KjcOptions.altcodegen) {
                fw.write
                    ("\nRGCC=/home/pkg/brave_new_linux/0225.btl.rawlib.starbuild/install/slgcc/bin/raw-gcc\n");
                fw.write("\nDMEM_PORTS  = 1\n");
                fw.write("ISSUE_WIDTH = 1\n\n");
                fw.write("EXTRA_BTL_ARGS += -issue_width $(ISSUE_WIDTH) -dmem_ports $(DMEM_PORTS)\n");
                fw.write("RGCCFLAGS += -missue_width=$(ISSUE_WIDTH) -mdmem_ports=$(DMEM_PORTS)\n");
//            }

            fw.write("\ninclude $(COMMONDIR)/Makefile.all\n\n");
            fw.write("clean:\n");
            fw.write("\trm -f *.o\n");
            fw.write("\trm -f tile*.s\n\n");
            fw.close();
        }
        catch (Exception e) 
            {
                System.err.println("Error writing Makefile");
                e.printStackTrace();
            }
    }
    
    //remove all tiles mapped to joiners from the coordinate hashset *tiles*
    private static void removeJoiners(HashSet<Object> tiles) {
        Iterator<FlatNode> it = Layout.getJoiners().iterator();
        while (it.hasNext()) {
            tiles.remove(Layout.getTile(it.next()));
        }
    }

    private static void createBCFile(boolean hasIO, HashSet<Object> tiles) throws Exception 
    {
        FileWriter fw = new FileWriter("fileio.bc");

        if (KjcOptions.magic_net) 
            fw.write("gTurnOffNativeCompilation = 1;\n");

        fw.write("include(\"<dev/basic.bc>\");\n");

        //workaround for magic instruction support...
        fw.write("include(\"<dev/magic_instruction.bc>\");\n");
    
        //let the simulation know how many tiles are mapped to 
        //filters or joiners
        fw.write("global gStreamItTilesUsed = " + Layout.getTilesAssigned() + ";\n");
        fw.write("global gStreamItTiles = " + RawBackend.rawRows * RawBackend.rawColumns +
                 ";\n");
        fw.write("global gStreamItUnrollFactor = " + KjcOptions.unroll + ";\n");
        fw.write("global streamit_home = getenv(\"STREAMIT_HOME\");\n");      
    
        fw.write("global gStreamItOutputs = " + KjcOptions.outputs + ";\n");
    
        if (KjcOptions.decoupled) {
            fw.write("global gStreamItFilterTiles = " + tiles.size()+ ";\n");
            fw.write("global gFilterNames;\n");
       
            fw.write("{\n");
            fw.write("  local workestpath = malloc(strlen(streamit_home) + 30);\n");
            fw.write("  gFilterNames = listi_new();\n");
            Iterator<Object> it = tiles.iterator();
            for (int i = 0; i < RawBackend.rawRows * RawBackend.rawColumns; i++) {
                if (tiles.contains(Layout.getTile(i))) {
                    fw.write("  listi_add(gFilterNames, \"" +
                             Layout.getNode(Layout.getTile(i)).getName() + "\");\n");
                }
            }
            fw.write("  sprintf(workestpath, \"%s%s\", streamit_home, \"/include/work_est.bc\");\n");
            //include the number gathering code and install the device file
            fw.write("  include(workestpath);\n");
            // add print service to the south of the SE tile
            fw.write("  {\n");
            fw.write("    local str = malloc(256);\n");
            fw.write("    local result;\n");
            fw.write("    sprintf(str, \"/tmp/%s.log\", *int_EA(gArgv,0));\n");
            fw.write("    result = dev_work_est_init(\"/dev/null\", gXSize+gYSize);\n");
            fw.write("    if (result == 0)\n");
            fw.write("      exit(-1);\n");
            fw.write("  }\n");
            fw.write("}\n");
        
        }

        //create the function to tell the simulator what tiles are mapped
        fw.write("fn mapped_tile(tileNumber) {\n");
        fw.write("if (0 ");
        Iterator<Object> tilesIterator = tiles.iterator();
        //generate the if statement with all the tile numbers of mapped tiles
        while (tilesIterator.hasNext()) {
            Coordinate tile = (Coordinate)tilesIterator.next();
            if (Layout.isAssigned(tile)) {
                fw.write("|| tileNumber == " + 
                         Layout.getTileNumber(tile) + "\n");
            }
        }
        fw.write(") {return 1; }\n");
        fw.write("return 0;\n");
        fw.write("}\n");
    
        //generate the bc code for the magic print handler...
        fw.write(RawSimulatorPrint.bCMagicHandler());
        
        /*
        if (KjcOptions.outputs > 0) {
            fw.write("{\n");
            fw.write("  local outputpath = malloc(strlen(streamit_home) + 30);\n");
            fw.write("  sprintf(outputpath, \"%s%s\", streamit_home, \"/include/output.bc\");\n");
            //include the number gathering code and install the device file
            fw.write("  include(outputpath);\n");
            //call the number gathering initialization function
            fw.write("  {\n");
            fw.write("    local str = malloc(256);\n");
            fw.write("    local result;\n");
            fw.write("    sprintf(str, \"/tmp/%s.log\", *int_EA(gArgv,0));\n");
            fw.write("    result = dev_output_init(\"/dev/null\", gXSize+gYSize);\n");
            fw.write("    if (result == 0)\n");
            fw.write("      exit(-1);\n");
            fw.write("  }\n");
            fw.write("}\n");
        }
        */

        //number gathering code
        if (KjcOptions.numbers > 0 && !IMEMEstimation.TESTING_IMEM) {
            fw.write("global printsPerSteady = " + NumberGathering.printsPerSteady + ";\n");
            fw.write("global calculatedPrintsPerSteady = " + NumberGathering.totalPrintsPerSteady + ";\n");
            fw.write("global skipPrints = " + NumberGathering.skipPrints + ";\n");
            fw.write("global quitAfter = " + KjcOptions.numbers + ";\n");
            fw.write("global gSinkX = " + 
                     Layout.getTile(NumberGathering.sink).getColumn() +
                     ";\n");
            fw.write("global gSinkY = " + 
                     Layout.getTile(NumberGathering.sink).getRow() +
                     ";\n");
        
            fw.write("{\n");
            fw.write("  local numberpath = malloc(strlen(streamit_home) + 30);\n");
            fw.write("  sprintf(numberpath, \"%s%s\", streamit_home, \"/include/gather_numbers.bc\");\n");
            //include the number gathering code and install the device file
            fw.write("  include(numberpath);\n");
            //call the number gathering initialization function
            fw.write("  gather_numbers_init();\n");
            /*  only number gathering crap
            // add print service to the south of the SE tile
            fw.write("  {\n");
            fw.write("    local str = malloc(256);\n");
            fw.write("    local result;\n");
            fw.write("    sprintf(str, \"/tmp/%s.log\", *int_EA(gArgv,0));\n");
            fw.write("    result = dev_gather_numbers_init(\"/dev/null\", gXSize+gYSize);\n");
            fw.write("    if (result == 0)\n");
            fw.write("      exit(-1);\n");
            fw.write("  }\n");
            */
            fw.write("}\n");
        }

        //magic network code
        if (KjcOptions.magic_net) {
            fw.write("include(\"magic_schedules.bc\");\n");
        
            fw.write("fn addMagicNetwork() {\n");
            fw.write("  local magicpath = malloc(strlen(streamit_home) + 30);\n");
            fw.write("  sprintf(magicpath, \"%s%s\", streamit_home, \"/include/magic_net.bc\");\n");
            //include the number gathering code and install the device file
            fw.write("  include(magicpath);\n");  
            //add the function to catch the magic instructions
            fw.write("  addMagicFIFOs();\n");
            fw.write("  create_schedules();\n");
            fw.write("}\n");
        }

        //      ("global gAUTOFLOPS = 0;\n" +
        //       "fn __clock_handler(hms)\n" +
        //              "{\n" +
        //              "  local i;\n" +
        //              "  for (i = 0; i < gNumProc; i++)\n" +
        //              "  {\n" +
        //              "    gAUTOFLOPS += imem_instr_is_fpu(get_imem_instr(i, get_pc_for_proc(i)));\n" +
        //              "  }\n" +
        //              "}\n" +
        //              "\n" +
        //              "EventManager_RegisterHandler(\"clock\", \"__clock_handler\");\n" +
        //              "\n" +

        fw.write
            ("global gAUTOFLOPS = 0;\n" +
             "fn __event_fpu_count(hms)\n" +
             "{" +
             "\tlocal instrDynamic = hms.instr_dynamic;\n" +
             "\tlocal instrWord = InstrDynamic_GetInstrWord(instrDynamic);\n" +
             "\tif (imem_instr_is_fpu(instrWord))\n" +
             "\t{\n" +
             "\t\tAtomicIncrement(&gAUTOFLOPS);\n" +
             "\t}\n" +
             "}\n\n" +
             "EventManager_RegisterHandler(\"issued_instruction\", \"__event_fpu_count\");\n" +

             "fn count_FLOPS(steps)\n" +
             "{\n" +
             "  gAUTOFLOPS = 0;\n" +
             "  step(steps);\n" +
             "  printf(\"// **** count_FLOPS: %4d FLOPS, %4d mFLOPS\n\",\n" +
             "         gAUTOFLOPS, (450*gAUTOFLOPS)/steps);\n" +
             "}\n" +
             "\n");
    
        if (hasIO){
            // create preamble
            fw.write("if (FindFunctionInSymbolHash(gSymbolTable, \"dev_data_transmitter_init\",3) == NULL)\n");
            fw.write("include(\"<dev/data_transmitter.bc>\");\n\n");
        
            // create the instrumentation function
            fw.write("// instrumentation code\n");
            fw.write("fn streamit_instrument(val){\n");
            fw.write("  local a;\n"); 
            fw.write("  local b;\n");
            fw.write("  Proc_GetCycleCounter(Machine_GetProc(machine,0), &a, &b);\n");
            fw.write("  //printf(\"cycleHi %X, cycleLo %X\\n\", a, b);\n");
            // use the same format string that generating a printf causes so we can use
            // the same results script;
            fw.write("  printf(\"[00: %08x%08x]: %d\\n\", a, b, val);\n");
            fw.write("}\n\n");
        

            //create the function to write the data
            fw.write("fn dev_st_port_to_file_size(filename, size, port)\n{\n");
            fw.write("local receive_device_descriptor = hms_new();\n");
            fw.write("// open the file\n  ;");
            fw.write("receive_device_descriptor.fileName = filename;\n  ");
            fw.write("receive_device_descriptor.theFile = fopen(receive_device_descriptor.fileName,\"w\");\n");
            fw.write("verify(receive_device_descriptor.theFile != NULL, \"### Failed to open output file\");\n");
            fw.write("receive_device_descriptor.calc =\n");
            fw.write("& fn(this)\n  {\n");
            fw.write("local theFile = this.theFile;\n");
            fw.write("while (1)\n {\n");
            fw.write("     local value = this.receive();\n");
            fw.write("     fwrite(&value, size, 1, theFile);\n");
            fw.write("     streamit_instrument(value);\n");
            fw.write("     fflush(theFile);\n");
            fw.write("}\n");
            fw.write("};\n");
            fw.write("return dev_data_transmitter_init(\"st_port_to_file\", port,0,receive_device_descriptor);\n");
            fw.write("}");
        }
        //
        fw.write("\n{\n");  
    
        if (KjcOptions.magic_net)
            fw.write("  addMagicNetwork();\n");

        if (hasIO) {
            //generate the code for the fileReaders
            Iterator<Object> frs = FileVisitor.fileReaders.iterator();
            fw.write("\tlocal f_readerpath = malloc(strlen(streamit_home) + 30);\n");
            fw.write("\tsprintf(f_readerpath, \"%s%s\", streamit_home, \"/include/from_file_raw.bc\");\n");
            //include the file reader device
            fw.write("\tinclude(f_readerpath);\n");
            while (frs.hasNext()) {
        
                FlatNode node = (FlatNode)frs.next();
                SIRFileReader fr = (SIRFileReader)node.contents;
                fw.write("\tdev_from_file_raw(\"" + fr.getFileName() + "\", " + 
                         getIOPort(Layout.getTile(node)) + ");\n");
            }
            //generate the code for the file writers
            Iterator<Object> fws = FileVisitor.fileWriters.iterator();
            while (fws.hasNext()) {
                FlatNode node = (FlatNode)fws.next();
                SIRFileWriter sfw = (SIRFileWriter)node.contents;
                int size = getTypeSize(((SIRFileWriter)node.contents).getInputType());
                fw.write("\tdev_st_port_to_file_size(\"" + sfw.getFileName() +
                         "\", " + size + ", " +
                         getIOPort(Layout.getTile(node)) + ");\n");
            }
        }
    
        fw.write("\n}\n");
        fw.close();
    }

    
    private static int getTypeSize(CType type) {
        if (type.equals(CStdType.Boolean))
            return 1;
        else if (type.equals(CStdType.Byte))
            return 1;
        else if (type.equals(CStdType.Integer))
            return 4;
        else if (type.equals(CStdType.Short))
            return 4;
        else if (type.equals(CStdType.Char))
            return 1;
        else if (type.equals(CStdType.Float))
            return 4;
        else if (type.equals(CStdType.Long))
            return 4;
        else
            {
                Utils.fail("Cannot write type to file: " + type);
            }
        return 0;
    }

    private static int getIOPort(Coordinate tile) 
    {
        return RawBackend.rawColumns + 
            + tile.getRow();
    }


}

        

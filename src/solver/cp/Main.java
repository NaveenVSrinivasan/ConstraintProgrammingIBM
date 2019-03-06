package solver.cp;

import ilog.concert.IloException;

import ilog.cp.IloCP;

import java.io.FileNotFoundException;

import java.io.IOException;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main
{  
  public static void main(String[] args) throws FileNotFoundException, IOException, IloException
  {
	if(args.length == 0)
	{
		System.out.println("Usage: java Main <file>");
		return;
	}
		
   	String input = args[0];
	Path path = Paths.get(input);
	String filename = path.getFileName().toString();
	System.out.println("Instance: " + input);
     
    Timer watch = new Timer();
    watch.start();
    CPInstance instance = new CPInstance(input);
    instance.solveAustraliaBinary();
    watch.stop();
     
    // OUTPUT FORMAT
    System.out.println("Instance: " + "Binary" + 
                       " Time: " + String.format("%.2f",watch.getTime()) +
                       " Result: " + instance.cp.getInfo(IloCP.IntInfo.NumberOfFails));
 
    watch.start();
    instance.solveAustraliaGlobal();
    watch.stop();
     
    // OUTPUT FORMAT
    System.out.println("Instance: " + "Global" + 
                       " Time: " + String.format("%.2f",watch.getTime()) +
                       " Result: " + instance.cp.getInfo(IloCP.IntInfo.NumberOfFails));

    watch.start();
    instance.solveSendMoreMoney();
    watch.stop();
     
    // OUTPUT FORMAT
    System.out.println("Instance: " + "SMM" + 
                       " Time: " + String.format("%.2f",watch.getTime()) +
                       " Result: " + instance.cp.getInfo(IloCP.IntInfo.NumberOfFails));
  }
}
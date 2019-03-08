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
     
    Timer watch = new Timer();
    watch.start();
    CPInstance instance = new CPInstance(input);
    instance.solve();
    watch.stop();
     
    System.out.println(String.format(
    	"Instance: %s Time: %.2f Result: %d Solution: %s",
    	filename,
    	watch.getTime(),
    	instance.cp.getInfo(IloCP.IntInfo.NumberOfFails),
    	instance.getSolution()
    ));
  }
}
package solver.cp;

import ilog.cp.*;

import ilog.concert.*;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;

public class CPInstance
{
  // BUSINESS parameters
  int numWeeks;
  int numDays;
  int numEmployees;
  int numShifts;
  int numIntervalsInDay;
  int[][] minDemandDayShift;
  int minDailyOperation;

  // EMPLOYEE parameters
  int minConsecutiveWork;
  int maxDailyWork;
  int minWeeklyWork;
  int maxWeeklyWork;
  int maxConsecutiveNightShift;
  int maxTotalNightShift;

  EmployeeState[][] matrix;

  // ILOG CP Solver
  IloCP cp;

  public CPInstance(String fileName)
  {
    try
    {
      Scanner read = new Scanner(new File(fileName));

      while (read.hasNextLine())
      {
        String line = read.nextLine();
        String[] values = line.split(" ");
        if(values[0].equals("Business_numWeeks:"))
        {
          numWeeks = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Business_numDays:"))
        {
          numDays = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Business_numEmployees:"))
        {
          numEmployees = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Business_numShifts:"))
        {
          numShifts = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Business_numIntervalsInDay:"))
        {
          numIntervalsInDay = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Business_minDemandDayShift:"))
        {
          int index = 1;
          minDemandDayShift = new int[numDays][numShifts];
          for(int d=0; d<numDays; d++)
            for(int s=0; s<numShifts; s++)
              minDemandDayShift[d][s] = Integer.parseInt(values[index++]);
        }
        else if(values[0].equals("Business_minDailyOperation:"))
        {
          minDailyOperation = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Employee_minConsecutiveWork:"))
        {
          minConsecutiveWork = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Employee_maxDailyWork:"))
        {
          maxDailyWork = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Employee_minWeeklyWork:"))
        {
          minWeeklyWork = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Employee_maxWeeklyWork:"))
        {
          maxWeeklyWork = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Employee_maxConsecutiveNigthShift:"))
        {
          maxConsecutiveNightShift = Integer.parseInt(values[1]);
        }
        else if(values[0].equals("Employee_maxTotalNigthShift:"))
        {
          maxTotalNightShift = Integer.parseInt(values[1]);
        }
      }
    }
    catch (FileNotFoundException e)
    {
      System.out.println("Error: file not found " + fileName);
    }
  }

  public void solve() throws IloException
  {
    cp = new IloCP();

    matrix = new EmployeeState[numEmployees][numDays];
    for (int i = 0; i < numEmployees; i++) {
      for (int j = 0; j < numDays; j++) {
        matrix[i][j] = new EmployeeState(this, j < numShifts ? null : matrix[i][j - 1]);
      }
    }

    // Break symmetry
    // IloIntVar[][] allShifts = new IloIntVar[numEmployees][numDays];
    // for (int i = 0; i < numEmployees; i++) {
    //   for (int j = 0; j < numDays; j++) {
    //     allShifts[i][j] = matrix[i][j].shift;
    //   }
    //   if (i > 0) {
    //     cp.add(cp.lexicographic(allShifts[i], allShifts[i-1]));
    //   }
    // }

    // Training requirement
    for (int i = 0; i < numEmployees; i++) {
      IloIntVar[] shifts = new IloIntVar[numShifts];
      for (int j = 0; j < numShifts; j++) {
        shifts[j] = matrix[i][j].shift;
      }
      cp.add(cp.allDiff(shifts));
    }

    // Weekly hours requirements
    for (int i = 0; i < numEmployees; i++) {
      IloIntVar[] lengths = new IloIntVar[numDays];
      // IloIntVar[] working = new IloIntVar[numDays];
      for (int j = 0; j < numDays; j++) {
        lengths[j] = matrix[i][j].length;
        // working[j] = matrix[i][j].working;
      }
      for (int j = 0; j <= numDays - 7; j++) {
        IloIntExpr sum = cp.sum(lengths, j, 7);
        cp.add(cp.ge(sum, minWeeklyWork));
        cp.add(cp.le(sum, maxWeeklyWork));

        // IloIntExpr prod = cp.scalProd(lengths, working, j, 7);
        // cp.add(cp.ge(prod, minWeeklyWork));
        // cp.add(cp.le(prod, maxWeeklyWork));
      }
    }

    // Minimum daily operation
    for (int i = 0; i < numDays; i++) {
      IloIntVar[] lengths = new IloIntVar[numEmployees];
      // IloIntVar[] working = new IloIntVar[numEmployees];
      for (int j = 0; j < numEmployees; j++) {
        lengths[j] = matrix[j][i].length;
        // working[j] = matrix[j][i].working;
      }
      IloIntExpr sum = cp.sum(lengths);
      cp.add(cp.ge(sum, minDailyOperation));

      // IloIntExpr prod = cp.scalProd(lengths, working);
      // cp.add(cp.ge(prod, minDailyOperation));
    }

    int[] shiftValues = IntStream.range(0, numShifts).toArray();

    // Maximum night shifts
    IloIntVar[] counts = new IloIntVar[numEmployees];
    for (int i = 0; i < numEmployees; i++) {
      IloIntVar[] shifts = new IloIntVar[numDays-numShifts];
      for (int j = 0; j < numDays-numShifts; j++) {
        shifts[j] = matrix[i][j+numShifts].shift;
      }

      cp.add(cp.lt(cp.count(shifts, numShifts-1), maxTotalNightShift));

      // IloIntVar[] cards = new IloIntVar[numShifts];
      // for (int j = 0; j < numShifts-1; j++) {
      //   cards[j] = cp.intVar(0, numDays-numShifts);
      // }
      // cards[numShifts-1] = cp.intVar(0, maxTotalNightShift-1);
      // cp.add(cp.distribute(cards, shiftValues, shifts));
    }

    // // Minimum demand
    // for (int i = 0; i < numDays; i++) {
    //   IloIntVar[] demand = new IloIntVar[numShifts];
    //   for (int j = 0; j < numShifts; j++) {
    //     demand[j] = cp.intVar(minDemandDayShift[i][j], numEmployees);
    //   }
    //   IloIntVar[] shifts = new IloIntVar[numEmployees];
    //   for (int j = 0; j < numEmployees; j++) {
    //     shifts[j] = matrix[j][i].shift;
    //   }
    //   cp.add(cp.distribute(demand, shiftValues, shifts));
    // }

    for (int i = 0; i < numDays; i++) {
      IloIntVar[] shifts = new IloIntVar[numEmployees];
      for (int j = 0; j < numEmployees; j++) {
        shifts[j] = matrix[j][i].shift;
      }
      for (int j = 0; j < numShifts; j++) {
        IloIntExpr count = cp.count(shifts, j);
        cp.add(cp.ge(count, minDemandDayShift[i][j]));
      }
    }

    // Important: Do not change! Keep these parameters as is
    cp.setParameter(IloCP.IntParam.Workers, 1);
    cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
    // cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);

    // Uncomment this: to set the solver output level if you wish
    // cp.setParameter(IloCP.IntParam.LogVerbosity, IloCP.ParameterValues.Quiet);
    if(cp.solve())
    {
      cp.printInformation();

      // Uncomment this: for poor man's Gantt Chart to display schedules
      // prettyPrint(numEmployees, numDays, beginED, endED);
    }
    else
    {
      System.out.println("No Solution found!");
      System.out.println("Number of fails: " + cp.getInfo(IloCP.IntInfo.NumberOfFails));
    }
  }

  // SK: technically speaking, the model with the global constaints
  // should result in fewer number of fails. In this case, the problem
  // is so simple that, the solver is able to retransform the model
  // and replace inequalities with the global all different constrains.
  // Therefore, the results don't really differ
  void solveAustraliaGlobal()
  {
    String[] Colors = {"red", "green", "blue"};
    try
    {
      cp = new IloCP();
      IloIntVar WesternAustralia = cp.intVar(0, 3);
      IloIntVar NorthernTerritory = cp.intVar(0, 3);
      IloIntVar SouthAustralia = cp.intVar(0, 3);
      IloIntVar Queensland = cp.intVar(0, 3);
      IloIntVar NewSouthWales = cp.intVar(0, 3);
      IloIntVar Victoria = cp.intVar(0, 3);

      IloIntExpr[] clique1 = new IloIntExpr[3];
      clique1[0] = WesternAustralia;
      clique1[1] = NorthernTerritory;
      clique1[2] = SouthAustralia;

      IloIntExpr[] clique2 = new IloIntExpr[3];
      clique2[0] = Queensland;
      clique2[1] = NorthernTerritory;
      clique2[2] = SouthAustralia;

      IloIntExpr[] clique3 = new IloIntExpr[3];
      clique3[0] = Queensland;
      clique3[1] = NewSouthWales;
      clique3[2] = SouthAustralia;

      IloIntExpr[] clique4 = new IloIntExpr[3];
      clique4[0] = Queensland;
      clique4[1] = Victoria;
      clique4[2] = SouthAustralia;

      cp.add(cp.allDiff(clique1));
      cp.add(cp.allDiff(clique2));
      cp.add(cp.allDiff(clique3));
      cp.add(cp.allDiff(clique4));

	  cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
	  cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);

      if (cp.solve())
      {
         System.out.println();
         System.out.println( "WesternAustralia:    " + Colors[(int)cp.getValue(WesternAustralia)]);
         System.out.println( "NorthernTerritory:   " + Colors[(int)cp.getValue(NorthernTerritory)]);
         System.out.println( "SouthAustralia:      " + Colors[(int)cp.getValue(SouthAustralia)]);
         System.out.println( "Queensland:          " + Colors[(int)cp.getValue(Queensland)]);
         System.out.println( "NewSouthWales:       " + Colors[(int)cp.getValue(NewSouthWales)]);
         System.out.println( "Victoria:            " + Colors[(int)cp.getValue(Victoria)]);
      }
      else
      {
        System.out.println("No Solution found!");
      }
    } catch (IloException e)
    {
      System.out.println("Error: " + e);
    }
  }

  void solveAustraliaBinary()
  {
    String[] Colors = {"red", "green", "blue"};
    try
    {
      cp = new IloCP();
      IloIntVar WesternAustralia = cp.intVar(0, 3);
      IloIntVar NorthernTerritory = cp.intVar(0, 3);
      IloIntVar SouthAustralia = cp.intVar(0, 3);
      IloIntVar Queensland = cp.intVar(0, 3);
      IloIntVar NewSouthWales = cp.intVar(0, 3);
      IloIntVar Victoria = cp.intVar(0, 3);

      cp.add(cp.neq(WesternAustralia , NorthernTerritory));
      cp.add(cp.neq(WesternAustralia , SouthAustralia));
      cp.add(cp.neq(NorthernTerritory , SouthAustralia));
      cp.add(cp.neq(NorthernTerritory , Queensland));
      cp.add(cp.neq(SouthAustralia , Queensland));
      cp.add(cp.neq(SouthAustralia , NewSouthWales));
      cp.add(cp.neq(SouthAustralia , Victoria));
      cp.add(cp.neq(Queensland , NewSouthWales));
      cp.add(cp.neq(NewSouthWales , Victoria));

	  cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
	  cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);

      if (cp.solve())
      {
         System.out.println();
         System.out.println( "WesternAustralia:    " + Colors[(int)cp.getValue(WesternAustralia)]);
         System.out.println( "NorthernTerritory:   " + Colors[(int)cp.getValue(NorthernTerritory)]);
         System.out.println( "SouthAustralia:      " + Colors[(int)cp.getValue(SouthAustralia)]);
         System.out.println( "Queensland:          " + Colors[(int)cp.getValue(Queensland)]);
         System.out.println( "NewSouthWales:       " + Colors[(int)cp.getValue(NewSouthWales)]);
         System.out.println( "Victoria:            " + Colors[(int)cp.getValue(Victoria)]);
      }
      else
      {
        System.out.println("No Solution found!");
      }
    } catch (IloException e)
    {
      System.out.println("Error: " + e);
    }
  }

  void solveSendMoreMoney()
  {
    try
    {
      // CP Solver
      cp = new IloCP();

      // SEND MORE MONEY
      IloIntVar S = cp.intVar(1, 9);
      IloIntVar E = cp.intVar(0, 9);
      IloIntVar N = cp.intVar(0, 9);
      IloIntVar D = cp.intVar(0, 9);
      IloIntVar M = cp.intVar(1, 9);
      IloIntVar O = cp.intVar(0, 9);
      IloIntVar R = cp.intVar(0, 9);
      IloIntVar Y = cp.intVar(0, 9);

      IloIntVar[] vars = new IloIntVar[]{S, E, N, D, M, O, R, Y};
      cp.add(cp.allDiff(vars));

      //                1000 * S + 100 * E + 10 * N + D
      //              + 1000 * M + 100 * O + 10 * R + E
      //  = 10000 * M + 1000 * O + 100 * N + 10 * E + Y

      IloIntExpr SEND = cp.sum(cp.prod(1000, S), cp.sum(cp.prod(100, E), cp.sum(cp.prod(10, N), D)));
      IloIntExpr MORE   = cp.sum(cp.prod(1000, M), cp.sum(cp.prod(100, O), cp.sum(cp.prod(10,R), E)));
      IloIntExpr MONEY  = cp.sum(cp.prod(10000, M), cp.sum(cp.prod(1000, O), cp.sum(cp.prod(100, N), cp.sum(cp.prod(10,E), Y))));

      cp.add(cp.eq(MONEY, cp.sum(SEND, MORE)));

      // Solver parameters
      cp.setParameter(IloCP.IntParam.Workers, 1);
      cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);
      if(cp.solve())
      {
        System.out.println("  " + cp.getValue(S) + " " + cp.getValue(E) + " " + cp.getValue(N) + " " + cp.getValue(D));
        System.out.println("  " + cp.getValue(M) + " " + cp.getValue(O) + " " + cp.getValue(R) + " " + cp.getValue(E));
        System.out.println(cp.getValue(M) + " " + cp.getValue(O) + " " + cp.getValue(N) + " " + cp.getValue(E) + " " + cp.getValue(Y));
      }
      else
      {
        System.out.println("No Solution!");
      }
    } catch (IloException e)
    {
      System.out.println("Error: " + e);
    }
  }

 /**
   * Poor man's Gantt chart.
   * Displays the employee schedules on the command line.
   * Each row corresponds to a single employee.
   * A "+" refers to a working hour and "." means no work
   * The shifts are separated with a "|"
   * The days are separated with "||"
   *
   * This might help you analyze your solutions.
   *
   * @param numEmployees the number of employees
   * @param numDays the number of days
   * @param beginED int[e][d] the hour employee e begins work on day d, -1 if not working
   * @param endED   int[e][d] the hour employee e ends work on day d, -1 if not working
   */
  void prettyPrint(int numEmployees, int numDays, int[][] beginED, int[][] endED)
  {
    for (int e = 0; e < numEmployees; e++)
    {
      System.out.print("E"+(e+1)+": ");
      if(e < 9) System.out.print(" ");
      for (int d = 0; d < numDays; d++)
      {
        for(int i=0; i < numIntervalsInDay; i++)
        {
          if(i%8==0)System.out.print("|");
          if (beginED[e][d] != endED[e][d] && i >= beginED[e][d] && i < endED[e][d]) System.out.print("+");
          else  System.out.print(".");
        }
        System.out.print("|");
      }
      System.out.println(" ");
    }
  }

  String getSolution() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < numDays; i++) {
      if (i > 0) sb.append(" ");
      for (int j = 0; j < numEmployees; j++) {
        if (j > 0) sb.append(" ");
        EmployeeState state = matrix[j][i];
        int shift = (int)cp.getValue(state.shift);
        int start, end;
        switch (shift) {
          case 0:
            start = end = -1;
            break;
          case 1:
            start = 8;
            end = start + (int)cp.getValue(state.length);
            break;
          case 2:
            start = 16;
            end = start + (int)cp.getValue(state.length);
            break;
          case 3:
            start = 0;
            end = start + (int)cp.getValue(state.length);
            break;
          default:
            throw new IllegalStateException(String.format("shift assigned value of %d", shift));
        }
        sb.append(String.format("%d %d", start, end));
      }
    }
    return sb.toString();
  }
}

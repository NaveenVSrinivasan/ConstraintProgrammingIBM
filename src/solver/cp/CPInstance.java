package solver.cp;

import ilog.cp.*;

import ilog.concert.*;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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

  int shiftLength;

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
      shiftLength = numIntervalsInDay / (numShifts - 1);
    }
    catch (FileNotFoundException e)
    {
      System.out.println("Error: file not found " + fileName);
    }
  }

  private class WorkerState {
    IloIntVar shift;
    IloIntVar start;
    IloIntVar end;
    IloIntExpr hours;

    WorkerState(IloCP cp, WorkerState previousDay) throws IloException {
      shift = cp.intVar(0, numShifts);
      start = cp.intVar(-1, numIntervalsInDay);
      end = cp.intVar(-1, numIntervalsInDay);
      hours = cp.diff(end, start);

      // start <= end
      cp.add(cp.le(start, end));

      // (start == end) <=> (shift == 0 /\ start == -1 /\ end == -1)
      cp.add(
        cp.equiv(
          cp.eq(start, end),
          cp.and(
            cp.eq(shift, 0),
            cp.and(cp.eq(start, -1), cp.eq(end, -1))
          )
        )
      );

      // (end - start == 0) \/ ((end - start >= minConsecutiveWork) /\ (end - start <= maxDailyWork))
      cp.add(
        cp.or(
          cp.eq(hours, 0),
          cp.and(cp.ge(hours, minConsecutiveWork), cp.le(hours, maxDailyWork))
        )
      );

      // (start >= (shift - 1) * shiftLength) /\ (end < (shift * shiftLength))
      cp.add(
        cp.and(
          cp.ge(
            start,
            cp.prod(cp.diff(shift, 1), shiftLength)
          ),
          cp.lt(
            end,
            cp.prod(shift, shiftLength)
          )
        )
      );

      // If the previous day's shift was the night shift, then today's shift cannot be
      if (previousDay != null) {
        cp.add(cp.imply(cp.eq(previousDay.shift, numShifts-1), cp.neq(shift, numShifts-1)));
      }
    }
  }

  public void solve() throws IloException
  {
    try
    {
      cp = new IloCP();

      List<List<WorkerState>> matrix = new ArrayList<>();
      for (int i = 0; i < numEmployees; i++) {
        List<WorkerState> states = new ArrayList<WorkerState>();
        for (int j = 0; j < numDays; j++) {
          states.add(new WorkerState(cp, j == 0 ? null : states.get(j - 1)));
        }
        matrix.add(states);
      }

      // Training requirement
      for (int i = 0; i < numEmployees; i++) {
        List<WorkerState> states = matrix.get(i).subList(0, numShifts);
        IloIntVar[] shifts = new IloIntVar[4];
        for (int j = 0; j < shifts.length; j++) {
          shifts[j] = states.get(j).shift;
        }
        cp.add(cp.allDiff(shifts));
      }

      // Weekly hours requirements
      for (int i = 0; i < numEmployees; i++) {
        for (int j = 0; j < numDays - 7; j++) {
          List<WorkerState> states = matrix.get(i).subList(j, j+7);
          IloIntExpr[] hours = new IloIntExpr[7];
          for (int k = 0; k < hours.length; k++) {
            hours[k] = states.get(k).hours;
          }
          IloIntExpr sum = cp.sum(hours);
          cp.add(cp.ge(sum, minWeeklyWork));
          cp.add(cp.le(sum, maxWeeklyWork));
        }
      }

      // Minimum daily operation
      for (int i = 0; i < numDays; i++) {
        IloIntExpr[] hours = new IloIntExpr[numEmployees];
        for (int j = 0; j < numEmployees; j++) {
          hours[j] = matrix.get(j).get(i).hours;
        }
        IloIntExpr sum = cp.sum(hours);
        cp.add(cp.ge(sum, minDailyOperation));
      }

      // Maximum consecutive night shift
      for (List<WorkerState> states : matrix) {
        IloIntVar[] shifts = new IloIntVar[numDays];
        for (int i = 0; i < numDays; i++) {
          shifts[i] = states.get(i).shift;
        }
        cp.add(
          cp.le(
            cp.count(shifts, numShifts-1),
            maxTotalNightShift
          )
        );
      }

      // Minimum demand
      for (int i = 0; i < numDays; i++) {
        IloIntVar[] shifts = new IloIntVar[numEmployees];
        for (int j = 0; j < numEmployees; j++) {
          shifts[j] = matrix.get(j).get(i).shift;
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
    catch(IloException e)
    {
      System.out.println("Error: " + e);
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

}

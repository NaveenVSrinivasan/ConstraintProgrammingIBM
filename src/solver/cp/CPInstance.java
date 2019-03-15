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
  final int NIGHT_SHIFT = 1;

  // EMPLOYEE parameters
  int minConsecutiveWork;
  int maxDailyWork;
  int minWeeklyWork;
  int maxWeeklyWork;
  int maxConsecutiveNightShift;
  int maxTotalNightShift;
  int[] lengths;

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

    lengths = new int[(maxDailyWork-minConsecutiveWork)+2];
    lengths[0] = 0;
    for (int i = 1; i < lengths.length; i++) {
      lengths[i] = minConsecutiveWork + (i - 1);
    }
  }

  public void solve() throws IloException
  {
    cp = new IloCP();

    matrix = new EmployeeState[numEmployees][numDays];
    for (int i = 0; i < numEmployees; i++) {
      for (int j = 0; j < numDays; j++) {
        matrix[i][j] = new EmployeeState(this, j < numShifts ? null : matrix[i][j-1]);
      }
    }

    // Break symmetry
    {
      IloIntVar[][] shifts = new IloIntVar[numEmployees][numDays];
      for (int i = 0; i < numEmployees; i++) {
        for (int j = 0; j < numDays; j++) {
          shifts[i][j] = matrix[i][j].shift;
        }
        if (i > 0) {
          cp.add(cp.lexicographic(shifts[i], shifts[i-1]));
        }
      }
    }

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
      for (int j = 0; j < numDays; j++) {
        lengths[j] = matrix[i][j].length;
      }

      for (int j = 0; j <= numDays - 7; j += 7) {
        IloIntExpr sum = cp.sum(lengths, j, 7);
        cp.add(cp.ge(sum, minWeeklyWork));
        cp.add(cp.le(sum, maxWeeklyWork));
      }
    }

    // Minimum daily operation
    for (int i = 0; i < numDays; i++) {
      IloIntVar[] lengths = new IloIntVar[numEmployees];
      for (int j = 0; j < numEmployees; j++) {
        lengths[j] = matrix[j][i].length;
      }

      IloIntExpr sum = cp.sum(lengths);
      cp.add(cp.ge(sum, minDailyOperation));
    }

    // Maximum night shifts
    for (int i = 0; i < numEmployees; i++) {
      IloIntVar[] shifts = new IloIntVar[numDays-numShifts];
      for (int j = 0; j < numDays-numShifts; j++) {
        shifts[j] = matrix[i][j+numShifts].shift;
      }

      cp.add(cp.lt(cp.count(shifts, NIGHT_SHIFT), maxTotalNightShift));
    }

    // Minimum demand
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

    IloVarSelector[] varSelector = new IloVarSelector[3];
    varSelector[0] = cp.selectSmallest(cp.domainSize());
    varSelector[1] = cp.selectSmallest(cp.varImpact());
    varSelector[2] = cp.selectRandomVar();

    IloValueSelector valueSelector = cp.selectLargest(cp.value());

    IloIntVar[] trainingVars = new IloIntVar[2*numEmployees*numShifts];
    int index = 0;
    for (int i = 0; i < numEmployees; i++) {
      for (int j = 0; j < numShifts; j++) {
        trainingVars[index++] = matrix[i][j].shift;
        trainingVars[index++] = matrix[i][j].length;
      }
    }

    IloSearchPhase trainingPhase = cp.searchPhase(trainingVars, cp.intVarChooser(varSelector),
        cp.intValueChooser(valueSelector));

    IloIntVar[] otherVars = new IloIntVar[2*numEmployees*(numDays-numShifts)];
    index = 0;
    for (int i = 0; i < numEmployees; i++) {
      for (int j = numShifts; j < numDays; j++) {
        otherVars[index++] = matrix[i][j].shift;
        otherVars[index++] = matrix[i][j].length;
      }
    }

    IloSearchPhase otherPhase = cp.searchPhase(otherVars, cp.intVarChooser(varSelector),
        cp.intValueChooser(valueSelector));

    cp.setParameter(IloCP.IntParam.Workers, 1);
    cp.setParameter(IloCP.DoubleParam.TimeLimit, 300);
    cp.setParameter(IloCP.IntParam.SearchType, IloCP.ParameterValues.DepthFirst);

    IloSearchPhase[] phases = new IloSearchPhase[]{trainingPhase, otherPhase};
    if(cp.solve(phases))
    {
      cp.printInformation();
    }
    else
    {
      System.out.println("No Solution found!");
      System.out.println("Number of fails: " + cp.getInfo(IloCP.IntInfo.NumberOfFails));
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

  String verifyAndGetSolution() {
    int[] totalNightShifts = new int[numEmployees];
    int[][] numEmployeesWorking = new int[numDays][numShifts];
    int numWeeks = numDays / 7;
    int[][] weeklyWork = new int[numWeeks][numEmployees];

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < numDays; i++) {
      if (i > 0) sb.append(" ");

      int operation = 0;
      for (int j = 0; j < numEmployees; j++) {
        if (j > 0) sb.append(" ");

        EmployeeState state = matrix[j][i];

        int length = (int)cp.getValue(state.length);
        operation += length;
        weeklyWork[i/7][j] += length;

        int shift = (int)cp.getValue(state.shift);
        numEmployeesWorking[i][shift] += 1;

        int start;
        switch (shift) {
          case 0:
            assert length == 0;
            start = -1;
            break;
          case 1: // NIGHT_SHIFT
            assert length >= minConsecutiveWork && length <= maxDailyWork;
            assert (int)cp.getValue(matrix[j][i-1].shift) != 1;
            assert ++totalNightShifts[j] <= maxTotalNightShift;
            start = 0;
            break;
          case 2:
            assert length >= minConsecutiveWork && length <= maxDailyWork;
            start = 8;
            break;
          case 3:
            assert length >= minConsecutiveWork && length <= maxDailyWork;
            start = 16;
            break;
          default:
            throw new IllegalStateException(String.format("shift assigned value of %d", shift));
        }
        int end = start + length;

        sb.append(String.format("%d %d", start, end));
      }

      assert operation >= minDailyOperation;
    }

    for (int i = 0; i < numDays; i++) {
      for (int j = 0; j < numShifts; j++) {
        assert numEmployeesWorking[i][j] >= minDemandDayShift[i][j];
      }
    }

    for (int i = 0; i < numWeeks; i++) {
      for (int j = 0; j < numEmployees; j++) {
        assert weeklyWork[i][j] >= minWeeklyWork;
      }
    }

    return sb.toString();
  }
}

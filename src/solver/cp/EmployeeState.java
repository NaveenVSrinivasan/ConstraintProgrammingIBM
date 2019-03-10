package solver.cp;

import ilog.concert.*;

public class EmployeeState {
  IloIntVar working, shift, length;

  public EmployeeState(CPInstance instance, EmployeeState yesterday) throws IloException {
    working = instance.cp.intVar(0, 1);
    shift = instance.cp.intVar(0, instance.numShifts-1);
    length = instance.cp.intVar(instance.minConsecutiveWork, instance.maxDailyWork);

    // (working == 0) <=> (shift == 0)
    instance.cp.add(
      instance.cp.equiv(
        instance.cp.eq(working, 0),
        instance.cp.eq(shift, 0)
      )
    );

    // If yesterday's shift was the night shift, then today's shift cannot be
    if (yesterday != null) {
      instance.cp.add(
        instance.cp.imply(
          instance.cp.eq(yesterday.shift, instance.numShifts-1),
          instance.cp.neq(shift, instance.numShifts-1)
        )
      );
    }
  }
}

package solver.cp;

import ilog.concert.*;

public class EmployeeState {
  IloIntVar shift, length;

  public EmployeeState(CPInstance instance, EmployeeState yesterday) throws IloException {
    shift = instance.cp.intVar(0, instance.numShifts-1);
    length = instance.cp.intVar(0, instance.maxDailyWork);

    // (shift == 0) <=> (length == 0)
    instance.cp.add(instance.cp.equiv(
      instance.cp.eq(shift, 0),
      instance.cp.eq(length, 0))
    );

    // (length > 0) => (length >= minConsecutiveWork)
    instance.cp.add(instance.cp.imply(
      instance.cp.gt(length, 0),
      instance.cp.ge(length, instance.minConsecutiveWork)
    ));

    // If yesterday's shift was the night shift, then today's shift cannot be
    if (yesterday != null) {
      instance.cp.add(instance.cp.imply(
        instance.cp.eq(yesterday.shift, instance.numShifts-1),
        instance.cp.neq(shift, instance.numShifts-1)
      ));
    }
  }
}

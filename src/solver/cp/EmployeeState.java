package solver.cp;

import ilog.concert.*;

public class EmployeeState {
  IloIntVar shift;
  IloIntervalVar interval;
  IloIntExpr length;

  public EmployeeState(CPInstance instance, EmployeeState yesterday) throws IloException {
    shift = instance.cp.intVar(0, instance.numShifts);
    length = instance.cp.intVar(0, instance.maxDailyWork);

    IloConstraint off = instance.cp.eq(length, 0);

    // (length == 0) \/ ((length >= minConsecutiveWork) /\ (length <= maxDailyWork))
    instance.cp.add(
      instance.cp.or(
        off,
        instance.cp.and(
          instance.cp.ge(length, instance.minConsecutiveWork),
          instance.cp.le(length, instance.maxDailyWork)
        )
      )
    );

    // (length == 0) <=> (shift == 0)
    instance.cp.add(instance.cp.equiv(off, instance.cp.eq(shift, 0)));

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

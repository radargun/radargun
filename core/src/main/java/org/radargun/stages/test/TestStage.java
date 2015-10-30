package org.radargun.stages.test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.state.SlaveState;
import org.radargun.stats.Statistics;
import org.radargun.utils.MinMax;
import org.radargun.utils.Projections;
import org.radargun.utils.TimeService;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "Stage representing steady-state of the test. Test load should not change for the duration of steady-state.")
public class TestStage extends BaseTestStage {

    @Property(doc = "Set to false for multi-iteration tests, and after last iteration execute finish-test stage. Default is false.")
    private boolean finish = true;

    protected RunningTest runningTest;

    @Override
    public DistStageAck executeOnSlave() {
        runningTest = (RunningTest) slaveState.get(RunningTest.nameFor(testName));
        if (runningTest == null) {
            return errorResponse("Test " + testName + " is not running! " + slaveState.asStringMap());
        } else if (runningTest.isTerminated()) {
            return errorResponse("Test " + testName + " was terminated!");
        }
        runningTest.setStatisticsPrototype(statisticsPrototype);
        int lastThreads = runningTest.getUsedThreads();
        runningTest.setMinWaitingThreads(1);

        log.info("Starting test " + testName + " steady-state");
        runningTest.setSteadyState(true);
        long now = TimeService.currentTimeMillis();
        boolean failed = false;
        try {
            long steadyStateEnd = now + duration;
            while (!runningTest.isTerminated() && now < steadyStateEnd) {
                Thread.sleep(Math.min(steadyStateEnd - now, 1000));
                now = TimeService.currentTimeMillis();
            }
        } catch (InterruptedException e) {
            log.error("Interruptions should not happen.", e);
            failed = true;
        }
        runningTest.setSteadyState(false);
        log.info("Ended steady-state");

        List<Statistics> statistics = runningTest.getStatistics();
        if (runningTest.getUsedThreads() != lastThreads) {
            log.error("Number of threads has changed during steady state.");
            failed = true;
        }
        if (runningTest.isTerminated()) {
            log.warn("The test was terminated during steady-state.");
            failed = true;
        }
        if (finish || failed) {
            runningTest.stopStressors();
            slaveState.remove(RunningTest.nameFor(testName));
            slaveState.removeServiceListener(runningTest);
        }
        destroy();

        if (statistics != null && mergeThreadStats) {
            Statistics merged = null;
            for (Statistics s : statistics) {
                if (merged == null) merged = s.copy();
                else merged.merge(s);
            }
            return newStatisticsAck(Collections.singletonList(merged), failed);
        } else {
            return newStatisticsAck(statistics, failed);
        }
    }

    @Override
    public StageResult processAckOnMaster(List<DistStageAck> acks) {
        StageResult result = super.processAckOnMaster(acks);
        if (result.isError()) return result;

        Report.Test test = getTest(true);
        testIteration = test == null ? 0 : test.getIterations().size();

        MinMax.Int usedThreads = new MinMax.Int();
        Map<Integer, Report.SlaveResult> slaveResults = new HashMap<Integer, Report.SlaveResult>();
        Statistics aggregated = null;
        boolean failed = false;
        for (StatisticsAck ack : Projections.instancesOf(acks, StatisticsAck.class)) {
            if (test != null) {
                // TODO: this looks like we could get same iteration value for all iterations reported
                String iterationValue = resolveIterationValue();
                if (iterationValue != null) {
                    test.setIterationValue(testIteration, iterationValue);
                }
                if (ack.statistics != null) {
                    test.addStatistics(testIteration, ack.getSlaveIndex(), ack.statistics);
                }
            }
            if (ack.statistics != null) {
                for (Statistics s : ack.statistics) {
                    if (aggregated == null) {
                        aggregated = s.copy();
                    } else {
                        aggregated.merge(s);
                    }
                }
            }
            usedThreads.add(ack.usedThreads);
            slaveResults.put(ack.getSlaveIndex(), new Report.SlaveResult(String.valueOf(ack.usedThreads), false));
            failed = failed || ack.failed;
            if (ack.failed) {
                log.warnf("Slave %d did not complete the test correctly", ack.getSlaveIndex());
            }
        }
        if (test != null) {
            test.addResult(getTestIteration(), new Report.TestResult("Used threads", slaveResults, usedThreads.toString(), false));
        } else {
            log.info("No test name - results are not recorded");
        }
        if (failed) {
            if (repeatCondition == null) {
                return StageResult.FAIL;
            } else {
                return StageResult.BREAK;
            }
        }
        if (checkRepeatCondition(aggregated)) {
            return StageResult.SUCCESS;
        } else {
            return StageResult.BREAK;
        }
    }

    /**
     * To be overridden in inheritors.
     */
    protected void destroy() {
    }

    protected DistStageAck newStatisticsAck(List<Statistics> statistics, boolean failed) {
        return new StatisticsAck(slaveState, statistics, runningTest.getUsedThreads(), failed);
    }

    protected static class StatisticsAck extends DistStageAck {
        public final int usedThreads;
        public final List<Statistics> statistics;
        public final boolean failed;

        protected StatisticsAck(SlaveState slaveState, List<Statistics> statistics, int usedThreads, boolean failed) {
            super(slaveState);
            this.statistics = statistics;
            this.usedThreads = usedThreads;
            this.failed = failed;
        }
    }
}

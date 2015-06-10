package org.radargun.stages.cache.test;

import org.radargun.DistStageAck;
import org.radargun.Operation;
import org.radargun.StageResult;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Report;
import org.radargun.stages.cache.generators.TimestampKeyGenerator;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.Stressor;
import org.radargun.state.SlaveState;
import org.radargun.stats.DefaultOperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.SynchronizedStatistics;
import org.radargun.traits.ContinuousQuery;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.Projections;
import org.radargun.utils.TimeService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Registers continuous query for given query and cache.
 *
 * @author vjuranek
 */
@Stage(doc = "Benchmark operations performance with enabled/disabled continuous query.")
public class ContinuousQueryStage extends AbstractQueryStage {

    @Property(doc = "Cache name with which continuous query should registered. Default is null, i.e. default cache.")
    private String cacheName = null;

    @Property(doc = "Allows to reset statistics at the begining of the stage. Default is false.")
    private boolean resetStats = false;

    @Property(doc = "Allows to remove continuous query. Default is false.")
    protected boolean remove = false;

    @InjectTrait
    private ContinuousQuery continuousQueryTrait;

    @InjectTrait
    private Queryable queryable;

    private SynchronizedStatistics statistics;

    @Override
    public DistStageAck executeOnSlave() {
        String statsKey = getClass().getName() + ".Stats";

        statistics = (SynchronizedStatistics) slaveState.get(statsKey);
        if (statistics == null) {
            statistics = new SynchronizedStatistics(new DefaultOperationStats());
            slaveState.put(statsKey, statistics);
        } else if (resetStats) {
            statistics.reset();
        }

        if (!remove) {
            registerCQ(slaveState);
        } else {
            unregisterCQ(slaveState);
        }

        return new ContinuousQueryAck(slaveState, statistics.snapshot(true));
    }

    @Override
    public StageResult processAckOnMaster(List<DistStageAck> acks) {
        StageResult result = super.processAckOnMaster(acks);
        if (result.isError()) return result;

        Report.Test test = createTest(testName, null);
        if (test != null) {
            int testIteration = test.getIterations().size();

            for (ContinuousQueryAck ack : Projections.instancesOf(acks, ContinuousQueryAck.class)) {
                if (ack.stats != null)
                    test.addStatistics(testIteration, ack.getSlaveIndex(), Collections.singletonList(ack.stats));
            }
        }
        return StageResult.SUCCESS;
    }

    @Override
    public OperationLogic getLogic() {
        return new Logic();
    }

    protected class Logic extends OperationLogic {
        @Override
        public void init(Stressor stressor) {
            super.init(stressor);
        }

        @Override
        public void run(Operation operation) throws RequestException {
            //no-op
        }
    }

    protected Report.Test createTest(String testName, String iterationName) {
        if (testName == null || testName.isEmpty()) {
            log.warn("No test name - results are not recorded");
            return null;
        } else {
            Report report = masterState.getReport();
            return report.createTest(testName, iterationName, true);
        }
    }

    private void registerCQ(SlaveState slaveState) {
        Class<?> clazz;
        try {
            clazz = slaveState.getClass().getClassLoader().loadClass(queryObjectClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot load class " + queryObjectClass, e);
        }

        Query.Builder builder = queryable.getBuilder(null, clazz);
        for (Condition condition : conditions) {
            condition.apply(builder);
        }
        if (orderBy != null) {
            for (SortElement se : orderBy) {
                builder.orderBy(se.attribute, se.asc ? Query.SortOrder.ASCENDING : Query.SortOrder.DESCENDING);
            }
        }
        if (projection != null) {
            builder.projection(projection);
        }
        if (offset >= 0) {
            builder.offset(offset);
        }
        if (limit >= 0) {
            builder.limit(limit);
        }
        Query query = builder.build();
        slaveState.put(ContinuousQuery.QUERY, query);

        ContinuousQuery.ContinuousQueryListener cqListener = new ContinuousQuery.ContinuousQueryListener() {
            @Override
            public void onEntryJoined(Object key, Object value) {
                statistics.registerRequest(getResponseTime(key), ContinuousQuery.ENTRY_JOINED);
                log.trace("Entry joined " + key + " -> " + value);
            }

            @Override
            public void onEntryLeft(Object key) {
                statistics.registerRequest(getResponseTime(key), ContinuousQuery.ENTRY_LEFT);
                log.trace("Entry left " + key);
            }
        };
        slaveState.put(ContinuousQuery.LISTENER, cqListener);

        continuousQueryTrait.createContinuousQuery(cacheName, query, cqListener);
    }

    public void unregisterCQ(SlaveState slaveState) {
        ContinuousQuery.ContinuousQueryListener cqListener = (ContinuousQuery.ContinuousQueryListener) slaveState.get(ContinuousQuery.LISTENER);
        if (cqListener != null) {
            continuousQueryTrait.removeContinuousQuery(cacheName, cqListener);
        }
    }

    private long getResponseTime(Object key) {
        if (key instanceof TimestampKeyGenerator.TimestampKey) {
            return (TimeUnit.MILLISECONDS.toNanos(TimeService.currentTimeMillis() - ((TimestampKeyGenerator.TimestampKey) key).getTimestamp()));
        }
        return 0; //latency of event arrival is not measured
    }

    private static class ContinuousQueryAck extends DistStageAck {
        public final Statistics stats;

        public ContinuousQueryAck(SlaveState slaveState, Statistics stats) {
            super(slaveState);
            this.stats = stats;
        }
    }
}

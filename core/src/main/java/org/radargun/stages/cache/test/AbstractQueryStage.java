package org.radargun.stages.cache.test;

import org.radargun.config.Converter;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Init;
import org.radargun.config.Property;
import org.radargun.config.PropertyHelper;
import org.radargun.stages.test.OperationLogic;
import org.radargun.stages.test.TestStage;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.Query;
import org.radargun.traits.Queryable;
import org.radargun.utils.NumberConverter;
import org.radargun.utils.ObjectConverter;
import org.radargun.utils.RandomValue;
import org.radargun.utils.ReflexiveConverters;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for stages which contain queries
 */
public abstract class AbstractQueryStage extends TestStage {

    @InjectTrait
    protected Queryable queryable;

    protected AtomicInteger expectedSize = new AtomicInteger(-1);


}

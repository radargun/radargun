package org.radargun.stages.cache.test;

import org.radargun.util.ReflectionUtils;
import org.radargun.utils.PrimitiveValue;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * @author vjuranek
 */
@Test
public class AbstractQueryStageTest {

    public void testSortConverterFromString() {
        AbstractQueryStage.SortConverter sc = new AbstractQueryStage.SortConverter();
        List<AbstractQueryStage.SortElement> sortElements = sc.convert("name:ASC, surname, address:DESC", List.class);
        assertEquals(sortElements.size(), 3);
        assertEquals(sortElements.get(0).attribute, "name");
        assertTrue(sortElements.get(0).asc);
        assertEquals(sortElements.get(1).attribute, "surname");
        assertTrue(sortElements.get(1).asc);
        assertEquals(sortElements.get(2).attribute, "address");
        assertFalse(sortElements.get(2).asc);
    }

    public void testSortConverterToString() throws Exception {
        List<AbstractQueryStage.SortElement> sortElements = new ArrayList<AbstractQueryStage.SortElement>();
        Constructor<AbstractQueryStage.SortElement> seConst = ReflectionUtils.getConstructor(AbstractQueryStage.SortElement.class, String.class, Boolean.TYPE);
        sortElements.add(seConst.newInstance("name", true));
        sortElements.add(seConst.newInstance("surname", true));
        sortElements.add(seConst.newInstance("address", false));

        AbstractQueryStage.SortConverter sc = new AbstractQueryStage.SortConverter();
        String sortString = sc.convertToString(sortElements);
        assertEquals(sortString, "name:ASC, surname:ASC, address:DESC");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSortConverterBadSyntaxt() {
        AbstractQueryStage.SortConverter sc = new AbstractQueryStage.SortConverter();
        @SuppressWarnings("unused") List<AbstractQueryStage.SortElement> sortElements = sc.convert("name:ACS", List.class);
        fail("SortConverter should failed during pasring!");
    }

}

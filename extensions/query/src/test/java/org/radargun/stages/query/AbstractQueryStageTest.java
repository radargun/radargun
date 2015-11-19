package org.radargun.stages.query;

import org.radargun.stages.query.OrderBy;
import org.radargun.util.ReflectionUtils;
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
        OrderBy.ListConverter sc = new OrderBy.ListConverter();
        List<OrderBy> sortElements = sc.convert("name:ASC, surname, address:DESC", List.class);
        assertEquals(sortElements.size(), 3);
        assertEquals(sortElements.get(0).attribute, "name");
        assertTrue(sortElements.get(0).asc);
        assertEquals(sortElements.get(1).attribute, "surname");
        assertTrue(sortElements.get(1).asc);
        assertEquals(sortElements.get(2).attribute, "address");
        assertFalse(sortElements.get(2).asc);
    }

    public void testSortConverterToString() throws Exception {
        List<OrderBy> sortElements = new ArrayList<OrderBy>();
        Constructor<OrderBy> seConst = ReflectionUtils.getConstructor(OrderBy.class, String.class, Boolean.TYPE);
        sortElements.add(seConst.newInstance("name", true));
        sortElements.add(seConst.newInstance("surname", true));
        sortElements.add(seConst.newInstance("address", false));

        OrderBy.ListConverter sc = new OrderBy.ListConverter();
        String sortString = sc.convertToString(sortElements);
        assertEquals(sortString, "name:ASC, surname:ASC, address:DESC");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSortConverterBadSyntaxt() {
        OrderBy.ListConverter sc = new OrderBy.ListConverter();
        @SuppressWarnings("unused") List<OrderBy> sortElements = sc.convert("name:ACS", List.class);
        fail("SortConverter should failed during pasring!");
    }

}

package com.carwash.shared.application;

import com.carwash.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class PaginationTest {
    record Row(String id, String name, Integer price, LocalDateTime at) { }
    private static final ListPolicy POLICY = new ListPolicy(Set.of("id", "name", "price", "at"), List.of(SortOrder.asc("id")),
            Map.of("name", ListPolicy.text(40), "from", ListPolicy.LOCAL_TIME, "to", ListPolicy.LOCAL_TIME), Set.of());
    private static final Map<String, Function<Row, ?>> FIELDS = Map.of("id", Row::id, "name", Row::name, "price", Row::price,
            "at", Row::at, "from", Row::at, "to", Row::at);
    @Test void firstMiddleLastEmptyAndOutOfRangeRetainRequestedMetadata() {
        var rows = List.of("a", "b", "c", "d", "e");
        for (int page = 0; page < 5; page++) {
            var result = PageResult.select(rows, new PageRequest(page, 2));
            assertEquals(page, result.page()); assertEquals(2, result.size()); assertEquals(5, result.totalElements());
            assertEquals(3, result.totalPages()); assertEquals(page < 2, result.hasNext()); assertEquals(page > 0, result.hasPrevious());
            assertEquals(List.of(List.of("a","b"),List.of("c","d"),List.of("e"),List.of(),List.of()).get(page),result.content());
        }
        var empty = PageResult.select(List.of(), new PageRequest(7, 20));
        assertEquals(7, empty.page()); assertEquals(0, empty.totalPages()); assertTrue(empty.hasPrevious()); assertFalse(empty.hasNext());
        var maximum = PageResult.of(List.of(), new PageRequest(0, 100), Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE / 100 + 1, maximum.totalPages());
        assertThrows(UnsupportedOperationException.class, () -> PageResult.select(rows, new PageRequest(0, 20)).content().clear());
    }
    @Test void validatesBoundsOverflowMalformedDuplicatesAndSortAllowlists() {
        assertEquals(new PageRequest(0,20), POLICY.parse(Map.of()).page());
        assertEquals(100,POLICY.parse(Map.of("size",List.of("100"))).page().size());
        assertEquals(Integer.MAX_VALUE,new PageRequest(Integer.MAX_VALUE,1).offset());
        for (var entry : List.of(Map.of("page",List.of("-1")),Map.of("page",List.of("2147483648")),
                Map.of("page",List.of("2147483647"),"size",List.of("100")),Map.of("page",List.of("1.1")),
                Map.of("size",List.of("0")),Map.of("size",List.of("101")),Map.of("size",List.of("-1")),
                Map.of("size",List.of("20","21")),Map.of("sort",List.of("password,asc")),
                Map.of("sort",List.of("id,sideways")),Map.of("sort",List.of("id")),Map.of("sort",List.of("id,asc","id,desc")),
                Map.of("sort",List.of("name,asc","price,asc","at,asc","id,asc")),Map.of("all",List.of("true")),
                Map.of("from",List.of("bad-date")),Map.of("from",List.of("+999999999-01-01T10:00:00")),Map.of("from",List.of("0000-01-01T10:00:00")),Map.of("from",List.of("2089-01-02T10:00:00"),"to",List.of("2089-01-01T10:00:00"))))
            assertThrows(BusinessRuleViolationException.class, () -> POLICY.parse(entry),entry.toString());
        assertThrows(BusinessRuleViolationException.class, () -> new PageRequest(-1,20));
        assertThrows(BusinessRuleViolationException.class, () -> POLICY.parse(Map.of("sort",Collections.singletonList(null))));
        assertThrows(BusinessRuleViolationException.class, () -> ListPolicy.INSTANT.apply("+999999999-01-01T10:00:00Z"));
        assertEquals(java.time.Instant.parse("2089-01-01T08:00:00.123456789Z"), ListPolicy.INSTANT.apply("2089-01-01T10:00:00.123456789+02:00"));
    }
    @Test void multiSortTiesNullsUnicodeAndFullNanosecondRangesAreStable() {
        var at = LocalDateTime.of(2089,1,1,10,0,0,123456789);
        var rows = List.of(new Row("b","tie",2,at),new Row("a","tie",2,at),new Row("c","tie",null,at.plusNanos(1)),
                new Row("d","different",1,at.minusNanos(1)));
        var query = POLICY.parse(Map.of("name",List.of("tie"),"sort",List.of("price,desc","at,asc")));
        assertEquals(List.of("a","b","c"),query.select(rows,FIELDS).content().stream().map(Row::id).toList());
        query=POLICY.parse(Map.of("from",List.of(at.toString()),"to",List.of(at.toString()),"size",List.of("1"),"page",List.of("1")));
        var result=query.select(rows,FIELDS);assertEquals(2,result.totalElements());assertEquals("b",result.content().getFirst().id());
        var unicode=List.of(new Row("a","\uE000",1,at),new Row("b","\uD800\uDC00",1,at));
        assertEquals("a",POLICY.parse(Map.of("sort",List.of("name,asc"))).select(unicode,FIELDS).content().getFirst().id());
        assertEquals(List.of("d","a","b","c"),POLICY.parse(Map.of("sort",List.of("price,asc"))).select(rows,FIELDS).content().stream().map(Row::id).toList());
    }
}

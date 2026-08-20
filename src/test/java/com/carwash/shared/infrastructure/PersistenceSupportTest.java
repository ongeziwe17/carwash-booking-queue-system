package com.carwash.shared.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersistenceSupportTest {

    @Test void truncatesOnlyTheDatabaseRepresentationToMicroseconds() {
        LocalDateTime value=LocalDateTime.of(2089,1,15,10,11,12,987654321);
        assertEquals(987654000,PersistenceSupport.databaseTime(value).getNano());
    }
    @Test void extractsSubMicrosecondRemainder(){assertEquals(321,PersistenceSupport.nanoRemainder(LocalDateTime.of(2089,1,15,10,11,12,987654321)));}
    @Test void reconstructsEveryNanosecond(){LocalDateTime value=LocalDateTime.of(2089,1,15,10,11,12,987654321);assertEquals(value,PersistenceSupport.domainTime(PersistenceSupport.databaseTime(value),PersistenceSupport.nanoRemainder(value)));}
    @Test void preservesExactMicroseconds(){LocalDateTime value=LocalDateTime.of(2089,1,15,10,11,12,987654000);assertEquals(value,PersistenceSupport.domainTime(PersistenceSupport.databaseTime(value),PersistenceSupport.nanoRemainder(value)));}
    @Test void keepsNullableTimestampsNull(){assertNull(PersistenceSupport.databaseTime(null));assertNull(PersistenceSupport.domainTime(null,(short)0));assertEquals(0,PersistenceSupport.nanoRemainder(null));}
}

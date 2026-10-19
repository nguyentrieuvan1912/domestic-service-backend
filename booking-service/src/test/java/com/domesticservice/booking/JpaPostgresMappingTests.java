package com.domesticservice.booking;

import com.domesticservice.booking.entity.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in against PostgreSQL. Every data change is rolled back; no app jobs or HTTP server start. */
@EnabledIfEnvironmentVariable(named="JPA_POSTGRES_VERIFY",matches="true")
@SpringBootTest(classes=JpaPostgresMappingTests.PersistenceOnly.class,
    webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false"})
@Transactional
@ActiveProfiles("jpa-mapping-verification")
class JpaPostgresMappingTests {
    @Configuration(proxyBeanMethods=false)
    @Profile("jpa-mapping-verification")
    @EnableAutoConfiguration
    @EntityScan("com.domesticservice.booking.entity")
    @EnableJpaRepositories("com.domesticservice.booking.repository")
    static class PersistenceOnly {}

    @Autowired EntityManager entityManager;

    @Test void allMappedTablesAndAssociationsCanBeRead() throws Exception {
        for(var type:entityManager.getMetamodel().getEntities()) {
            var rows=entityManager.createQuery("from "+type.getName(),type.getJavaType()).setMaxResults(1).getResultList();
            for(var row:rows) {
                for(var attribute:type.getAttributes()) {
                    if(!attribute.isAssociation()) continue;
                    String name=attribute.getName();
                    var getter=row.getClass().getMethod("get"+Character.toUpperCase(name.charAt(0))+name.substring(1));
                    org.hibernate.Hibernate.initialize(getter.invoke(row));
                }
            }
        }
    }

    @Test void entityCrudUsesTheExistingSchemaAndRollsBack() {
        String key="jpa-"+UUID.randomUUID();
        var rows=entityManager.createQuery("select b.id from Booking b",Long.class).setMaxResults(1).getResultList();
        org.junit.jupiter.api.Assumptions.assumeFalse(rows.isEmpty(),"A booking is needed to verify the local FK");
        var incident=new BookingIncident(); incident.setBooking(entityManager.getReference(Booking.class,rows.getFirst()));
        incident.setReporterId(1L); incident.setReporterRole("ADMIN"); incident.setReason(key); incident.setStatus("OPEN"); incident.setCreatedAt(Instant.now());
        entityManager.persist(incident); entityManager.flush(); Long id=incident.getId(); entityManager.clear();
        var stored=entityManager.find(BookingIncident.class,id); assertEquals(rows.getFirst(),stored.getBooking().getId());
        stored.setStatus("RESOLVED"); entityManager.flush(); entityManager.clear();
        assertEquals("RESOLVED",entityManager.find(BookingIncident.class,id).getStatus());
        entityManager.remove(entityManager.find(BookingIncident.class,id)); entityManager.flush();
        assertNull(entityManager.find(BookingIncident.class,id));
    }
}

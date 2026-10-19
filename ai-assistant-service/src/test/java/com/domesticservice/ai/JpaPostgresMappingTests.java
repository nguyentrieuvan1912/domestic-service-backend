package com.domesticservice.ai;

import com.domesticservice.ai.entity.*;
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
    @EntityScan("com.domesticservice.ai.entity")
    @EnableJpaRepositories("com.domesticservice.ai.repository")
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
        var document=new KnowledgeDocument(); document.setTitle(key); document.setContent("JPA verification");
        document.setStatus("INACTIVE"); document.setUpdatedAt(Instant.now());
        entityManager.persist(document); entityManager.flush(); Long id=document.getId(); entityManager.clear();
        var stored=entityManager.find(KnowledgeDocument.class,id); assertEquals(key,stored.getTitle());
        stored.setTitle("JPA updated"); entityManager.flush(); entityManager.clear();
        assertEquals("JPA updated",entityManager.find(KnowledgeDocument.class,id).getTitle());
        entityManager.remove(entityManager.find(KnowledgeDocument.class,id)); entityManager.flush();
        assertNull(entityManager.find(KnowledgeDocument.class,id));
    }
}

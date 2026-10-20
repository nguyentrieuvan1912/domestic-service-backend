package com.domesticservice.identity;

import com.domesticservice.identity.entity.*;
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
    @EntityScan("com.domesticservice.identity.entity")
    @EnableJpaRepositories("com.domesticservice.identity.repository")
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
        var customer = new Customer();
        customer.setUserName(key); customer.setPasswordHash("disabled-jpa-verification");
        customer.setFullName("JPA verification"); customer.setEmail(key+"@example.invalid");
        customer.setPhone("09"+String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100000000L)));
        customer.setRole("CUSTOMER"); customer.setStatus("INACTIVE");
        customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now());
        customer.setTotalBookings(0); customer.setRewardPoints(0L);
        entityManager.persist(customer); entityManager.flush();
        Long id=customer.getId(); entityManager.clear();
        assertInstanceOf(Customer.class,entityManager.find(User.class,id));
        var address=new Address(); address.setCustomer(entityManager.getReference(Customer.class,id));
        address.setTitle("JPA verification"); address.setReceiverName("Verification"); address.setReceiverPhone("0900000000");
        address.setProvince("TP. Hồ Chí Minh"); address.setWard("Verification"); address.setDetailAddress("Verification");
        address.setIsDefault(false); address.setCreatedAt(Instant.now()); address.setUpdatedAt(Instant.now());
        entityManager.persist(address); entityManager.flush(); entityManager.clear();
        var stored=entityManager.find(Address.class,address.getId());
        assertEquals(id,stored.getCustomer().getId());
        stored.setTitle("JPA updated"); entityManager.flush(); entityManager.clear();
        assertEquals("JPA updated",entityManager.find(Address.class,address.getId()).getTitle());
        entityManager.remove(entityManager.find(Address.class,address.getId())); entityManager.flush();
        entityManager.remove(entityManager.find(Customer.class,id)); entityManager.flush();
        assertNull(entityManager.find(User.class,id));
    }
}

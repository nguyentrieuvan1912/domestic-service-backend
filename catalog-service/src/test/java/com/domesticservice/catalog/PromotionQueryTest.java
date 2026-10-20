package com.domesticservice.catalog;

import com.domesticservice.catalog.service.PromotionQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.context.SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:promotion_metadata;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@org.springframework.test.context.ActiveProfiles("test")
class PromotionQueryTest {
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate database;
    @org.springframework.beans.factory.annotation.Autowired com.domesticservice.catalog.repository.support.NativeQueryExecutor queries;

    @Test void exposesRealScopesAndUnlimitedConditions() {
        var db = database;
        db.execute("CREATE TABLE promotions(id BIGINT,promotion_code VARCHAR(60),promotion_name VARCHAR(160),description VARCHAR(1000),start_at TIMESTAMP,end_at TIMESTAMP,discount_type VARCHAR(30),discount_value BIGINT,max_discount_amount BIGINT,minimum_booking_amount BIGINT,usage_limit INT,used_count INT,status VARCHAR(30))");
        db.execute("CREATE TABLE promotion_services(promotion_id BIGINT,service_id BIGINT)");
        db.update("INSERT INTO promotions VALUES(1,'GIAM10','All services','',DATEADD('DAY',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP),'PERCENTAGE',10,NULL,0,NULL,0,'ACTIVE'),(2,'VESINH10','Hourly cleaning','',DATEADD('DAY',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP),'PERCENTAGE',10,NULL,0,NULL,0,'ACTIVE')");
        db.update("INSERT INTO promotion_services VALUES(2,2)");
        var result=new PromotionQueryService(new com.domesticservice.catalog.repository.PromotionQuerySqlRepository(queries)).promotions();
        assertEquals(2,result.size());
        assertEquals(List.of(),result.getFirst().get("serviceIds"));
        assertNull(result.getFirst().get("usageLimit"));
        assertNull(result.getFirst().get("maxDiscountAmount"));
        assertEquals(List.of(2L),result.get(1).get("serviceIds"));
        assertEquals(true,result.get(1).get("isActive"));
    }
}

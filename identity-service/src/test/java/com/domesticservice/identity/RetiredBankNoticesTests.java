package com.domesticservice.identity;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class RetiredBankNoticesTests {
    @Test void retirementKeepsHistoryAndRealWithdrawalNotices() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(ds);
        db.execute("CREATE TABLE admin_inbox_entries(source varchar(30),source_key varchar(160),active boolean,read_at timestamp with time zone)");
        db.update("INSERT INTO admin_inbox_entries VALUES('finance','bank:1',true,null),('finance','withdrawal:1',true,null),('identity','bank:2',true,null)");
        try(var connection=ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V18__retire_manual_bank_review_notices.sql"));
        }
        assertEquals(3,db.queryForObject("SELECT count(*) FROM admin_inbox_entries",Integer.class));
        assertFalse(db.queryForObject("SELECT active FROM admin_inbox_entries WHERE source_key='bank:1'",Boolean.class));
        assertNotNull(db.queryForObject("SELECT read_at FROM admin_inbox_entries WHERE source_key='bank:1'",java.sql.Timestamp.class));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM admin_inbox_entries WHERE active AND read_at IS NULL",Integer.class));
    }
}

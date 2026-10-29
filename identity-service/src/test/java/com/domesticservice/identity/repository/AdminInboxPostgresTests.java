package com.domesticservice.identity.repository;

import com.domesticservice.identity.dto.AdminWorkItem;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ADMIN_INBOX_POSTGRES_VERIFY",matches="true")
class AdminInboxPostgresTests {
 private JdbcTemplate db;private AdminInboxRepository repo;private TransactionTemplate tx;
 @BeforeEach void setup() throws Exception {
  String url=System.getenv("ADMIN_INBOX_TEST_URL");
  assertEquals("jdbc:postgresql://localhost:15446/admin_inbox_test",url,"Tests must use the disposable inbox database only");
  var ds=new DriverManagerDataSource(url,"postgres","inbox_test_only");db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  db.execute("DROP TABLE IF EXISTS admin_inbox_entries");db.execute("CREATE TABLE IF NOT EXISTS users(id bigint PRIMARY KEY)");
  db.update("INSERT INTO users(id) VALUES(1),(2) ON CONFLICT DO NOTHING");
  try(var connection=ds.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V16__admin_inbox.sql"));}
  repo=new AdminInboxRepository(db);
 }
 private AdminWorkItem item(String key,String state){return new AdminWorkItem(key,state,"transactions","/admin/transactions","Cần xử lý "+key,"Trạng thái "+state);}
 private void apply(long user,String source,AdminWorkItem... items){tx.executeWithoutResult(status->repo.apply(user,source,List.of(items)));}
 @SuppressWarnings("unchecked") private List<Map<String,Object>> items(long user){return (List<Map<String,Object>>)repo.snapshot(user,null).get("items");}
 private long first(long user){return ((Number)items(user).getFirst().get("id")).longValue();}
 @Test void repeatedSyncKeepsOneEntryAndPreservesRead(){
  apply(1,"finance",item("withdrawal:1","PENDING"));long id=first(1);assertTrue(repo.markRead(1,id));
  apply(1,"finance",item("withdrawal:1","PENDING"));assertEquals(1,items(1).size());assertEquals(0L,repo.snapshot(1,null).get("unread"));assertEquals(1L,repo.snapshot(1,null).get("totalPending"));assertNotNull(items(1).getFirst().get("readAt"));
 }
 @Test void readStateAndQueuesBelongToEachAdmin(){
  apply(1,"finance",item("bank:1","PENDING"));apply(2,"finance",item("bank:1","PENDING"));
  assertFalse(repo.markRead(2,first(1)));assertTrue(repo.markRead(1,first(1)));assertEquals(1L,repo.snapshot(2,null).get("unread"));assertEquals(1,items(2).size());
 }
 @Test void nextActionCreatesNewUnreadAndRetainsHistory(){
  apply(1,"finance",item("withdrawal:1","PENDING"));repo.markRead(1,first(1));apply(1,"finance",item("withdrawal:1","APPROVED"));
  assertEquals(2,items(1).size());assertEquals(1L,repo.snapshot(1,null).get("unread"));assertEquals(1L,repo.snapshot(1,null).get("totalPending"));assertEquals(false,items(1).getLast().get("active"));
 }
 @Test void resolvingOneSourceDoesNotClearAnotherOrMarkMessagesRead(){
  apply(1,"finance",item("bank:1","PENDING"));apply(1,"identity",item("support:1","OPEN"));apply(1,"finance");
  assertEquals(2L,repo.snapshot(1,null).get("unread"));assertEquals(1L,repo.snapshot(1,null).get("totalPending"));
 }
 @Test void reopeningAReadResolvedTaskCreatesFreshNotice(){
  apply(1,"booking",item("incident:1","OPEN"));long old=first(1);repo.markRead(1,old);apply(1,"booking");apply(1,"booking",item("incident:1","OPEN"));
  assertTrue(first(1)>old);assertEquals(2,items(1).size());assertEquals(1L,repo.snapshot(1,null).get("unread"));assertEquals(1L,repo.snapshot(1,null).get("totalPending"));
 }
 @Test void readAllWatermarkDoesNotConsumeNewArrival(){
  apply(1,"finance",item("bank:1","PENDING"));long through=first(1);apply(1,"finance",item("bank:1","PENDING"),item("bank:2","PENDING"));repo.readAll(1,through);
  assertEquals(1L,repo.snapshot(1,null).get("unread"));assertEquals(2L,repo.snapshot(1,null).get("totalPending"));
 }
 @Test @SuppressWarnings("unchecked") void paginationAndFiltersUseDatabaseCounts(){
  var tasks=new ArrayList<AdminWorkItem>();for(int i=0;i<55;i++)tasks.add(item("bank:"+i,"PENDING"));tx.executeWithoutResult(status->repo.apply(1,"finance",tasks));
  var first=repo.snapshot(1,null);assertEquals(true,first.get("hasMore"));assertEquals(50,((List<?>)first.get("items")).size());
  var next=repo.snapshot(1,((Number)first.get("nextBefore")).longValue());assertEquals(5,((List<?>)next.get("items")).size());
  repo.markRead(1,first(1));var unread=repo.snapshot(1,null,"unread");assertEquals(54L,unread.get("unread"));assertEquals(55L,unread.get("totalPending"));
  assertTrue(((List<Map<String,Object>>)unread.get("items")).stream().allMatch(row->row.get("readAt")==null));
 }
 @Test void failedSnapshotRollsBackDeactivation(){
  apply(1,"finance",item("bank:1","PENDING"));
  assertThrows(RuntimeException.class,()->apply(1,"finance",new AdminWorkItem("bank:2","PENDING","transactions","/admin/transactions","x".repeat(256),"invalid fixture")));
  assertEquals(1L,repo.snapshot(1,null).get("totalPending"));assertEquals(1,items(1).size());
 }
}

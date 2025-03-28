package net.javapla.orm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import net.javapla.orm.ORM.FieldRecord;


class ORMTest {

    @Test
    void insertStatement() {
        String s = ORM.insertStatement(SpotifyEpisode.class);
        //System.out.println(s);
        assertEquals("insert into syndication_spotify (episode_number,id,url,title,description) values (?,?,?,?,?);", s);
    }
    @ORM.Table("syndication_spotify")
    public static record SpotifyEpisode(long episode_number, String id, String url, String title, String description) {}

    
    @Test
    void questionMarks() {
        String marks = ORM.questionMarks(5);
        assertEquals("?,?,?,?,?", marks);
    }
    
    
    @Test
    void identifier() {
        // read the annotation
        assertEquals("record_id", ORM.identifier(IndentifierRecord1.class).name());
        
        // no annotation, default to "id"
        assertEquals("id", ORM.identifier(NoIndentifierRecord.class).name());
        
        // multiple annotations, consider first
        assertEquals("number", ORM.identifier(IndentifierRecord2.class).name());
    }
    @ORM.Table("identifier_record") record IndentifierRecord1(@ORM.Identifier String record_id) {}
    record IndentifierRecord2(@ORM.Identifier long number, @ORM.Identifier String record_id) {}
    record NoIndentifierRecord(long number) {}
    
    @Test
    void fetchStatement() {
        assertEquals("select * from identifier_record where record_id = ?;", ORM.fetchStatement(IndentifierRecord1.class));
    }
    
    @Test
    void insertStatement_omitting_autogenerated() {
        String s = ORM.insertStatement(AutoGenerated.class);
        assertEquals("insert into auto_generated_record (name,uri) values (?,?);", s);
    }
    @ORM.Table("auto_generated_record") record AutoGenerated(@ORM.Identifier @ORM.AutoGenerated long id, String name, String uri, @ORM.AutoGenerated String updated) {}
    
    @Test
    void fields() {
        // should not include final-static
        FieldRecord[] fields = ORM.fields(WithStaticField.class);
        for (FieldRecord field : fields) {
            if (field.name().equals("FORMAT")) fail();
        }
    }
    record WithStaticField(String some, int thing) {
        public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }
    
    @Test
    void fields_omitting_autogenerated() {
        FieldRecord[] fields = ORM.pruneAutoGenerated( ORM.fields(AutoGenerated.class) );
        assertEquals(2, fields.length);
    }
    
    @Test
    void handle_timestamps() {
        
    }
    @ORM.Table("timestamped_record") record Timestamped(@ORM.Identifier @ORM.AutoGenerated long id, boolean paused, @ORM.AutoGenerated java.sql.Timestamp date) {}
    
    
    /*@Test
    void selectIdentifierOnly() {
        
    }*/
    
    /*@Test
    void updateStatement() {
        System.out.println(ORM.updateStatement(UpdaterRecord.class));
    }
    @ORM.Table("updater_table") record UpdaterRecord(@ORM.Identifier String type, String ts) {}
    */
    
    
}

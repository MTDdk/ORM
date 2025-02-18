package net.javapla.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ORM {
    
    //private static final HashMap<Class<?>, Field[]>       classFields      = new HashMap<>();
    private static final HashMap<Class<?>, FieldRecord[]> classFieldRecords = new HashMap<>();
    private static final HashMap<Class<?>, String>        fetchStatements   = new HashMap<>();
    private static final HashMap<Class<?>, String>        insertStatements  = new HashMap<>();

    static <T> T orm(Class<T> clazz, ResultSet r) {
        //Field[] fields = fields(clazz);
        FieldRecord[] fields = fields(clazz);
        
        try {
            
            if (clazz.isRecord()) {
                
                Constructor<T> constructor = constructorWithFields(fields, clazz);
                Object[] init = new Object[fields.length];
                
                for (int i = 0; i < fields.length; i++) {
                    FieldRecord f = fields[i];
                    
                    //String type = f.getType().getSimpleName();
                    //String name = f.getName();
                    /*if (f.getAnnotation(Timestamp.class) != null) {
                        type = "timestamp";
                    }
                    
                    String name = f.getName();
                    Method method = ResultSetLookup.method(type);
                    
                    if (f.getAnnotation(Timestamp.class) != null) {
                        init[i] = ((java.sql.Timestamp)method.invoke(r, name)).getTime();
                    } else {
                        init[i] = method.invoke(r, name);
                    }*/
                    Method resultSetmethod = ResultSetLookup.byName(f.type);
                    
                    init[i] = resultSetmethod.invoke(r, f.name);
                }
                
                return constructor.newInstance(init);
                
            } else {
                T instance = clazz.getConstructor().newInstance();
                
                for (FieldRecord f : fields) {
                    //String type = f.getType().getSimpleName();
                    //String name = f.getName();
                    Method resultSetmethod = ResultSetLookup.byName(f.type);
                    
                    f.field.set(instance, resultSetmethod.invoke(r, f.name));
                }
                
                return instance;
            }
            
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                 | NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    static Object[] orm(ResultSet set, String sql, Class<?> ... classes) {
        Object[] result = new Object[classes.length];
        HashMap<String,Integer> columnDefinition = ResultSetMetaDataLookup.formColumnDefinition(sql, set);
        
        for (int i = 0; i < classes.length; i++) {
            
            Class<?> c = classes[i];
            FieldRecord[] fields = fields(c);
            
            try {
                
                if (c.isRecord()) {
                    
                    Constructor<?> constructor = constructorWithFields(fields, c);
                    Object[] init = new Object[fields.length];
                    
                    for (int j = 0; j < fields.length; j++) {
                        FieldRecord f = fields[j];
                        int column = columnDefinition.get(f.columnDefintion);
                        
                        Method resultSetmethod = ResultSetLookup.byColumn(f.type);
                        init[j] = resultSetmethod.invoke(set, column);
                    }
                    
                    result[i] = constructor.newInstance(init);
                    
                } else {
                    
                }
                
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | /*NoSuchMethodException |*/ SecurityException e) {
                e.printStackTrace();
            }
            
        }
        return result;
    }
    
    static <T> String fetchStatement(Class<T> c) {
        return fetchStatement(c, identifier(c));
    }
    
    static <T> String fetchStatement(Class<T> c, FieldIdentifier identifier) {
        return fetchStatements.computeIfAbsent(c, cl -> {
            return "select * from " + tableName(c) + " where " + identifier.name + " = ?;";
        });
    }
    
    static <T> String insertStatement(Class<T> c) {
        return insertStatements.computeIfAbsent(c, cl -> {
            FieldRecord[] fields = fields(c);
            
            // remove autogeneratable fields
            fields = pruneAutoGenerated(fields);
            
            
            return "insert into " + tableName(c) + " (" + names(fields) + ") values (" + questionMarks(fields.length) + ");";
        });
    }
    
//    static <T> String updateStatement(Class<T> c) {
//        Field[] fields = fields(c);
//        FieldRecord identifier = identifier(c);
//        return "update " + tableName(c) + " (" + names(fields, identifier) + ") values" + ";";
//    }
    
    static <T> T fetch(Connection conn, Class<T> c, Object identifierValue) throws SQLException {
        FieldIdentifier identifier = identifier(c);
        try (/*conn;*/  PreparedStatement ps = conn.prepareStatement(ORM.fetchStatement(c, identifier))) {
            
            Method method = PreparedStatementLookup.method(identifier.type);
            method.invoke(ps, 1, identifierValue);
            
            ResultSet set = ps.executeQuery();
            
            if (set.next()) {
                return ORM.orm(c, set);
            }
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    static <T> List<T> fetchAll(Connection conn, Class<T> c) throws SQLException {
        try (/*conn;*/ PreparedStatement ps = conn.prepareStatement("select * from " + tableName(c) + ";")) {
            
            LinkedList<T> result = new LinkedList<>();
            
            ResultSet set = ps.executeQuery();
            while (set.next()) {
                result.add(ORM.orm(c, set));
            }
            
            return result;
        }
    }
    
    static <T> List<T> fetchAll(Connection conn, Class<T> c, Object identifierValue) throws SQLException {
        try (/*conn;*/ PreparedStatement ps = conn.prepareStatement("select * from " + tableName(c) + " where " + identifier(c) + " = ?;")) {
            LinkedList<T> result= new LinkedList<>();
            
            ResultSet set = ps.executeQuery();
            while (set.next()) {
                result.add(ORM.orm(c, set));
            }
            
            return result;
        }
    }
    
    static <T> int insert(Connection conn, T o) throws SQLException {
        @SuppressWarnings("unchecked")
        Class<T> c = (Class<T>) o.getClass();
        
        try (/*conn;*/ PreparedStatement ps = conn.prepareStatement(insertStatement(c), Statement.RETURN_GENERATED_KEYS)) {
            
            FieldRecord[] fields = pruneAutoGenerated(fields(c));
            
            int i = 1;
            for (FieldRecord f : fields) {
                String type = f.type;
                String name = f.name;
                Method valueMethod = c.getMethod(name);
                
                Method method = PreparedStatementLookup.method(type);
                method.invoke(ps, i++, valueMethod.invoke(o));
            } 
            
            ps.executeUpdate();
            
            ResultSet set = ps.getGeneratedKeys();
            if (set.next()) return set.getInt(1);
            
            return 0;
            
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return -1;
    }
    
    
    /*static Field[] fields(Class<?> clazz) {
        return classFields.computeIfAbsent(clazz, cl -> {
            
            if (cl.isRecord()) {
                Field[] fields = cl.getDeclaredFields();
                
                Field[] temp = new Field[fields.length];
                int length = 0;
                
                // exclude static fields (they are definitely not a part of the constructor)
                for (Field field : fields) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        temp[length++] = field;
                    }
                }
                
                Field[] result = new Field[length];
                System.arraycopy(temp, 0, result, 0, length);
                return result;
            }
            
            return cl.getFields();
        });
    }*/
    static FieldRecord[] fields(Class<?> clazz) {
        return classFieldRecords.computeIfAbsent(clazz, cl -> {
            String table = tableName(cl);
            
            if (cl.isRecord()) {
                Field[] fields = cl.getDeclaredFields();
                
                FieldRecord[] temp = new FieldRecord[fields.length];
                int length = 0;
                
                // exclude static fields (they are definitely not a part of the constructor)
                for (Field field : fields) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        temp[length++] = new FieldRecord(field, table);
                    }
                }
                
                FieldRecord[] result = new FieldRecord[length];
                System.arraycopy(temp, 0, result, 0, length);
                return result;
            }
            
            // else isClass
            return toRecords(cl.getFields(), table);
        });
    }
    
    static FieldRecord[] toRecords(Field[] fields, String table) {
        FieldRecord[] result = new FieldRecord[fields.length];
        
        for (int i = 0; i < fields.length; i++) {
            result[i] = new FieldRecord(fields[i], table);
        }
        
        return result;
    }
    
    static String names(FieldRecord[] fields) {
        StringBuilder b = new StringBuilder();
        for (FieldRecord f : fields) {
            b.append(f.name);
            b.append(',');
        }
        b.deleteCharAt(b.length() - 1);
        return b.toString();
    }
    
//    /**
//     * Skips indentifier fields
//     */
//    static String names(Field[] fields, FieldRecord skip) {
//        StringBuilder b = new StringBuilder();
//        for (Field f : fields) {
//            if (f.getName().equals(skip.name)) continue;
//            b.append(f.getName());
//            b.append(',');
//        }
//        b.deleteCharAt(b.length() - 1);
//        return b.toString();
//    }
    
    static String questionMarks(int n) {
        return "?,".repeat(n).substring(0, n * 2 - 1);
    }
    
    static FieldRecord[] pruneAutoGenerated(FieldRecord[] fields) {
        FieldRecord[] temp = new FieldRecord[fields.length];
        int length = 0;
        
        for (FieldRecord field : fields) {
            if (field.field.getAnnotation(AutoGenerated.class) == null) {
                temp[length++] = field;
            }
        }
        
        FieldRecord[] result = new FieldRecord[length];
        System.arraycopy(temp, 0, result, 0, length);
        return result;
    }
    
    static <T> String tableName(Class<T> c) {
        String name = null;
        
        Table table = c.getAnnotation(Table.class);
        if (table != null) name = table.value();
        if (name == null) name = c.getSimpleName().toLowerCase();
        
        return name;
    }
    
    static <T> FieldIdentifier identifier(Class<T> c) {
        FieldRecord[] fields = fields(c);
        
        for (FieldRecord field : fields) {
            if (field.field.getAnnotation(Identifier.class) != null) {
                return new FieldIdentifier(field.name, field.type);
            }
        }
        
        return FieldIdentifier.DEF;
    }
    
    static <T> Constructor<T> constructorWithFields(FieldRecord[] fields, Class<T> c) {
        // Technically this just requires that there is a constructor with the same arguments as declared fields.
        // The method does not require c to be a record
        Class<?>[] args = new Class<?>[fields.length];
        for (int i = 0; i < fields.length; i++) {
            args[i] = fields[i].field.getType();
        }
        try {
            return c.getConstructor(args);
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    static class ResultSetLookup {
        private static final HashMap<String, Method> stringNameMethods, intColumnMethods;
        static {
            Method[] declaredMethods = ResultSet.class.getDeclaredMethods();
            stringNameMethods = allSingleStringParameterMethods(declaredMethods);
            intColumnMethods  = allSingleIntegerParameterMethods(declaredMethods);
        }
        
        private ResultSetLookup() {}
        
        private static HashMap<String, Method> allSingleStringParameterMethods(Method[] declaredMethods) {
            HashMap<String, Method> map = new HashMap<>();
            
            for (Method m : declaredMethods) {
                if (m.getParameterCount() == 1 && m.getParameters()[0].getType().equals(java.lang.String.class)) {
                    map.put(m.getName().toLowerCase(), m);
                }
            }
            
            return map;
        }
        private static HashMap<String, Method> allSingleIntegerParameterMethods(Method[] declaredMethods) {
            HashMap<String, Method> map = new HashMap<>();
            
            for (Method m : declaredMethods) {
                if (m.getParameterCount() == 1 && m.getParameters()[0].getType().equals(int.class)) {
                    map.put(m.getName().toLowerCase(), m);
                }
            }
            
            return map;
        }
        
        public static Method byName(String type) {
            return stringNameMethods.get("get" + type.toLowerCase());
        }
        public static Method byColumn(String type) {
            return intColumnMethods.get("get" + type.toLowerCase());
        }
    }
    
    static class PreparedStatementLookup {
        private static final HashMap<String, Method> methods;
        static {
            Method[] declaredMethods = PreparedStatement.class.getMethods();
            methods = allDoubleStringParameterMethods(declaredMethods);
        }
        
        PreparedStatementLookup() {}
        
        private static HashMap<String, Method> allDoubleStringParameterMethods(Method[] declaredMethods) {
            HashMap<String, Method> map = new HashMap<>();
            
            for (Method m : declaredMethods) {
                if (m.getParameterCount() == 2 && 
                    m.getName().startsWith("set") &&
                    m.getParameters()[0].getType().equals(int.class)) {
                    
                    map.put(m.getName().toLowerCase(), m);
                }
            }
            
            return map;
        }
        
        static Method method(String type) {
            return methods.get("set" + type.toLowerCase());
        }
    }
    
    static class ResultSetMetaDataLookup {
        private static final HashMap<String, HashMap<String, Integer>> columnDefinitions = new HashMap<>();
        
        ResultSetMetaDataLookup() {}
        
        static HashMap<String,Integer> formColumnDefinition(String originalSQL, ResultSet set) /*throws SQLException*/ {
            try {
                return columnDefinitions.computeIfAbsent(originalSQL, sql -> {
                    // easier when using com.mysql.cj.jdbc.result.ResultSetMetaData#getFields
                    try {
                        ResultSetMetaData metaData = set.getMetaData();
                        return _formColumnDefinition(originalSQL, metaData);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (RuntimeException e) {
                //if (e.getCause() instanceof SQLException) throw (SQLException)e.getCause();
                e.printStackTrace();
                return null;
            }
        }
        static HashMap<String,Integer> _formColumnDefinition(String originalSQL, ResultSetMetaData metaData) throws SQLException {
            HashMap<String, Integer> tableDotColumnName = new HashMap<>(metaData.getColumnCount()); // table.column
            
            // match "originalTableName" with @Table of record + "originalColumnName" with field of record
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                tableDotColumnName.put(metaData.getTableName(i) + "." + metaData.getColumnName(i), i);
            }
            
            return tableDotColumnName;
        }
    }
    
    record FieldIdentifier(String name, String type) {
        static FieldIdentifier DEF = new FieldIdentifier("id", "String");
    }
    
    record FieldRecord(Field field, String name, String type, String table, String columnDefintion) {
        public FieldRecord(Field field, String table) {
            this(field, field.getName(), field.getType().getSimpleName(), table, table + "." + field.getName());
        }
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    static @interface Table {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    static @interface Identifier { }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    static @interface AutoGenerated { }
    
    /*@Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    static @interface Timestamp { }*/
}

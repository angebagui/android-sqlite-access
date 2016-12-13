package istat.android.data.access.sqlite;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import istat.android.data.access.sqlite.interfaces.JSONable;
import istat.android.data.access.sqlite.utils.Toolkit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

public abstract class SQLiteModel implements JSONable, QueryAble, Cloneable {
    HashMap<String, Object> FieldNameValuePair = new HashMap<String, Object>();
    HashMap<String, Field> nameFieldPair = new HashMap<String, Field>();
    HashMap<String, Field> nestedTableField = new HashMap<String, Field>();
    //    protected String tb_name, primary_key;
//    protected String[] tb_projection;
    public static String TAG_CLASS = "istat.data.access.DbEntity.class";
    private Object instance;
    List<String> reflectionFieldNames = new ArrayList<String>();

    SQLiteModel() {
        instance = this;
    }

    protected void set(String name, Object value) {
        set(name, this, value);
    }

    public String getPrimaryKey() {
        return getString(getPrimaryFieldName());
    }

    protected Object get(String name) {
        return get(name, this);// FieldNameValuePair.get(name);
    }

    protected String getString(String name) {
        Object value = get(name);
        return value == null ? "" : value.toString();
    }

    protected boolean getBoolean(String name) {
        return Boolean.valueOf(getString(name));
    }

    protected double getDouble(String name) {
        try {
            return Double.valueOf(getString(name));
        } catch (Exception e) {
            return 0;
        }
    }

    protected float getFloat(String name) {
        try {
            return Float.valueOf(getString(name));
        } catch (Exception e) {
            return 0;
        }
    }

    protected long getLong(String name) {
        try {
            return Long.valueOf(getString(name));
        } catch (Exception e) {
            return 0;
        }
    }

    protected int getInteger(String name) {
        try {
            return Integer.valueOf(getString(name));
        } catch (Exception e) {
            return 0;
        }
    }

    protected static <T extends SQLiteModel> void set(String name, T obj,
                                                      Object value) {
        try {
            if (obj.reflectionFieldNames.contains(name)) {
                Field field = obj.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.set(obj, value);
            } else {
                obj.FieldNameValuePair.put(name, value);
            }

        } catch (NoSuchFieldException noe) {
            obj.FieldNameValuePair.put(name, value);
            noe.printStackTrace();
        } catch (Exception e) {
            // Log.e("ERROR", name);
            e.printStackTrace();
            obj.FieldNameValuePair.put(name, value);

        }
    }

    protected static <T extends SQLiteModel> Object get(String name, T obj) {
        try {
            if (obj.reflectionFieldNames.contains(name)) {
                Field field = obj.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(obj);
            } else {
                return obj.FieldNameValuePair.get(name);
            }

        } catch (NoSuchFieldException noe) {
            noe.printStackTrace();
            return obj.FieldNameValuePair.get(name);

        } catch (Exception e) {
            // Log.e("ERROR", name);
            e.printStackTrace();
            return obj.FieldNameValuePair.get(name);

        }
    }

    public HashMap<String, Object> toHashMap() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.putAll(map);
        return map;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = createJsonFromHashMap(FieldNameValuePair);
        try {
            String className = instance.getClass() + "";
            className = className.substring(6, className.length()).trim();
            json.put(TAG_CLASS, className);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return json;
    }

    public JSONObject toJson(boolean addClassName) {
        JSONObject json = toJson();
        if (!addClassName) {
            json.remove(TAG_CLASS);
        }
        return json;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }


    @Override
    public ContentValues toContentValues() {
        ContentValues pairs = new ContentValues();
        String[] projections = getProjections();
        for (String projection : projections) {
            if (projection != null) {
                if (get(projection) != null) {
                    String values = getString(projection);
                    pairs.put(projection, values);
                }
            }
        }
        return pairs;
    }

    public final void fillFromJson(JSONObject json) {
        try {
            List<String> keySet = JSONArrayToStringList(json.names());
            if (keySet.size() > 0) {
                for (String tmp : keySet) {
                    if (tmp.equals(TAG_CLASS))
                        continue;
                    Object value = createObject(tmp, json.optString(tmp));
                    if (value != null) {
                        set(tmp, value);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public final void fillFromCursor(Cursor c) {
        for (String projection : getProjections()) {
            if (projection != null) {
                int columnIndex = c.getColumnIndex(projection);
                if (columnIndex >= 0) {
                    String values = c.getString(columnIndex);
                    if (!TextUtils.isEmpty(values)) {
                        set(projection, values);
                    }
                }
            }
        }
    }

    public final void fillFromPrimaryKey(String primaryKey, SQLiteDatabase db) {
        String tb_name = getName();
        String[] tb_projection = getProjections();
        String primary_key = getPrimaryFieldName();
        Cursor c = db.query(tb_name, tb_projection, primary_key + "=?",
                new String[]{primaryKey}, null, null, null);
        if (c.getCount() > 0) {
            c.moveToNext();
            fillFromCursor(c);
        }
    }

    public long merge(SQLiteDatabase db) {
        long out = 0;
        if (exist(db)) {
            out = update(db);
            fillFromPrimaryKey(getPrimaryKey(), db);
        } else {
            out = insert(db);
        }
        return out;
    }

    @Override
    public long persist(SQLiteDatabase db) {
        if (exist(db)) {
            return update(db);
        } else {
            return insert(db);
        }
    }

    protected void onPersistEmbeddedDbEntity(SQLiteDatabase db, QueryAble entity) {
        entity.persist(db);
    }


    public long insert(SQLiteDatabase db) {
        long out = 0;
        try {
            out = db.insert(getName(), null, toContentValues());
            persistEmbeddedDbEntity(db);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }

        return out;
    }

    public int update(SQLiteDatabase db) {
        int out = 0;
        try {
            out = update(db, getPrimaryFieldName() + "= ?",
                    new String[]{getPrimaryKey()});
            persistEmbeddedDbEntity(db);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }

        return out;
    }

    public void merge(SQLiteModel entity) {
        merge(entity, true, true);
    }

    public void merge(SQLiteModel entity, boolean override, boolean mergeEmptyValue) {
        HashMap<String, Object> bundle = entity.FieldNameValuePair;
        Iterator<String> keySet = bundle.keySet().iterator();
        while (keySet.hasNext()) {
            String tmp = keySet.next();
            Object obj = bundle.get(tmp);
            if (obj != null) {
                if (FieldNameValuePair.containsValue(obj) && override) {
                    if (!mergeEmptyValue && obj == null
                            || (TextUtils.isEmpty(obj.toString()))) {

                    } else {
                        FieldNameValuePair.put(tmp, obj);
                    }
                } else {
                    FieldNameValuePair.put(tmp, obj);
                }

            }
        }
    }

    public boolean exist(SQLiteDatabase db) {
        String primary_key = getPrimaryFieldName();
        String tb_name = getName();
        if (TextUtils.isEmpty(primary_key)) {
            return false;
        }
        Cursor c = db.query(tb_name, new String[]{primary_key}, primary_key
                + "= ?", new String[]{getPrimaryKey()}, null, null, null);
        int count = c.getCount();
        c.close();
        return count > 0;
    }

    public int delete(SQLiteDatabase db) {
        return delete(db, getPrimaryFieldName() + "= ?",
                new String[]{getPrimaryKey()});
    }

    public static <T extends SQLiteModel> T fromJson(String json)
            throws InstantiationException, IllegalAccessException,
            JSONException {
        return fromJson(new JSONObject(json), null);
    }

    public static <T extends SQLiteModel> T fromJson(String json, Class<T> clazz)
            throws InstantiationException, IllegalAccessException,
            JSONException {
        return fromJson(new JSONObject(json), clazz);
    }

    public static <T extends SQLiteModel> T fromQueryable(JSONable q)
            throws InstantiationException, IllegalAccessException {
        return fromJson(q.toJson(), q.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T extends SQLiteModel> T fromJson(JSONObject json,
                                                     Class<?> cLass) throws InstantiationException,
            IllegalAccessException {
        if (json == null)
            return null;
        try {
            String clazz = json.optString(TAG_CLASS);
            if (!TextUtils.isEmpty(clazz)) {
                cLass = Class.forName(clazz);
            } else if (cLass == null) {
                return null;
            }
            try {
                Object obj = cLass.getConstructor(JSONObject.class)
                        .newInstance(json);
                if (obj instanceof SQLiteModel)
                    return (T) obj;
                else
                    return null;
            } catch (Exception e) {
                Object obj = Class.forName(clazz).newInstance();
                if (obj instanceof SQLiteModel) {
                    JSONable jsonModel = (SQLiteModel) obj;
                    jsonModel.fillFromJson(json);
                    if (obj instanceof SQLiteModel)
                        return (T) jsonModel;
                    else
                        return null;
                } else {
                    return null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static SQLiteModel fromObject(final Object obj) throws InstantiationException,
            IllegalAccessException {
        final Class<?> cLass = obj.getClass();
        List<String> tmp = new ArrayList<String>();
        HashMap<String, Object> map = new HashMap<String, Object>();
        HashMap<String, Field> nameFieldPair = new HashMap<String, Field>();
        HashMap<String, Field> nestedTableField = new HashMap<String, Field>();
        boolean hasColumnAnnotation = false;
        String primaryKey = null;
        String eligiblePrimaryName = null;
        try {

            List<Field> fields = Toolkit.getAllFieldFields(cLass, true, false);
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                field.setAccessible(true);
                if (!field.isAnnotationPresent(Ignore.class)) {
                    String columnName = null;
                    if (field.isAnnotationPresent(PrimaryKey.class) && primaryKey == null) {
                        primaryKey = field.getName();
                    } else if (field.getName().equalsIgnoreCase("id")) {
                        eligiblePrimaryName = field.getName();
                    }
                    if (field.isAnnotationPresent(Column.class)) {
                        Column column = cLass.getAnnotation(Column.class);
                        columnName = column.name();
                        if (!hasColumnAnnotation) {
                            tmp.clear();
                        }
                        hasColumnAnnotation = true;
                    }
                    if (columnName == null && !hasColumnAnnotation) {
                        columnName = field.getName();
                    }
                    if (columnName != null && !tmp.contains(columnName)) {
                        tmp.add(columnName);
                        map.put(columnName, field.get(obj));
                        nameFieldPair.put(columnName, field);
                        if (isNestedTableProperty(cLass, field)) {
                            nestedTableField.put(columnName, field);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (primaryKey == null) {
            primaryKey = eligiblePrimaryName;
        }
        if (!tmp.contains(primaryKey) && primaryKey != null) {
            tmp.add(primaryKey);
        }
        final String[] projections = tmp.toArray(new String[tmp.size()]);
        final String primary = primaryKey;
        SQLiteModel model = new SQLiteModel() {

            @Override
            public String getName() {
                String tableName = null;
                if (cLass.isAnnotationPresent(Table.class)) {
                    Table table = cLass.getAnnotation(Table.class);
                    tableName = table.name();
                }
                if (tableName == null) {
                    tableName = cLass.getSimpleName();
                }
                return tableName;
            }

            @Override
            public String[] getProjections() {

                return projections;
            }

            @Override
            public String getPrimaryFieldName() {
                return primary;
            }
        };
        model.FieldNameValuePair.putAll(map);
        model.nameFieldPair.putAll(nameFieldPair);
        model.nestedTableField.putAll(nestedTableField);
        return model;
    }

    public static SQLiteModel fromClass(final Class cLass) throws InstantiationException,
            IllegalAccessException {
        List<String> projectionAdder = new ArrayList<String>();
        HashMap<String, Field> nameFieldPair = new HashMap<String, Field>();
        HashMap<String, Field> nestedTableField = new HashMap<String, Field>();
        boolean hasColumnAnnotation = false;
        String primaryKey = null;
        String eligiblePrimaryName = null;
        try {
            List<Field> fields = Toolkit.getAllFieldFields(cLass, true, false);
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                if (!field.isAnnotationPresent(Ignore.class)) {
                    String columnName = null;
                    if (field.isAnnotationPresent(PrimaryKey.class) && primaryKey == null) {
                        primaryKey = field.getName();
                    } else if (field.getName().equalsIgnoreCase("id")) {
                        eligiblePrimaryName = field.getName();
                    }
                    if (field.isAnnotationPresent(Column.class)) {
                        Annotation columnAnnotation = cLass.getAnnotation(Column.class);
                        Column column = (Column) columnAnnotation;
                        columnName = column.name();
                        if (!hasColumnAnnotation) {
                            projectionAdder.clear();
                        }
                        hasColumnAnnotation = true;
                    }
                    if (columnName == null && !hasColumnAnnotation) {
                        columnName = field.getName();
                    }
                    if (columnName != null && !projectionAdder.contains(columnName)) {
                        projectionAdder.add(columnName);
                        nameFieldPair.put(columnName, field);
                        if (isNestedTableProperty(cLass, field)) {
                            nestedTableField.put(columnName, field);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (primaryKey == null) {
            primaryKey = eligiblePrimaryName;
        }
        if (!projectionAdder.contains(primaryKey) && primaryKey != null) {
            projectionAdder.add(primaryKey);
        }
        final String[] projections = projectionAdder.toArray(new String[projectionAdder.size()]);
        final String primary = primaryKey;
        SQLiteModel model = new SQLiteModel() {

            @Override
            public String getName() {
                String tableName = null;
                if (cLass.isAnnotationPresent(Table.class)) {
                    Table table = (Table) cLass.getAnnotation(Table.class);
                    tableName = table.name();
                }
                if (tableName == null) {
                    tableName = cLass.getSimpleName();
                }
                return tableName;
            }

            @Override
            public String[] getProjections() {
                return projections;
            }

            @Override
            public String getPrimaryFieldName() {
                return primary;
            }
        };
        model.nameFieldPair.putAll(nameFieldPair);
        model.nestedTableField.putAll(nestedTableField);
        return model;
    }

    private void persistEmbeddedDbEntity(SQLiteDatabase db) {
        try {
            Iterator<String> keySet = FieldNameValuePair.keySet().iterator();
            while (keySet.hasNext()) {
                String tmp = keySet.next();
                Object obj = FieldNameValuePair.get(tmp);
                if (obj != null && obj instanceof QueryAble) {
                    onPersistEmbeddedDbEntity(db, ((QueryAble) obj));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject createJsonFromHashMap(
            HashMap<String, Object> bundle) {
        return createJsonFromHashMap(bundle, false);
    }

    private static JSONObject createJsonFromHashMap(
            HashMap<String, Object> bundle, boolean acceptEmpty) {
        try {
            JSONObject json = new JSONObject();
            Iterator<String> keySet = bundle.keySet().iterator();
            while (keySet.hasNext()) {
                String tmp = keySet.next();
                Object obj = bundle.get(tmp);
                if (obj != null) {
                    if (obj instanceof JSONable) {
                        JSONable jsonModel = (JSONable) obj;
                        json.put(tmp, jsonModel.toJson());
                    } else {
                        String value = obj.toString();
                        if (!TextUtils.isEmpty(value)
                                && !value.equals(TAG_CLASS)) {
                            json.put(tmp, value);
                        }
                    }

                }
            }
            return json;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private Object createObject(String name, String objToString) {
        if (TextUtils.isEmpty(objToString))
            return null;
        try {
            JSONObject json = new JSONObject(objToString);
            String clazz = json.optString(TAG_CLASS);
            if (TextUtils.isEmpty(clazz)) {
                clazz = getFieldTypeClass(name).getCanonicalName();
            }
            if (TextUtils.isEmpty(clazz)) {
                return objToString;
            }
            try {
                Class<?> cLass = Class.forName(clazz);
                Object obj = cLass.getConstructor(JSONObject.class)
                        .newInstance(json);
                return obj;
            } catch (Exception e) {
                e.printStackTrace();
                Object obj = Class.forName(clazz).newInstance();
                if (obj instanceof JSONable) {
                    JSONable jsonModel = (JSONable) obj;
                    jsonModel.fillFromJson(json);
                    return jsonModel;
                } else {
                    return objToString;
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return objToString;
        } catch (Exception e) {
            e.printStackTrace();
            return objToString;
        }
    }

    public Field getField(String field) {
        if (nameFieldPair.containsKey(field)) {
            return nameFieldPair.get(field);
        } else {
            return null;
        }
    }

    public Class<?> getFieldTypeClass(String fieldName) {
        if (FieldNameValuePair.containsKey(fieldName)) {
            Object obj = get(fieldName);
            if (obj != null) {
                return obj.getClass();
            }
        } else {
            Field field = getField(fieldName);
            if (field != null) {
                return field.getType();
            }
        }
        return Object.class;
    }


    public <T> T asClass(Class<T> clazz) throws IllegalAccessException, InstantiationException {
        T instance = clazz.newInstance();
        List<Field> fields = Toolkit.getAllFieldFields(clazz, true, false);
        for (Field field : fields) {
            if (!field.isAnnotationPresent(Ignore.class)) {
                try {
                    field.setAccessible(true);
                    if (field.getType().isAssignableFrom(String.class)) {
                        field.set(instance, getString(field.getName()));
                    } else if (field.getType().isAssignableFrom(Double.class)) {
                        field.set(instance, getDouble(field.getName()));
                    } else if (field.getType().isAssignableFrom(Float.class)) {
                        field.set(instance, getFloat(field.getName()));
                    } else if (field.getType().isAssignableFrom(Long.class)) {
                        field.set(instance, getLong(field.getName()));
                    } else if (field.getType().isAssignableFrom(Boolean.class)) {
                        field.set(instance, getBoolean(field.getName()));
                    } else if (field.getType().isAssignableFrom(Integer.class)) {
                        field.set(instance, getInteger(field.getName()));
                    } else {
                        Gson gson = new Gson();
                        Type type = field.getType();
                        String retrievedEntity = getString(field.getName());
                        if (Toolkit.isJson(retrievedEntity)) {
                            Log.d("asClass", "stringularProperty=" + retrievedEntity);
                            if (!TextUtils.isEmpty(retrievedEntity)) {
                                Object obj = gson.fromJson(retrievedEntity, type);
                                field.set(instance, obj);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return instance;
    }

    private static <T> boolean isNestedTableProperty(Class<T> clazz, Field field) {
        return field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(ManyToMany.class)
                || field.isAnnotationPresent(ManyToOne.class);
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Table {
        String name() default "";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Column {
        String name() default "";

        boolean nullable() default true;
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PrimaryKey {
        public final static int POLICY_AUTOGENERATE = 2;
        public final static int POLICY_AUTOINCREMENT = 1;
        public final static int POLICY_NONE = 0;

        int policy() default 0;
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Ignore {
        int when() default 0;
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotNull {
    }


    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface OneToOne {
        String mappedBy();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface OneToMany {
        String mappedBy();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ManyToOne {
        String mappedBy();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ManyToMany {
        String mappedBy() default "";
    }


    private boolean hasPrimaryKey() {

        return !TextUtils.isEmpty(getPrimaryKey());
    }

    protected void clear() {
        String[] projections = getProjections();
        for (String name : projections) {
            set(name, null);
        }
    }

    public void fillJson(Class<?> clazz, JSONObject json)
            throws IllegalAccessException, IllegalArgumentException,
            JSONException {
        List<Field> fields = Toolkit.getAllFieldIncludingPrivateAndSuper(clazz);
        for (Field field : fields) {
            if (field.isAnnotationPresent(PrimaryKey.class)
                    || field.isAnnotationPresent(Column.class)) {
                field.setAccessible(true);
                Object obj = field.get(instance);
                if (obj instanceof JSONable) {
                    JSONable jsonEntity = (JSONable) obj;
                    json.put(field.getName(), jsonEntity.toJson());
                } else {
                    json.put(field.getName(), field.get(instance));
                }
            } else {
                // field.setAccessible(true);
                // Log.d("filed::" + field.getName(), field.get(instance) +
                // "");
            }
        }
    }

    public void fillMap(Class<?> clazz, HashMap<String, Object> map)
            throws IllegalAccessException, IllegalArgumentException,
            JSONException {
        List<Field> fields = Toolkit.getAllFieldIncludingPrivateAndSuper(clazz);
        for (Field field : fields) {
            if (field.isAnnotationPresent(PrimaryKey.class)
                    || field.isAnnotationPresent(Column.class)) {
                field.setAccessible(true);
                Object obj = field.get(instance);
                if (obj != null && !TextUtils.isEmpty(obj + "")) {
                    map.put(field.getName(), obj);
                }
            }
        }
    }


    protected int update(SQLiteDatabase db, String whereClause,
                         String[] whereArgs) {
        return db.update(getName(), toContentValues(), whereClause,
                whereArgs);
    }

    protected int delete(SQLiteDatabase db, String whereClause,
                         String[] whereArgs) {
        return db.delete(getName(), whereClause, whereArgs);
    }

    public static ContentValues createContentValuesFromJSONObject(
            JSONObject json) throws JSONException {
        ContentValues pair = new ContentValues();
        List<String> keySet = JSONArrayToStringList(json.names());
        if (keySet.size() > 0) {
            for (String tmp : keySet) {
                String value = json.optString(tmp);
                if (!TextUtils.isEmpty(value)) {
                    pair.put(tmp, value);
                }
            }
        }

        return pair;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static List<String> JSONArrayToStringList(JSONArray array)
            throws JSONException {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < array.length(); i++)
            out.add(array.getString(i));
        return out;
    }

    public int getPrimaryKeyPolicy() {
        Field field = getField(getPrimaryFieldName());
        if (field != null && field.isAnnotationPresent(PrimaryKey.class)) {
            PrimaryKey primary = field.getAnnotation(PrimaryKey.class);
            return primary.policy();
        }
        return 0;
    }

    String[] getNestedColumnNames() {
        String[] out = new String[nestedTableField.size()];
        Iterator<String> iterator = nestedTableField.keySet().iterator();
        int index = 0;
        while (iterator.hasNext()) {
            out[index] = iterator.next();
            index++;
        }
        return out;
    }

    Field[] getNestedFields() {
        Field[] out = new Field[nestedTableField.size()];
        Iterator<String> iterator = nestedTableField.keySet().iterator();
        int index = 0;
        while (iterator.hasNext()) {
            String name = iterator.next();
            out[index] = nestedTableField.get(name);
            index++;
        }
        return out;
    }

    public final static class Builder {


        public Builder setPrimaryFieldName(String name) {
            this.primaryFieldName = name;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder addProjection(String... name) {
            for (String n : name) {
                this.projections.add(n);
            }
            return this;
        }


        public Builder setProjections(java.lang.String[] projections) {
            this.projections = new ArrayList<String>();
            Collections.addAll(this.projections, projections);
            return this;
        }

        public void setProjections(List<String> projections) {
            this.projections = projections;
        }

        public Builder setFieldNameValuePair(HashMap<String, Object> fieldNameValuePair) {
            FieldNameValuePair = fieldNameValuePair;
            return this;
        }

        public Builder setNameFieldPair(HashMap<String, Field> nameFieldPair) {
            this.nameFieldPair = nameFieldPair;
            return this;
        }

        public Builder setNestedTableNameFieldPair(HashMap<String, Field> nestedTableNameFieldPair) {
            this.nestedTableNameFieldPair = nestedTableNameFieldPair;
            return this;
        }

        public SQLiteModel create() {
            SQLiteModel model = new SQLiteModel() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String[] getProjections() {
                    return projections.toArray(new String[projections.size()]);
                }

                @Override
                public String getPrimaryFieldName() {
                    return primaryFieldName;
                }
            };
            model.FieldNameValuePair = FieldNameValuePair;
            model.nameFieldPair = nameFieldPair;
            model.nestedTableField = nestedTableNameFieldPair;
            return model;
        }

        String name, primaryFieldName;
        List<String> projections = new ArrayList<String>();
        HashMap<String, Object> FieldNameValuePair = new HashMap<String, Object>();
        HashMap<String, Field> nameFieldPair = new HashMap<String, Field>();
        HashMap<String, Field> nestedTableNameFieldPair = new HashMap<String, Field>();
    }

    public final static SQLiteModel createFromManyToMany(Class<?> parentTableClass, Class<?> childTableClass) {


        return null;
    }

}

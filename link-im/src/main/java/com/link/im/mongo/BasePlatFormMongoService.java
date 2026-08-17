package com.link.im.mongo;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;


@Slf4j
public abstract class BasePlatFormMongoService<T> {

    @Autowired
    protected MongoTemplate mongo;


    protected final Class<T> ENTITY_CLASS;

    public Gson gson = new Gson();


    @SuppressWarnings("unchecked")
    protected BasePlatFormMongoService() {
        this.ENTITY_CLASS = (Class<T>) resolveEntityClass(getClass());
    }

    /**
     * 沿继承链向上查找泛型实参。
     *
     * <p>不能只看直接父类：当子类与本类之间存在中间抽象类时
     * （如 XxxHandler → BaseXxxHandler → BasePlatFormMongoService&lt;Entity&gt;），
     * 子类的直接父类是不带泛型实参的普通 Class，泛型实参声明在更上一层。
     * 这里逐层上溯，直到找到 ParameterizedType 为止。
     *
     * <p>注意：中间类自身必须是「不带类型变量的具体化」——即
     * {@code BaseXxxHandler extends BasePlatFormMongoService<Entity>}。
     * 若中间类写成 {@code BaseXxx<T> extends BasePlatFormMongoService<T>}，
     * 拿到的会是类型变量而非真实 Class，此处会明确报错而不是静默出错。
     */
    private static Class<?> resolveEntityClass(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Type superclass = current.getGenericSuperclass();
            if (superclass instanceof ParameterizedType parameterized) {
                Type arg = parameterized.getActualTypeArguments()[0];
                if (arg instanceof Class<?> entity) {
                    return entity;
                }
                // 泛型实参仍是类型变量（中间类没写死实体类型），继续向上找
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException(clazz.getName()
                + " 继承 BasePlatFormMongoService 时必须指定实体泛型，"
                + "如 extends BasePlatFormMongoService<UserInfo>；"
                + "若中间隔了抽象类，请在该抽象类上写死实体类型");
    }

    public void print(String title,Object t){
        String json = gson.toJson(t);
        log.info(title + json);
    }

    public static boolean pageValidator(int skip,int limit){
        if (skip < 0 || limit <=0)
            return false;
        return true;
    }

    // 为空返回false
    public static boolean stringValidator(String... params){
        boolean validator = true;
        for (String param : params) {
            if (StringUtils.isEmpty(param)){
                validator = false;
                break;
            }
        }
        return validator;
    }

    protected void printf(String title,Object obj,Class<?> clazz){
        Gson gson = new Gson();
        String json = gson.toJson(obj, clazz);

        log.info(title+":"+json);
    }

    public long now(){
        return System.currentTimeMillis();
    }

    protected MongoTemplate getMongoTemplate() {
        return this.mongo;
    }

    protected T findOne(Query query) {
        return mongo.findOne(query, ENTITY_CLASS);
    }

    protected T findById(Object id) {
        return mongo.findById(id, ENTITY_CLASS);
    }

    protected T findById(String id) {
        return mongo.findOne(eq(
                where("_id").is(id)
        ), ENTITY_CLASS);
    }



    protected T findAndModify(Query eq, Update update, FindAndModifyOptions options) {
       return mongo.findAndModify(eq, update, options, ENTITY_CLASS);
    }

    protected List<T> find(Query query) {
        return mongo.find(query, ENTITY_CLASS);
    }

    protected List<T> findAll() {
        return mongo.findAll(ENTITY_CLASS);
    }

    protected boolean exists(Query query) {
        return mongo.exists(query, ENTITY_CLASS);
    }

    protected long count(Query query) {
        return mongo.count(query, ENTITY_CLASS);
    }

    protected T save(T entity) {
        return mongo.save(entity);
    }

    protected T insert(T entity) {
        return mongo.insert(entity);
    }

    protected long updateFirst(Query query, Update update) {
        return mongo.updateFirst(query, update, ENTITY_CLASS).getModifiedCount();
    }

    protected long updateMulti(Query query, Update update) {
        return mongo.updateMulti(query, update, ENTITY_CLASS).getModifiedCount();
    }

    protected long remove(Query query) {
        return mongo.remove(query, ENTITY_CLASS).getDeletedCount();
    }


    protected Criteria where(String key){
        return Criteria.where(key);
    }

    protected Query eq(Criteria criteria){
        return new Query(criteria);
    }

    protected FindAndModifyOptions options(){
        return new FindAndModifyOptions();
    }

    protected Update update(){return new Update();}

    protected Query eq(String field, Object value) {
        return new Query(Criteria.where(field).is(value));
    }


    protected <E> String col(SFunction<E, ?> column) {
        return LambdaUtil.fieldName(column);
    }


    protected <E> String colOf(SFunction<E, ?> column) {
        return LambdaUtil.fieldName(column);
    }

    protected Query eq(SFunction<T, ?> column, Object value) {
        return new Query(Criteria.where(col(column)).is(value));
    }


    protected String nextUnique(SFunction<T, ?> column, java.util.function.Supplier<String> supplier) {
        final int maxRetry = 10;
        String field = col(column);
        for (int i = 0; i < maxRetry; i++) {
            String candidate = supplier.get();
            if (!mongo.exists(new Query(Criteria.where(field).is(candidate)), ENTITY_CLASS)) {
                return candidate;
            }
        }
        throw new IllegalStateException("生成唯一值连续 " + maxRetry + " 次撞号，字段=" + field
                + "，请检查号段是否已接近用尽");
    }
}

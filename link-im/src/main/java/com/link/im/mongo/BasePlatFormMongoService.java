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



    @SuppressWarnings("unchecked")
    protected BasePlatFormMongoService() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType)) {
            throw new IllegalStateException(
                    getClass().getName() + " 继承 LinkMongoService 时必须指定实体泛型，如 extends LinkMongoService<UserInfo>");
        }
        Type[] args = ((ParameterizedType) superclass).getActualTypeArguments();
        this.ENTITY_CLASS = (Class<T>) args[0];
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

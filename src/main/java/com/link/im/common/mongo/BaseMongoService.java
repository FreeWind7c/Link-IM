package com.link.im.common.mongo;

import com.link.im.entity.chat.ChatSession;
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

/**
 * IM 模块 service 基类，封装 MongoTemplate 常用操作。
 * 子类继承时传入实体类型，例如：
 * <pre>{@code
 * public class UserInfoService extends LinkMongoService<UserInfo> { ... }
 * }</pre>
 * 基类通过反射拿到该泛型类型，方法内部自动使用，无需再逐个传 Class。
 * 需要操作其它实体或复杂查询时，仍可直接用 this.mongo 拿到原始 MongoTemplate。
 *
 * @param <T> 该 service 主要操作的实体类型
 */
public abstract class BaseMongoService<T> {

    @Autowired
    protected MongoTemplate mongo;

    /**
     * 子类传入的实体类型，构造时通过反射解析得到
     */
    protected final Class<T> entityClass;

    @SuppressWarnings("unchecked")
    protected BaseMongoService() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType)) {
            throw new IllegalStateException(
                    getClass().getName() + " 继承 LinkMongoService 时必须指定实体泛型，如 extends LinkMongoService<UserInfo>");
        }
        Type[] args = ((ParameterizedType) superclass).getActualTypeArguments();
        this.entityClass = (Class<T>) args[0];
    }

    public static boolean pageValidator(int skip,int limit){
        if (skip < 0 || limit <=0)
            return false;
        return true;
    }

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

    public long now(){
        return System.currentTimeMillis();
    }

    protected MongoTemplate getMongoTemplate() {
        return this.mongo;
    }

    protected T findOne(Query query) {
        return mongo.findOne(query, entityClass);
    }

    protected T findById(Object id) {
        return mongo.findById(id, entityClass);
    }



    protected T findAndModify(Query eq, Update update, FindAndModifyOptions options) {
       return mongo.findAndModify(eq, update, options, entityClass);
    }

    protected List<T> find(Query query) {
        return mongo.find(query, entityClass);
    }

    protected List<T> findAll() {
        return mongo.findAll(entityClass);
    }

    protected boolean exists(Query query) {
        return mongo.exists(query, entityClass);
    }

    protected long count(Query query) {
        return mongo.count(query, entityClass);
    }

    protected T save(T entity) {
        return mongo.save(entity);
    }

    protected T insert(T entity) {
        return mongo.insert(entity);
    }

    protected long updateFirst(Query query, Update update) {
        return mongo.updateFirst(query, update, entityClass).getModifiedCount();
    }

    protected long updateMulti(Query query, Update update) {
        return mongo.updateMulti(query, update, entityClass).getModifiedCount();
    }

    protected long remove(Query query) {
        return mongo.remove(query, entityClass).getDeletedCount();
    }


    protected Criteria where(String key){
        return Criteria.where(key);
    }

    protected Query eq(Criteria criteria){
        return new Query(criteria);
    }


    protected Update update(){return new Update();}

    protected Query eq(String field, Object value) {
        return new Query(Criteria.where(field).is(value));
    }

    /**
     * 通过方法引用拿字段名：this.col(UserInfo::getAccount) -> "account"。
     * 字段名与属性名不一致时（实体属性带 @Field 注解），返回注解里的名字。
     */
    protected <E> String col(SFunction<E, ?> column) {
        return LinkLambdaUtil.fieldName(column);
    }

    /**
     * 跨实体取字段名：this.colOf(UserInfo::getUserNo) -> "userNo"。
     * 当前 service 主实体（T）之外的实体用这个，T 自己的字段用 col 即可。
     */
    protected <E> String colOf(SFunction<E, ?> column) {
        return LinkLambdaUtil.fieldName(column);
    }

    /**
     * lambda 版等值查询：this.eq(UserInfo::getAccount, account)。
     * 等价于 new Query(Criteria.where("account").is(account))，但字段名编译期可校验、重构安全。
     */
    protected Query eq(SFunction<T, ?> column, Object value) {
        return new Query(Criteria.where(col(column)).is(value));
    }

    /**
     * 生成在指定字段上唯一的值：用 supplier 生成候选值，查库判重，撞了重试。
     * 例：唯一 userNo -> this.nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next)
     *
     * @param column   要保证唯一的字段
     * @param supplier 候选值生成器（如 LinkUserNoGenerator::next）
     * @return 当前库中不存在的唯一值
     */
    protected String nextUnique(SFunction<T, ?> column, java.util.function.Supplier<String> supplier) {
        final int maxRetry = 10;
        String field = col(column);
        for (int i = 0; i < maxRetry; i++) {
            String candidate = supplier.get();
            if (!mongo.exists(new Query(Criteria.where(field).is(candidate)), entityClass)) {
                return candidate;
            }
        }
        throw new IllegalStateException("生成唯一值连续 " + maxRetry + " 次撞号，字段=" + field
                + "，请检查号段是否已接近用尽");
    }
}

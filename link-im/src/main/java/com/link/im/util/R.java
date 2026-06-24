package com.link.im.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.link.im.enums.BaseEnum;

import java.util.HashMap;
import java.util.Map;

/**
 * 返回数据
 *
 * @author Mark sunlightcs@gmail.com
 */
public class R extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;



	public static R isPay() {
		R r = new R();
		r.put("code",200);
		r.put("status",1);
		r.put("msg","已支付");
		return r;
	}

	public static R notPay(String msg){
		R r = new R();
		r.put("code",200);
		r.put("status",0);
		r.put("msg",msg);
		return r;
	}

	public static R notLogin() {
		R r = new R();
		r.put("code",500);
		r.put("msg","请先登录");
		return r;
	}



    public R setData(Object data) {
		put("data",data);
		return this;
	}

	//利用fastjson进行反序列化
	public <T> T getData(TypeReference<T> typeReference) {
		Object data = get("data");	//默认是map
		String jsonString = JSON.toJSONString(data);
		T t = JSON.parseObject(jsonString, typeReference);
		return t;
	}


	//利用fastjson进行反序列化
	public <T> T getData(String key,TypeReference<T> typeReference) {
		Object data = get(key);	//默认是map
		String jsonString = JSON.toJSONString(data);
		T t = JSON.parseObject(jsonString, typeReference);
		return t;
	}



	public R() {
		put("code", 200);
		put("msg", "success");
	}
	
	public static R error() {
		return error(500, "ERROR");
	}


	
	public static R error(String msg) {
		return error(500, msg);

	}

	public static R error(BaseEnum base) {
		return error(base.getCode(), base.getMessage());
	}

	
	public static R error(int code, String msg) {
		R r = new R();
		r.put("code", code);
		r.put("msg", msg);
		return r;
	}

	public static R ok(String msg) {
		R r = new R();
		r.put("code",200);
		r.put("msg", msg);
		return r;
	}
	
	public static R ok(Map<String, Object> map) {
		R r = new R();
		r.putAll(map);
		return r;
	}
	
	public static R ok() {
		R r = new R();
		r.put("code",200);
		r.put("msg","SUCCESS");
		return r;
	}

	public static R record() {
		R r = new R();
		r.put("code",1);
		r.put("msg","SUCCESS");
		return r;
	}

	public static R collected(){
		R r = new R();
		r.put("code",0);
		r.put("msg","ERROR");
		return r;
	}


	public static R notBuy() {
		R r = new R();
		r.put("code",200);
		r.put("status",0);
		r.put("msg","未购买");
		return r;
	}

	public static R isBuy() {
		R r = new R();
		r.put("code",200);
		r.put("status",1);
		r.put("msg","已购买");
		return r;
	}

	public R put(Object value) {
		super.put("msg", value);
		return this;
	}

	public R putData(String key,Object value){
		R r = new R();
		r.put(key,value);
		return r;
	}

	public Integer getCode() {

		return (Integer) this.get("code");
	}




	public R setToken(String token) {
		this.put("token",token);
		return this;
	}

	public R putMsg(String msg, String appHttpCodeEnum) {
		put("msg",appHttpCodeEnum);
		return this;
	}

	public R setAuth(Integer auth) {
		R r = new R();
		r.put("auth",auth);
		return r;
	}

	public R setTokenAndAuth(String token,Integer auth) {
		R r = new R();
		r.put("token",token);
		r.put("auth",auth);
		return r;
	}

	public R put0( String key,Object value) {
		this.put(key,value);
		return this;

	}

	public R setTask(Runnable task) {
		R r = new R();
		r.put("task",1);
		task.run();;
		return r;
	}

	public R setSize(int size) {
		this.put("size",size);
		return this;
	}

	public R setUrl(String url) {
		this.put("url",url);
		return this;
	}

	public R setChildrenData(Object data) {
		this.put("children",data);
		return this;
	}
}